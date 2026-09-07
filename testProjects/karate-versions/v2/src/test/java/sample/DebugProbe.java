package sample;

import io.karatelabs.core.RunEventType;
import io.karatelabs.core.Runner;
import io.karatelabs.core.ScenarioRuntime;
import io.karatelabs.core.StepRunEvent;
import io.karatelabs.core.SuiteResult;
import io.karatelabs.js.DebugPointFactory;
import io.karatelabs.js.RunInterceptor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phase 0 of docs/DEBUGGER.md: does blocking inside Karate 2's debug interceptor actually work as a
 * breakpoint, and does anything upstream mind?
 *
 * <p>Run with: <pre>../../gradlew -p testProjects/karate-versions :v2:debugProbe</pre>
 * (-PpauseLine=N to pause on another line, -PpauseSeconds=N for a longer hold).</p>
 *
 * <p>What the output has to show:</p>
 * <ul>
 *   <li>The run stops before the target step and holds for the full pause - WAIT/waitForResume is a
 *       real suspend primitive, not advisory.</li>
 *   <li>The suite finishes normally afterwards, with the paused step passing: nothing upstream
 *       timed out or failed while a thread sat parked.</li>
 *   <li>The ScenarioRuntime stashed by the STEP_ENTER listener is readable from the paused thread,
 *       and its variables are the ones a user would expect at that line. GHERKIN_STEP points carry
 *       no context of their own, so this ThreadLocal pairing is what a variables view depends on.</li>
 *   <li>JS_STATEMENT points arrive too, carrying an io.karatelabs.js.Context - the later
 *       expression-level stepping story is real.</li>
 * </ul>
 */
public class DebugProbe {

  /** What DebugPointFactory hands us. GHERKIN_STEP passes (step, null); JS points pass (node, context). */
  record Point(int kind, int line, String source, Object node, Object context) {
    String kindName() {
      return switch (kind) {
        case DebugPointFactory.GHERKIN_STEP -> "GHERKIN_STEP";
        case DebugPointFactory.JS_STATEMENT -> "JS_STATEMENT";
        case DebugPointFactory.JS_EXPRESSION -> "JS_EXPRESSION";
        default -> "UNKNOWN(" + kind + ")";
      };
    }
  }

  /** Set on STEP_ENTER, read on the same thread inside the interceptor. */
  private static final ThreadLocal<ScenarioRuntime> CURRENT = new ThreadLocal<>();

  private static final AtomicInteger JS_POINTS = new AtomicInteger();
  private static final AtomicInteger STEP_POINTS = new AtomicInteger();

  public static void main(String[] args) {
    String path = System.getProperty("probePath", "classpath:sample/users.feature");
    int pauseLine = Integer.getInteger("pauseLine", 9);
    long pauseMillis = Long.getLong("pauseSeconds", 20L) * 1000L;
    int parallelism = Integer.getInteger("parallelism", 1);

    log("target " + path + ", pausing on line " + pauseLine + " for " + (pauseMillis / 1000)
      + "s, parallel=" + parallelism);

    DebugPointFactory<Point> factory = Point::new;

    RunInterceptor<Point> interceptor = new RunInterceptor<>() {

      @Override
      public RunInterceptor.Action beforeExecute(Point point) {
        if (point.kind() == DebugPointFactory.GHERKIN_STEP) {
          STEP_POINTS.incrementAndGet();
        } else if (JS_POINTS.incrementAndGet() == 1) {
          log("first JS point: " + point.kindName() + " line " + point.line()
            + ", context=" + (point.context() == null ? "null" : point.context().getClass().getName()));
        }
        if (point.kind() != DebugPointFactory.GHERKIN_STEP || point.line() != pauseLine) {
          return RunInterceptor.Action.PROCEED;
        }
        ScenarioRuntime runtime = CURRENT.get();
        log("PAUSE at " + point.source() + ":" + point.line()
          + " on " + Thread.currentThread()
          + " (virtual=" + Thread.currentThread().isVirtual() + ")"
          + " | runtime from ThreadLocal: " + (runtime == null ? "MISSING" : "ok"));
        if (runtime != null) {
          log("  currentStep: " + runtime.getCurrentStep().getText());
          log("  variables:   " + runtime.getAllVariables().keySet());
          log("  eval(id):    " + runtime.eval("id"));
        }
        return RunInterceptor.Action.WAIT;
      }

      /**
       * Both call sites - StepExecutor.execute for GHERKIN_STEP and Interpreter for JS_STATEMENT -
       * call this exactly ONCE after a WAIT and only test the result for SKIP. There is no polling
       * loop: the block has to happen inside this call. (The archived design on 0b99a31 assumed a
       * loop; it is wrong.)
       */
      @Override
      public RunInterceptor.Action waitForResume() {
        long start = System.currentTimeMillis();
        try {
          Thread.sleep(pauseMillis);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        log("RESUME after " + (System.currentTimeMillis() - start) + "ms parked");
        return RunInterceptor.Action.PROCEED;
      }
    };

    long start = System.currentTimeMillis();
    SuiteResult result = Runner.builder()
      .path(path)
      .workingDir(".")
      .listener(event -> {
        if (event.getType() == RunEventType.STEP_ENTER && event instanceof StepRunEvent step) {
          CURRENT.set(step.scenarioRuntime());
        }
        return true;
      })
      .debugSupport(interceptor, factory)
      .outputHtmlReport(false)
      .parallel(parallelism);

    log("suite finished in " + (System.currentTimeMillis() - start) + "ms: " + result);
    log("gherkin points: " + STEP_POINTS.get() + ", js points: " + JS_POINTS.get());
  }

  private static void log(String message) {
    System.out.println("[DEBUG-PROBE] " + message);
  }
}
