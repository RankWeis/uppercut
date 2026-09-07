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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

  /** Why the run stopped, so the IDE can say so rather than leaving the user to work it out. */
  public enum Reason {
    BREAKPOINT, STEP, FAILURE
  }

  private static final class Suspension {
    private final CountDownLatch latch = new CountDownLatch(1);
    private final SuspendedFrame frame;
    private volatile Decision decision = Decision.PROCEED;

    private Suspension(SuspendedFrame frame) {
      this.frame = frame;
    }
  }

  private final BreakpointTable breakpoints = new BreakpointTable();
  private final Map<String, Suspension> suspended = new ConcurrentHashMap<>();
  /** Threads the IDE asked to stop again at their very next step. */
  private final Set<String> stepping = ConcurrentHashMap.newKeySet();
  /** A condition that would not evaluate, carried from the decision to the pause that follows it. */
  private final ThreadLocal<String> conditionError = new ThreadLocal<>();
  private final CountDownLatch handshake = new CountDownLatch(1);

  private volatile Socket socket;
  private volatile PrintWriter out;
  private volatile boolean detached;
  private volatile boolean pauseOnFailure;

  /**
   * Connects to the IDE and waits for the first breakpoint set, so no step can run past a breakpoint
   * before the IDE has had a chance to send it. Returns false when there is no usable channel, in
   * which case the caller runs undebugged.
   *
   * @param karateMajor which Karate this run drives, announced in the greeting so a mismatch between
   *     what the IDE detected and what actually loaded is visible on the wire rather than silent
   */
  public boolean connect(int port, long handshakeTimeoutMillis, int karateMajor) {
    try {
      Socket connected = new Socket(InetAddress.getLoopbackAddress(), port);
      connected.setTcpNoDelay(true);
      this.socket = connected;
      this.out = new PrintWriter(new java.io.OutputStreamWriter(connected.getOutputStream(),
        StandardCharsets.UTF_8), true);
      Thread reader = new Thread(this::readLoop, "uppercut-debug-reader");
      reader.setDaemon(true);
      reader.start();
      send("HELLO", ordered("protocol", DebugProtocol.VERSION, "karateMajor", karateMajor));
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
    return pause(path, line, stepText, scenarioName, null);
  }

  /**
   * Called on the thread executing the step, from inside Karate's interceptor. Returns immediately
   * unless this line carries a breakpoint, in which case it blocks until the IDE resumes it.
   *
   * <p>The frame is what serves variables and evaluation while the thread is parked; it is only ever
   * touched from the reader thread between the pause and the resume, which is exactly the window in
   * which the scenario is standing still.</p>
   */
  public Decision pause(String path, int line, String stepText, String scenarioName,
    SuspendedFrame frame) {
    if (!shouldPauseAtStep(path, line)) {
      return Decision.PROCEED;
    }
    // A step reached because the user pressed Step is reported as such: "stopped where you asked" and
    // "stopped at your breakpoint" are different answers to "why am I here".
    Reason reason = stepping.remove(DebugProtocol.threadKey(Thread.currentThread()))
      ? Reason.STEP : Reason.BREAKPOINT;
    String failedCondition = conditionError.get();
    conditionError.remove();
    return park(path, line, stepText, scenarioName, frame, reason,
      failedCondition == null ? null : "Breakpoint condition failed: " + failedCondition);
  }

  private Decision park(String path, int line, String stepText, String scenarioName,
    SuspendedFrame frame, Reason reason, String error) {
    String thread = DebugProtocol.threadKey(Thread.currentThread());
    Suspension suspension = new Suspension(frame);
    suspended.put(thread, suspension);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("thread", thread);
    payload.put("path", path);
    payload.put("line", line);
    payload.put("step", stepText);
    payload.put("scenario", scenarioName);
    payload.put("reason", reason.name().toLowerCase(java.util.Locale.ROOT));
    if (error != null) {
      payload.put("error", error);
    }
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
   * Whether this step should stop the run - it carries a breakpoint, or the IDE asked this thread to
   * step. Asked in {@code beforeExecute}, which only decides; the parking itself happens in
   * {@link #pause} from the single {@code waitForResume} call that follows.
   */
  public boolean shouldPauseAtStep(String path, int line) {
    return shouldPauseAtStep(path, line, null);
  }

  /**
   * Whether this step should stop the run, evaluating a breakpoint's condition against the scenario
   * when it has one.
   *
   * <p>A condition that throws stops the run and the error is shown: a debugger that silently never
   * stops because of a typo is worse than one that stops too often.</p>
   */
  public boolean shouldPauseAtStep(String path, int line, SuspendedFrame frame) {
    if (detached) {
      return false;
    }
    if (stepping.contains(DebugProtocol.threadKey(Thread.currentThread()))) {
      return true;
    }
    if (!breakpoints.matches(path, line)) {
      return false;
    }
    String condition = breakpoints.conditionAt(path, line);
    if (condition == null || condition.isBlank() || frame == null) {
      return true;
    }
    try {
      conditionError.remove();
      return isTrue(frame.evaluate(condition));
    } catch (Exception e) {
      conditionError.set(condition + " -> " + rootMessage(e));
      return true;
    }
  }

  /**
   * Karate expressions return whatever the scenario holds, so anything that is not a boolean or null
   * counts as true - a condition of {@code response.id} means "when there is one".
   */
  static boolean isTrue(Object value) {
    if (value == null) {
      return false;
    }
    if (value instanceof Boolean bool) {
      return bool;
    }
    return !(value instanceof CharSequence text) || !text.isEmpty();
  }

  static String rootMessage(Throwable error) {
    Throwable cause = error;
    while (cause.getCause() != null && cause.getCause() != cause) {
      cause = cause.getCause();
    }
    return cause.getMessage() == null ? String.valueOf(cause) : cause.getMessage();
  }

  /** Whether a failed step should stop the run, so an adapter can skip the work when it should not. */
  public boolean isPauseOnFailure() {
    return pauseOnFailure && !detached;
  }

  /**
   * Stops on a step that has just failed, with the scenario still standing and its variables intact.
   *
   * <p>This is the question a debugger is usually opened for - the step failed, what was in
   * {@code response}? - and it is the one case where guessing where to put a breakpoint first, then
   * running again, is pure waste.</p>
   */
  public void pauseAfterFailure(String path, int line, String stepText, String scenarioName,
    SuspendedFrame frame, String error) {
    if (!isPauseOnFailure()) {
      return;
    }
    park(path, line, stepText, scenarioName, frame, Reason.FAILURE, error);
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
      case DebugProtocol.BREAKPOINT -> breakpoints.add(command.path(), command.line(), command.payload());
      case DebugProtocol.BREAKPOINTS_END -> {
        breakpoints.commit();
        handshake.countDown();
      }
      case DebugProtocol.VARIABLES -> sendVariables(command);
      case DebugProtocol.EVALUATE -> sendEvaluation(command);
      case DebugProtocol.STEP -> {
        // Arm before releasing, or the thread can reach its next step first and run straight past it.
        stepping.add(command.argument());
        release(command.argument(), Decision.PROCEED);
      }
      case DebugProtocol.PAUSE_ON_FAILURE -> pauseOnFailure = Boolean.parseBoolean(command.argument());
      case DebugProtocol.RESUME -> release(command.argument(), Decision.PROCEED);
      case DebugProtocol.SKIP -> release(command.argument(), Decision.SKIP);
      case DebugProtocol.RESUME_ALL -> releaseAll();
      case DebugProtocol.DETACH -> detach();
      default -> {
        // Unknown verb from a newer IDE: ignore it rather than break the run.
      }
    }
  }

  /**
   * Answers a request for the scenario's variables, or for the children of one of them.
   *
   * <p>An unknown thread, a resumed one, or a path that no longer resolves all answer with an empty
   * list: the IDE can ask about a tree the user left open across a resume, and that is not an error
   * worth showing them.</p>
   */
  private void sendVariables(Command command) {
    Suspension suspension = suspended.get(command.argument());
    List<Map<String, Object>> values = new ArrayList<>();
    if (suspension != null && suspension.frame != null) {
      try {
        Map<String, Object> variables = suspension.frame.variables();
        List<String> path = DebugProtocol.splitPath(command.payload() == null ? "" : command.payload());
        Map<String, Object> shown = path.isEmpty() ? variables
          : DebugValues.children(DebugValues.resolve(variables, path));
        shown.forEach((name, value) -> values.add(describe(DebugValues.render(name, value))));
      } catch (Exception e) {
        // A variable that throws on inspection must not take the debug channel down with it.
        values.clear();
      }
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", command.requestId());
    payload.put("values", values);
    send("VARIABLES", payload);
  }

  private void sendEvaluation(Command command) {
    Suspension suspension = suspended.get(command.argument());
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", command.requestId());
    if (suspension == null || suspension.frame == null) {
      payload.put("error", "This thread is no longer paused");
      send("EVALUATED", payload);
      return;
    }
    try {
      DebugValues.Value value = DebugValues.render("", suspension.frame.evaluate(command.payload()));
      payload.put("value", value.preview());
      payload.put("type", value.type());
      payload.put("hasChildren", value.hasChildren());
    } catch (Exception e) {
      // Karate wraps the real problem several layers deep; the innermost message is the useful one.
      Throwable cause = e;
      while (cause.getCause() != null && cause.getCause() != cause) {
        cause = cause.getCause();
      }
      payload.put("error", String.valueOf(cause.getMessage() == null ? cause : cause.getMessage()));
    }
    send("EVALUATED", payload);
  }

  private static Map<String, Object> describe(DebugValues.Value value) {
    Map<String, Object> described = new LinkedHashMap<>();
    described.put("name", value.name());
    described.put("type", value.type());
    described.put("value", value.preview());
    described.put("hasChildren", value.hasChildren());
    return described;
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
    stepping.clear();
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
