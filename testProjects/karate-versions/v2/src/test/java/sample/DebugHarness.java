package sample;

import com.rankweis.uppercut.testrunner.KarateV2TestRunner;
import java.io.BufferedReader;
import java.io.IOException;
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
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * End of phase 1 in docs/DEBUGGER.md: the real debug agent and the real v2 adapter, driven by a
 * stand-in for the IDE, against a real Karate 2 suite. The unit tests in KarateTestRunner cover the
 * agent without Karate; this covers the half that only a real run can prove - that the interceptor
 * fires, that the breakpoint the IDE set on a source path matches the classpath copy Karate reports,
 * and that resume lets the suite finish.
 *
 * <p>Needs the runner classes built first:</p>
 * <pre>
 *   ./gradlew :KarateTestRunner:classes
 *   ./gradlew -p testProjects/karate-versions :v2:debugHarness
 * </pre>
 */
public class DebugHarness {

  private static final Pattern THREAD = Pattern.compile("\"thread\":\"([^\"]*)\"");

  public static void main(String[] args) throws Exception {
    Path feature = Path.of("src/test/java/"
      + System.getProperty("feature", "sample/users.feature")).toAbsolutePath();
    int line = Integer.getInteger("pauseLine", 9);
    long holdMillis = Long.getLong("pauseSeconds", 5L) * 1000L;

    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      CountDownLatch pausedSeen = new CountDownLatch(1);
      Thread ide = new Thread(() -> fakeIde(server, feature, line, holdMillis, pausedSeen), "fake-ide");
      ide.setDaemon(true);
      ide.start();

      log("running users.feature with a breakpoint on line " + line + " (source path: " + feature + ")");
      long start = System.currentTimeMillis();
      new KarateV2TestRunner(Map.of(
        "testname", List.of("file:" + feature),
        "working-dir", List.of("."),
        "parallelism", List.of("1"),
        "debug-port", List.of(String.valueOf(server.getLocalPort()))))
        .doTest();
      long elapsed = System.currentTimeMillis() - start;

      boolean paused = pausedSeen.await(1, TimeUnit.SECONDS);
      log("suite finished in " + elapsed + "ms");
      log("PAUSED seen: " + paused);
      log(paused && elapsed >= holdMillis
        ? "PASS - the run stopped at the breakpoint and resumed on command"
        : "FAIL - expected a pause of at least " + holdMillis + "ms");
    }
  }

  /** The IDE end: set one breakpoint, wait for PAUSED, hold, then resume that thread. */
  private static boolean movedBreakpoint;

  private static void fakeIde(ServerSocket server, Path feature, int line, long holdMillis,
    CountDownLatch pausedSeen) {
    try (Socket socket = server.accept();
      BufferedReader in = new BufferedReader(
        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
      PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
      log("agent connected");
      out.println("PAUSE_ON_FAILURE " + Boolean.getBoolean("pauseOnFailure"));
      out.println("CLEAR");
      String condition = System.getProperty("condition", "");
      out.println("BREAKPOINT " + base64(feature.toString()) + " " + line
        + (condition.isEmpty() ? "" : " " + base64(condition)));
      out.println("BREAKPOINTS_END");
      String message;
      while ((message = in.readLine()) != null) {
        log("← " + message);
        if (!message.startsWith("EVENT PAUSED ")) {
          continue;
        }
        pausedSeen.countDown();
        Matcher matcher = THREAD.matcher(message);
        String thread = matcher.find() ? matcher.group(1) : "";

        // What phase 3 added: ask the parked scenario what it holds, and make it evaluate something.
        log("→ VARIABLES " + thread);
        out.println("VARIABLES " + thread + " 1");
        log("← " + in.readLine());
        log("→ EVALUATE " + thread + " id");
        out.println("EVALUATE " + thread + " 2 " + base64("id"));
        log("← " + in.readLine());
        log("→ EVALUATE " + thread + " (a deliberate error)");
        out.println("EVALUATE " + thread + " 3 " + base64("nosuchvariable.field"));
        log("← " + in.readLine());

        log("holding " + thread + " for " + (holdMillis / 1000) + "s");
        Thread.sleep(holdMillis);

        // Add a breakpoint while the run is suspended: the IDE does this whenever the user clicks the
        // gutter mid-session, and the agent must pick it up for steps it has not reached yet.
        if (!movedBreakpoint) {
          movedBreakpoint = true;
          log("→ moving the breakpoint to line " + (line + 1) + " mid-run");
          out.println("CLEAR");
          out.println("BREAKPOINT " + base64(feature.toString()) + " " + (line + 1));
          out.println("BREAKPOINTS_END");
        }

        if (Boolean.getBoolean("stepAfterPause")) {
          log("→ STEP " + thread);
          out.println("STEP " + thread);
        } else {
          log("→ RESUME " + thread);
          out.println("RESUME " + thread);
        }
      }
    } catch (IOException | InterruptedException e) {
      log("ide ended: " + e);
    }
  }

  private static String base64(String value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static void log(String message) {
    System.out.println("[DEBUG-HARNESS] " + message);
  }
}
