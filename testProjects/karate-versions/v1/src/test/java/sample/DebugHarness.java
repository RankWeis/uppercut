package sample;

import com.rankweis.uppercut.testrunner.KarateTestRunner;
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
 * The Karate 1 half of the phase 4 parity check in docs/DEBUGGER.md: the same agent and protocol as
 * the v2 harness, reaching Karate through RuntimeHook.beforeStep instead of v2's interceptor. Proves
 * that a v1 run pauses on the step, answers VARIABLES and EVALUATE from ScenarioEngine.vars, and
 * resumes - the behaviour that has to hold before the old JDI path can be deleted.
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
    Path feature = Path.of("src/test/java/sample/users-v1.feature").toAbsolutePath();
    int line = Integer.getInteger("pauseLine", 9);
    long holdMillis = Long.getLong("pauseSeconds", 5L) * 1000L;

    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      CountDownLatch pausedSeen = new CountDownLatch(1);
      Thread ide = new Thread(() -> fakeIde(server, feature, line, holdMillis, pausedSeen), "fake-ide");
      ide.setDaemon(true);
      ide.start();

      log("running users-v1.feature with a breakpoint on line " + line + " (source path: " + feature + ")");
      long start = System.currentTimeMillis();
      KarateTestRunner.main(new String[]{
        "--testname", "file:" + feature,
        // Karate 1 relativizes the feature path against the working dir, so both must be absolute.
        "--working-dir", Path.of(".").toAbsolutePath().normalize().toString(),
        "--parallelism", "1",
        "--debug-port", String.valueOf(server.getLocalPort())});
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
      out.println("CLEAR");
      out.println("BREAKPOINT " + base64(feature.toString()) + " " + line);
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

        // Add a breakpoint in the NEXT scenario while the run is suspended, the way the IDE does
        // when the gutter is clicked mid-session.
        if (!movedBreakpoint) {
          movedBreakpoint = true;
          int moveTo = Integer.getInteger("moveTo", line + 1);
          log("→ moving the breakpoint to line " + moveTo + " mid-run");
          out.println("CLEAR");
          out.println("BREAKPOINT " + base64(feature.toString()) + " " + moveTo);
          out.println("BREAKPOINTS_END");
        }

        log("→ RESUME " + thread);
        out.println("RESUME " + thread);
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
