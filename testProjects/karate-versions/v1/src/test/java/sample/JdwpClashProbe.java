package sample;

import com.rankweis.uppercut.testrunner.KarateTestRunner;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Does an opt-in JVM debugger get along with the Karate debug agent, or do they fight over the same
 * JVM? The phase 6 spike for docs/DEBUGGER.md.
 *
 * <p>A Karate run currently attaches no JVM debugger at all. Bringing one back as an opt-in second
 * tab is cheap to wire, so the only question worth spiking is what happens when both debuggers are
 * live in one JVM - because they are not independent: a Java breakpoint's default suspend policy is
 * <b>all threads</b>, and one of those threads is the agent's socket reader.</p>
 *
 * <p>The Karate 1 twin of the v2 probe: same experiments, {@code RuntimeHook.beforeStep} and platform
 * threads instead of the v2 interceptor and virtual ones.</p>
 *
 * <p>Two processes. The parent is a stand-in for the IDE with both tabs open: it listens on the
 * Karate debug channel <i>and</i> attaches JDI to the child, exactly as the two sessions would. The
 * child is a real Karate suite under the real agent, launched with JDWP the way an opt-in Debug run
 * would launch it.</p>
 *
 * <pre>
 *   ./gradlew :KarateTestRunner:classes
 *   ../../gradlew -p testProjects/karate-versions :v1:jdwpClash
 *   ../../gradlew -p testProjects/karate-versions :v1:jdwpClash -Pparallelism=2
 * </pre>
 *
 * <p>The sequence: pause the scenario on the step that calls Java, prove the Karate channel answers,
 * then suspend the whole VM the way a Java breakpoint would and ask the same question again. Then
 * release the VM, let the run reach the real Java breakpoint, and see which thread it lands on.</p>
 */
public class JdwpClashProbe {

  private static final Pattern THREAD = Pattern.compile("\"thread\":\"([^\"]*)\"");
  /** The step that calls into sample.HelperV1; Karate pauses before it, so Java has not run yet. */
  private static final int JAVA_CALL_LINE = 6;

  private final LinkedBlockingQueue<String> fromAgent = new LinkedBlockingQueue<>();
  private final CountDownLatch agentConnected = new CountDownLatch(1);
  private volatile PrintWriter toAgent;

  public static void main(String[] args) throws Exception {
    if (args.length > 0 && "child".equals(args[0])) {
      child(Integer.parseInt(args[1]), args[2], args[3]);
      return;
    }
    new JdwpClashProbe().parent();
  }

  // ---------------------------------------------------------------- child

  /** The test JVM: a real Karate suite with the real agent, and JDWP open for the parent. */
  private static void child(int idePort, String feature, String parallelism) throws Exception {
    KarateTestRunner.main(new String[]{
      "--testname", "file:" + feature,
      // Karate 1 relativizes the feature path against the working dir, so both must be absolute.
      "--working-dir", Path.of(".").toAbsolutePath().normalize().toString(),
      "--parallelism", parallelism,
      "--debug-port", String.valueOf(idePort)});
  }

  // --------------------------------------------------------------- parent

  private void parent() throws Exception {
    Path feature = Path.of("src/test/java/sample/javacall.feature").toAbsolutePath();
    String parallelism = System.getProperty("parallelism", "1");

    try (ServerSocket ide = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
      ServerSocket free = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      int jdwpPort = free.getLocalPort();
      free.close();

      Thread channel = new Thread(() -> serveChannel(ide, feature), "fake-ide");
      channel.setDaemon(true);
      channel.start();

      log("launching the test JVM with JDWP on " + jdwpPort + ", karate channel on "
        + ide.getLocalPort() + ", parallelism " + parallelism);
      Process child = launchChild(ide.getLocalPort(), feature, jdwpPort, parallelism);

      VirtualMachine vm = attach(jdwpPort);
      log("JDI attached: " + vm.name() + " " + vm.version());
      log("virtual threads visible to JDI at attach: "
        + vm.allThreads().stream().filter(t -> t.name().startsWith("VirtualThread")).count()
        + " of " + vm.allThreads().size() + " threads");

      BreakpointHits hits = new BreakpointHits();
      armHelperBreakpoint(vm, hits);
      // suspend=y means the child has run nothing yet; let it go now that the request is armed.
      vm.resume();

      runExperiments(vm, hits);

      child.waitFor(60, TimeUnit.SECONDS);
      log("child exited with " + (child.isAlive() ? "(still alive)" : child.exitValue()));
    }
  }

  private void runExperiments(VirtualMachine vm, BreakpointHits hits) throws Exception {
    if (!agentConnected.await(30, TimeUnit.SECONDS)) {
      log("FAIL - the agent never connected to the karate channel");
      return;
    }
    log("experiment 0: the agent connected while JDWP was attached, so the two do not "
      + "exclude each other at startup");

    String thread = awaitPaused(30_000);
    if (thread == null) {
      log("FAIL - the run never paused on the karate breakpoint");
      return;
    }
    log("karate paused on " + thread + " at javacall.feature:" + JAVA_CALL_LINE);

    log("experiment 1: karate channel with the VM running");
    report(askVariables(thread, 1, 5_000));

    log("experiment 2: karate channel while the VM is suspended, which is what a java "
      + "breakpoint with the default suspend policy does");
    vm.suspend();
    log("vm.suspend() returned; suspendCount on the paused scenario thread: "
      + suspendCount(vm, thread));
    long blocked = askVariables(thread, 2, 5_000);
    report(blocked);

    log("experiment 3: does a karate resume issued during the suspension survive it");
    toAgent.println("RESUME " + thread);
    String duringSuspension = fromAgent.poll(3, TimeUnit.SECONDS);
    log("anything from the agent in 3s of suspension: " + duringSuspension);

    long resumedAt = System.currentTimeMillis();
    vm.resume();
    String afterResume = fromAgent.poll(10, TimeUnit.SECONDS);
    log("first line after vm.resume(), " + (System.currentTimeMillis() - resumedAt) + "ms later: "
      + afterResume);

    log("experiment 4: the java breakpoint in the user's own code");
    if (hits.latch.await(30, TimeUnit.SECONDS)) {
      log("java breakpoint hit on thread: " + hits.threadName
        + " (virtual: " + hits.virtualThread + ")");
      log("karate channel while stopped at the java breakpoint:");
      report(askVariables(thread, 4, 5_000));
      hits.release();
      log("released the java breakpoint");
    } else {
      log("FAIL - the java breakpoint in sample.HelperV1 never hit"
        + (hits.armed ? "" : " (it was never armed - HelperV1 was not prepared)"));
    }
  }

  /** Sends a VARIABLES request and returns how long the reply took, or -1 if none came. */
  private long askVariables(String thread, int id, long timeoutMillis) throws InterruptedException {
    fromAgent.clear();
    long start = System.currentTimeMillis();
    toAgent.println("VARIABLES " + thread + " " + id);
    String reply = fromAgent.poll(timeoutMillis, TimeUnit.MILLISECONDS);
    return reply == null ? -1 : System.currentTimeMillis() - start;
  }

  private void report(long millis) {
    log(millis < 0 ? "  -> no reply: the channel is frozen" : "  -> replied in " + millis + "ms");
  }

  private String awaitPaused(long timeoutMillis) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMillis;
    while (System.currentTimeMillis() < deadline) {
      String message = fromAgent.poll(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
      if (message == null) {
        return null;
      }
      if (message.startsWith("EVENT PAUSED ")) {
        Matcher matcher = THREAD.matcher(message);
        return matcher.find() ? matcher.group(1) : "";
      }
    }
    return null;
  }

  // -------------------------------------------------------------- plumbing

  private Process launchChild(int idePort, Path feature, int jdwpPort, String parallelism)
    throws IOException {
    List<String> command = List.of(
      Path.of(System.getProperty("java.home"), "bin", "java").toString(),
      "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=127.0.0.1:" + jdwpPort,
      "-cp", System.getProperty("java.class.path"),
      JdwpClashProbe.class.getName(), "child", String.valueOf(idePort), feature.toString(),
      parallelism);
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    Thread pump = new Thread(() -> pump(process.getInputStream()), "child-output");
    pump.setDaemon(true);
    pump.start();
    return process;
  }

  private static void pump(InputStream stream) {
    try (BufferedReader reader = new BufferedReader(
      new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("<<UPPERCUT-V2>> SUITE_EXIT") || line.startsWith("<<UPPERCUT-V2>> FEATURE_EXIT")) {
          System.out.println("[child] " + line.substring(0, Math.min(120, line.length())) + " ...");
        } else {
          System.out.println("[child] " + line);
        }
      }
    } catch (IOException e) {
      // the child ended
    }
  }

  private static VirtualMachine attach(int port) throws Exception {
    AttachingConnector connector = Bootstrap.virtualMachineManager().attachingConnectors().stream()
      .filter(c -> "com.sun.jdi.SocketAttach".equals(c.name()))
      .findFirst().orElseThrow(() -> new IllegalStateException("no SocketAttach connector"));
    Map<String, Connector.Argument> arguments = connector.defaultArguments();
    arguments.get("hostname").setValue("127.0.0.1");
    arguments.get("port").setValue(String.valueOf(port));
    IllegalStateException last = new IllegalStateException("could not attach");
    for (int attempt = 0; attempt < 40; attempt++) {
      try {
        return connector.attach(arguments);
      } catch (IOException e) {
        Thread.sleep(250);
      }
    }
    throw last;
  }

  /** What the JDI event thread records about the breakpoint in sample.HelperV1. */
  private static final class BreakpointHits {
    private final CountDownLatch latch = new CountDownLatch(1);
    private volatile boolean armed;
    private volatile String threadName;
    private volatile boolean virtualThread;
    private volatile EventSet held;

    private void release() {
      EventSet set = held;
      if (set != null) {
        set.resume();
      }
    }
  }

  /**
   * Arms a breakpoint on {@code HelperV1.compute} with the default suspend-all policy - the point of
   * the probe is what the IDE's default does, not what a careful user could choose instead.
   */
  private void armHelperBreakpoint(VirtualMachine vm, BreakpointHits hits) {
    ClassPrepareRequest prepare = vm.eventRequestManager().createClassPrepareRequest();
    prepare.addClassFilter("sample.HelperV1");
    prepare.setSuspendPolicy(EventRequest.SUSPEND_ALL);
    prepare.enable();

    Thread events = new Thread(() -> {
      try {
        while (true) {
          EventSet set = vm.eventQueue().remove();
          boolean hold = false;
          for (Event event : set) {
            if (event instanceof ClassPrepareEvent prepared) {
              ReferenceType type = prepared.referenceType();
              BreakpointRequest breakpoint = vm.eventRequestManager()
                .createBreakpointRequest(type.methodsByName("compute").get(0).location());
              breakpoint.setSuspendPolicy(EventRequest.SUSPEND_ALL);
              breakpoint.enable();
              hits.armed = true;
              log("armed a java breakpoint on sample.HelperV1.compute, suspend policy ALL");
            } else if (event instanceof BreakpointEvent stopped) {
              ThreadReference thread = stopped.thread();
              hits.threadName = thread.name() + " #" + thread.uniqueID();
              hits.virtualThread = isVirtual(thread);
              hits.held = set;
              hits.latch.countDown();
              hold = true;
            } else if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) {
              return;
            }
          }
          if (!hold) {
            set.resume();
          }
        }
      } catch (Exception e) {
        log("jdi event loop ended: " + e);
      }
    }, "jdi-events");
    events.setDaemon(true);
    events.start();
  }

  private static boolean isVirtual(ThreadReference thread) {
    try {
      return (boolean) ThreadReference.class.getMethod("isVirtual").invoke(thread);
    } catch (ReflectiveOperationException e) {
      return thread.name().startsWith("VirtualThread");
    }
  }

  private static String suspendCount(VirtualMachine vm, String threadKey) {
    try {
      return vm.allThreads().stream()
        .filter(t -> threadKey.equals(t.name()))
        .findFirst()
        .map(t -> String.valueOf(t.suspendCount()))
        .orElse("(thread " + threadKey + " not visible to JDI)");
    } catch (Exception e) {
      return "(" + e + ")";
    }
  }

  /** The IDE's karate tab: one breakpoint on the step that calls Java, then relay everything. */
  private void serveChannel(ServerSocket server, Path feature) {
    try (Socket socket = server.accept();
      BufferedReader in = new BufferedReader(
        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
      PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
      toAgent = out;
      out.println("PAUSE_ON_FAILURE false");
      out.println("CLEAR");
      out.println("BREAKPOINT " + base64(feature.toString()) + " " + JAVA_CALL_LINE);
      out.println("BREAKPOINTS_END");
      agentConnected.countDown();
      String message;
      while ((message = in.readLine()) != null) {
        log("← " + (message.length() > 160 ? message.substring(0, 160) + " ..." : message));
        fromAgent.put(message);
      }
    } catch (IOException | InterruptedException e) {
      log("karate channel ended: " + e);
    }
  }

  private static String base64(String value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static void log(String message) {
    System.out.println("[JDWP-CLASH] " + message);
  }
}
