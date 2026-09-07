package com.rankweis.uppercut.testrunner.debug;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Karate 1's half of the debugger. Where v2 has a purpose-built interceptor, v1 has
 * {@code RuntimeHook.beforeStep(Step, ScenarioRuntime)} - called on the thread about to run the step,
 * before it runs, and able to veto it - which is the same shape and just as good a breakpoint.
 *
 * <p>This replaced a JDI path that mapped a Gherkin step to the bytecode of a step-definition method
 * through {@code StepRuntime.findMethodsMatching}. That binding was heuristic: it matched on step
 * text, so it missed steps whose text no method matched, and it stopped inside karate-core with Java
 * locals rather than the scenario's own variables. Blocking in the hook stops on the step itself and
 * can show what Karate holds.</p>
 *
 * <p>Reflective like the rest of the runner, so the jar still compiles and ships without any Karate
 * version on its classpath.</p>
 */
public final class KarateV1DebugAdapter {

  private final DebugAgent agent;

  public KarateV1DebugAdapter(DebugAgent agent) {
    this.agent = agent;
  }

  /**
   * Handles a {@code beforeStep} call. Returns true to run the step, false to skip it - the same
   * contract {@code RuntimeHook} has, so the caller can return this straight back to Karate.
   */
  public boolean beforeStep(Object step, Object scenarioRuntime) {
    if (agent.isDetached()) {
      return true;
    }
    String path = featurePath(step);
    int line = intOf(step, "getLine");
    if (path == null || line < 0) {
      return true;
    }
    if (!agent.shouldPauseAtStep(path, line)) {
      return true;
    }
    return agent.pause(path, line, stepText(step), scenarioName(scenarioRuntime),
      new ScenarioEngineFrame(engineOf(scenarioRuntime))) != DebugAgent.Decision.SKIP;
  }

  /**
   * Handles an {@code afterStep} call: stops on a step that has just failed, with the scenario still
   * standing and its variables as the failure left them.
   */
  public void afterStep(Object stepResult, Object scenarioRuntime) {
    if (!agent.isPauseOnFailure()) {
      return;
    }
    try {
      Object result = stepResult.getClass().getMethod("getResult").invoke(stepResult);
      if (result == null
        || !Boolean.TRUE.equals(result.getClass().getMethod("isFailed").invoke(result))) {
        return;
      }
      Object step = stepResult.getClass().getMethod("getStep").invoke(stepResult);
      Object error = result.getClass().getMethod("getError").invoke(result);
      agent.pauseAfterFailure(featurePath(step), intOf(step, "getLine"), stepText(step),
        scenarioName(scenarioRuntime), new ScenarioEngineFrame(engineOf(scenarioRuntime)),
        KarateV2DebugAdapter.message(error));
    } catch (ReflectiveOperationException | RuntimeException e) {
      // A failure we cannot describe is not worth failing the run over.
    }
  }

  private static String featurePath(Object step) {
    try {
      Object feature = step.getClass().getMethod("getFeature").invoke(step);
      Object resource = feature.getClass().getMethod("getResource").invoke(feature);
      Object relative = resource.getClass().getMethod("getRelativePath").invoke(resource);
      return relative == null ? null : String.valueOf(relative);
    } catch (ReflectiveOperationException | RuntimeException e) {
      return null;
    }
  }

  private static String stepText(Object step) {
    try {
      Object prefix = step.getClass().getMethod("getPrefix").invoke(step);
      Object text = step.getClass().getMethod("getText").invoke(step);
      return ((prefix == null ? "" : prefix + " ") + (text == null ? "" : text)).trim();
    } catch (ReflectiveOperationException | RuntimeException e) {
      return "";
    }
  }

  private static String scenarioName(Object scenarioRuntime) {
    try {
      Field scenario = scenarioRuntime.getClass().getField("scenario");
      Object value = scenario.get(scenarioRuntime);
      Object name = value.getClass().getMethod("getName").invoke(value);
      return name == null ? "" : String.valueOf(name);
    } catch (ReflectiveOperationException | RuntimeException e) {
      return "";
    }
  }

  private static Object engineOf(Object scenarioRuntime) {
    try {
      return scenarioRuntime.getClass().getField("engine").get(scenarioRuntime);
    } catch (ReflectiveOperationException | RuntimeException e) {
      return null;
    }
  }

  private static int intOf(Object target, String method) {
    try {
      return (Integer) target.getClass().getMethod(method).invoke(target);
    } catch (ReflectiveOperationException | RuntimeException e) {
      return -1;
    }
  }

  /**
   * Variables and evaluation for a parked v1 scenario, read off its {@code ScenarioEngine}.
   *
   * <p>v1 wraps every variable in a {@code Variable}; unwrapping it with {@code getValue()} is what
   * makes the tree show the same shapes the v2 side does, so one renderer serves both.</p>
   */
  // Package-private so its failure-state handling can be tested against a stand-in engine.
  record ScenarioEngineFrame(Object engine) implements SuspendedFrame {

    @Override
    public Map<String, Object> variables() {
      Map<String, Object> unwrapped = new LinkedHashMap<>();
      if (engine == null) {
        return unwrapped;
      }
      try {
        Object vars = engine.getClass().getField("vars").get(engine);
        if (vars instanceof Map<?, ?> map) {
          map.forEach((name, variable) -> unwrapped.put(String.valueOf(name), unwrap(variable)));
        }
      } catch (ReflectiveOperationException | RuntimeException e) {
        return unwrapped;
      }
      return unwrapped;
    }

    /**
     * Evaluates, then puts the engine's failure state back exactly as it was.
     *
     * <p>Karate 1's {@code evalKarateExpression} records a failure on the engine as well as throwing,
     * so a mistyped expression in the Evaluate window <b>failed the scenario</b> - the run reported
     * "passed: 1 | failed: 1" for a typo the user made while looking around. Nothing typed into a
     * debugger may change the result of the run, so the previous failed reason is captured and
     * restored whatever happens. (Karate 2 has no such coupling; its eval only throws.)</p>
     */
    @Override
    public Object evaluate(String expression) throws Exception {
      if (engine == null) {
        throw new IllegalStateException("No Karate scenario is available on this thread");
      }
      Object failedReasonBefore = failedReason();
      try {
        return unwrap(engine.getClass().getMethod("evalKarateExpression", String.class)
          .invoke(engine, expression));
      } catch (InvocationTargetException e) {
        Throwable cause = e.getCause();
        throw cause instanceof Exception checked ? checked : new IllegalStateException(cause);
      } finally {
        restoreFailedReason(failedReasonBefore);
      }
    }

    private Object failedReason() {
      try {
        return engine.getClass().getMethod("getFailedReason").invoke(engine);
      } catch (ReflectiveOperationException | RuntimeException e) {
        return null;
      }
    }

    private void restoreFailedReason(Object previous) {
      try {
        if (previous != failedReason()) {
          engine.getClass().getMethod("setFailedReason", Throwable.class).invoke(engine, previous);
        }
      } catch (ReflectiveOperationException | RuntimeException e) {
        // Nothing else to try; better a stale failure than swallowing the evaluation entirely.
      }
    }

    private static Object unwrap(Object variable) {
      if (variable == null) {
        return null;
      }
      try {
        return variable.getClass().getMethod("getValue").invoke(variable);
      } catch (ReflectiveOperationException | RuntimeException e) {
        // Not a Variable, or a Variable that will not give up its value: show it as it is.
        return variable;
      }
    }
  }
}
