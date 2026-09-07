package com.rankweis.uppercut.testrunner.debug;

import static com.rankweis.uppercut.testrunner.KarateV2TestRunner.toJsonString;

import com.rankweis.uppercut.testrunner.debug.DebugProtocol.Command;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The debug agent inside the test JVM: it holds the breakpoints the IDE set, parks the thread that
 * reaches one, and releases it when the IDE says so.
 *
 * <p>The IDE listens and the agent connects, the same way round as JDWP, so the IDE picks a free port
 * before it launches anything and nothing has to be scraped back out of stdout.</p>
 *
 * <p><b>Everything here fails open.</b> A debugger that gets a connection wrong must cost the user
 * their breakpoints, never their test run: a refused connection, a silent IDE, a dropped socket or an
 * interrupt all end with the run proceeding. A parked test JVM that no longer has anyone to talk to is
 * the one outcome worth going out of the way to prevent.</p>
 */
public final class DebugAgent implements AutoCloseable {

  /** What the interceptor should do with the step it paused on. */
  public enum Decision {
    PROCEED, SKIP
  }

  private static final class Suspension {
    private final CountDownLatch latch = new CountDownLatch(1);
    private volatile Decision decision = Decision.PROCEED;
  }

  private final BreakpointTable breakpoints = new BreakpointTable();
  private final Map<String, Suspension> suspended = new ConcurrentHashMap<>();
  private final CountDownLatch handshake = new CountDownLatch(1);

  private volatile Socket socket;
  private volatile PrintWriter out;
  private volatile boolean detached;

  /**
   * Connects to the IDE and waits for the first breakpoint set, so no step can run past a breakpoint
   * before the IDE has had a chance to send it. Returns false when there is no usable channel, in
   * which case the caller runs undebugged.
   */
  public boolean connect(int port, long handshakeTimeoutMillis) {
    try {
      Socket connected = new Socket(InetAddress.getLoopbackAddress(), port);
      connected.setTcpNoDelay(true);
      this.socket = connected;
      this.out = new PrintWriter(new java.io.OutputStreamWriter(connected.getOutputStream(),
        StandardCharsets.UTF_8), true);
      Thread reader = new Thread(this::readLoop, "uppercut-debug-reader");
      reader.setDaemon(true);
      reader.start();
      send("HELLO", ordered("protocol", DebugProtocol.VERSION, "karateMajor", 2));
      if (!handshake.await(handshakeTimeoutMillis, TimeUnit.MILLISECONDS)) {
        // The IDE is there but never sent a breakpoint set. Run rather than hang; if it sends one
        // later the table picks it up, it just may have missed the steps that already ran.
        return true;
      }
      return true;
    } catch (IOException e) {
      detached = true;
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      detached = true;
      return false;
    }
  }

  /**
   * Called on the thread executing the step, from inside Karate's interceptor. Returns immediately
   * unless this line carries a breakpoint, in which case it blocks until the IDE resumes it.
   */
  public Decision pause(String path, int line, String stepText, String scenarioName) {
    if (detached || !breakpoints.matches(path, line)) {
      return Decision.PROCEED;
    }
    String thread = DebugProtocol.threadKey(Thread.currentThread());
    Suspension suspension = new Suspension();
    suspended.put(thread, suspension);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("thread", thread);
    payload.put("path", path);
    payload.put("line", line);
    payload.put("step", stepText);
    payload.put("scenario", scenarioName);
    send("PAUSED", payload);
    try {
      suspension.latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      suspended.remove(thread);
    }
    send("RESUMED", ordered("thread", thread, "action", suspension.decision.name()));
    return suspension.decision;
  }

  /**
   * Whether this line carries a breakpoint. Asked in {@code beforeExecute}, which only decides; the
   * parking itself happens in {@link #pause} from the single {@code waitForResume} call that follows.
   */
  public boolean isBreakpoint(String path, int line) {
    return !detached && breakpoints.matches(path, line);
  }

  /** True once the channel is gone or the IDE detached; the interceptor can stop asking. */
  public boolean isDetached() {
    return detached;
  }

  int breakpointCount() {
    return breakpoints.size();
  }

  private void readLoop() {
    try (BufferedReader in = new BufferedReader(
      new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = in.readLine()) != null) {
        handle(DebugProtocol.parse(line));
      }
    } catch (IOException e) {
      // Falls through to the same place a clean close does.
    } finally {
      detach();
    }
  }

  private void handle(Command command) {
    if (command == null) {
      return;
    }
    switch (command.name()) {
      case DebugProtocol.CLEAR -> breakpoints.clear();
      case DebugProtocol.BREAKPOINT -> breakpoints.add(command.path(), command.line());
      case DebugProtocol.BREAKPOINTS_END -> {
        breakpoints.commit();
        handshake.countDown();
      }
      case DebugProtocol.RESUME -> release(command.argument(), Decision.PROCEED);
      case DebugProtocol.SKIP -> release(command.argument(), Decision.SKIP);
      case DebugProtocol.RESUME_ALL -> releaseAll();
      case DebugProtocol.DETACH -> detach();
      default -> {
        // Unknown verb from a newer IDE: ignore it rather than break the run.
      }
    }
  }

  private void release(String thread, Decision decision) {
    Suspension suspension = suspended.get(thread);
    if (suspension == null) {
      // A resume for a thread that already continued, or a stale key. Nothing to do.
      return;
    }
    suspension.decision = decision;
    suspension.latch.countDown();
  }

  private void releaseAll() {
    suspended.forEach((thread, suspension) -> {
      suspension.decision = Decision.PROCEED;
      suspension.latch.countDown();
    });
  }

  /** Stops pausing and lets every parked thread go. Safe to call repeatedly. */
  public void detach() {
    detached = true;
    handshake.countDown();
    releaseAll();
  }

  /**
   * A payload whose key order is the order written here. {@code Map.of} randomises iteration per JVM,
   * which makes the wire output - and anything reading a log of it - differ run to run for no reason.
   */
  private static Map<String, Object> ordered(String firstKey, Object firstValue,
    String secondKey, Object secondValue) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put(firstKey, firstValue);
    payload.put(secondKey, secondValue);
    return payload;
  }

  private void send(String event, Map<String, Object> payload) {
    PrintWriter writer = out;
    if (writer == null) {
      return;
    }
    // println on a PrintWriter is synchronized, so virtual threads can emit concurrently.
    writer.println("EVENT " + event + " " + toJsonString(payload));
  }

  @Override
  public void close() {
    detach();
    Socket open = socket;
    if (open != null) {
      try {
        open.close();
      } catch (IOException e) {
        // Closing on the way out; nothing left to report it to.
      }
    }
  }
}
