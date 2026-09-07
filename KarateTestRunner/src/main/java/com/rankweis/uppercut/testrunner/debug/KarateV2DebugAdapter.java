package com.rankweis.uppercut.testrunner.debug;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * Karate 2's half of the debugger: turns the engine's {@code RunInterceptor} callbacks into calls on
 * {@link DebugAgent}. Everything is reflective so this jar still has no compile-time dependency on any
 * Karate version.
 *
 * <p>Two facts about the engine shape this class, both verified against karate-core/karate-js 2.1.1
 * (see the phase 0 results in {@code docs/DEBUGGER.md}):</p>
 *
 * <ol>
 *   <li><b>{@code waitForResume()} is called exactly once</b> after a {@code WAIT}, by both
 *   {@code StepExecutor.execute} and the JS {@code Interpreter}, and only its {@code SKIP} result is
 *   examined. There is no polling loop, so the block has to happen inside that one call - returning
 *   {@code WAIT} again silently resumes the run. Hence {@code beforeExecute} only <i>decides</i>, and
 *   {@code waitForResume} does the parking.</li>
 *   <li><b>A {@code GHERKIN_STEP} point carries {@code (step, null)}</b> - no execution context. The
 *   {@code ScenarioRuntime} comes instead from the {@code STEP_ENTER} event that fires immediately
 *   before it on the same thread, stashed here in a ThreadLocal.</li>
 * </ol>
 */
public final class KarateV2DebugAdapter {

  /** What the point factory builds. The engine only ever hands it back to us, so its shape is ours. */
  record Point(int kind, int line, String source, Object node, Object context) {
  }

  /** Carries the decision from {@code beforeExecute} to the {@code waitForResume} that follows it. */
  private record Pending(String path, int line, String step, String scenario) {
  }

  private static final ThreadLocal<Object> CURRENT_RUNTIME = new ThreadLocal<>();
  private static final ThreadLocal<Pending> PENDING = new ThreadLocal<>();

  private final DebugAgent agent;
  private final int gherkinStepKind;
  private final Object proceed;
  private final Object skip;
  private final Object waitAction;

  private KarateV2DebugAdapter(DebugAgent agent, int gherkinStepKind, Class<?> actionClass) {
    this.agent = agent;
    this.gherkinStepKind = gherkinStepKind;
    this.proceed = action(actionClass, "PROCEED");
    this.skip = action(actionClass, "SKIP");
    this.waitAction = action(actionClass, "WAIT");
  }

  /**
   * Installs the interceptor on a {@code Runner.Builder}. Returns null when this Karate build has no
   * {@code debugSupport} - a 2.x older than the API, say - so the run proceeds without a debugger
   * rather than failing.
   */
  public static KarateV2DebugAdapter install(Object builder, Class<?> builderClass, DebugAgent agent) {
    try {
      Class<?> interceptorClass = Class.forName("io.karatelabs.js.RunInterceptor");
      Class<?> factoryClass = Class.forName("io.karatelabs.js.DebugPointFactory");
      Class<?> actionClass = Class.forName("io.karatelabs.js.RunInterceptor$Action");
      int gherkinStepKind = factoryClass.getField("GHERKIN_STEP").getInt(null);
      KarateV2DebugAdapter adapter = new KarateV2DebugAdapter(agent, gherkinStepKind, actionClass);
      Method debugSupport = builderClass.getMethod("debugSupport", interceptorClass, factoryClass);
      debugSupport.invoke(builder, adapter.interceptorProxy(interceptorClass), adapter.factoryProxy(factoryClass));
      return adapter;
    } catch (ReflectiveOperationException | RuntimeException e) {
      agent.detach();
      return null;
    }
  }

  /**
   * Called for every run event. {@code STEP_ENTER} fires on the thread that is about to execute the
   * step, immediately before the interceptor sees the matching {@code GHERKIN_STEP} point, which is
   * what makes this pairing sound.
   */
  public void observe(Object runEvent) {
    try {
      String type = String.valueOf(runEvent.getClass().getMethod("getType").invoke(runEvent));
      if ("STEP_ENTER".equals(type)) {
        CURRENT_RUNTIME.set(runEvent.getClass().getMethod("scenarioRuntime").invoke(runEvent));
      } else if ("STEP_EXIT".equals(type) && agent.isPauseOnFailure()) {
        pauseIfFailed(runEvent);
      }
    } catch (ReflectiveOperationException | RuntimeException e) {
      // Best effort: without the runtime a pause still works, it just has less to say about itself.
    }
  }

  /**
   * Stops on a step that has just failed. STEP_EXIT is fired on the thread that ran the step, before
   * it moves on, so blocking here parks the scenario with its variables exactly as the failure left
   * them.
   */
  private void pauseIfFailed(Object runEvent) throws ReflectiveOperationException {
    Object result = runEvent.getClass().getMethod("result").invoke(runEvent);
    if (result == null) {
      return;
    }
    Object status = result.getClass().getMethod("getStatus").invoke(result);
    if (!"FAILED".equals(String.valueOf(status))) {
      return;
    }
    Object step = result.getClass().getMethod("getStep").invoke(result);
    Object error = result.getClass().getMethod("getError").invoke(result);
    agent.pauseAfterFailure(sourcePath(step), lineOf(step), stepText(step), scenarioName(),
      new ScenarioRuntimeFrame(CURRENT_RUNTIME.get()), message(error));
  }

  private static String sourcePath(Object step) {
    try {
      Object feature = step.getClass().getMethod("getFeature").invoke(step);
      Object resource = feature.getClass().getMethod("getResource").invoke(feature);
      return String.valueOf(resource.getClass().getMethod("getRelativePath").invoke(resource));
    } catch (ReflectiveOperationException | RuntimeException e) {
      return "";
    }
  }

  private static int lineOf(Object step) {
    try {
      return (Integer) step.getClass().getMethod("getLine").invoke(step);
    } catch (ReflectiveOperationException | RuntimeException e) {
      return -1;
    }
  }

  /** Karate wraps the real problem several layers deep; the innermost message is the useful one. */
  static String message(Object error) {
    if (!(error instanceof Throwable throwable)) {
      return error == null ? "" : String.valueOf(error);
    }
    Throwable cause = throwable;
    while (cause.getCause() != null && cause.getCause() != cause) {
      cause = cause.getCause();
    }
    return cause.getMessage() == null ? cause.toString() : cause.getMessage();
  }

  private Object factoryProxy(Class<?> factoryClass) {
    return Proxy.newProxyInstance(classLoader(), new Class<?>[]{factoryClass},
      (proxy, method, args) -> switch (method.getName()) {
        case "create" -> new Point((Integer) args[0], (Integer) args[1], (String) args[2], args[3], args[4]);
        case "toString" -> "UppercutDebugPointFactory";
        case "hashCode" -> System.identityHashCode(proxy);
        case "equals" -> proxy == args[0];
        default -> null;
      });
  }

  private Object interceptorProxy(Class<?> interceptorClass) {
    return Proxy.newProxyInstance(classLoader(), new Class<?>[]{interceptorClass},
      (proxy, method, args) -> switch (method.getName()) {
        case "beforeExecute" -> beforeExecute(args[0]);
        case "waitForResume" -> waitForResume();
        case "afterExecute" -> null;
        case "toString" -> "UppercutRunInterceptor";
        case "hashCode" -> System.identityHashCode(proxy);
        case "equals" -> proxy == args[0];
        default -> null;
      });
  }

  private Object beforeExecute(Object point) {
    if (agent.isDetached() || !(point instanceof Point p) || p.kind() != gherkinStepKind) {
      return proceed;
    }
    if (!agent.shouldPauseAtStep(p.source(), p.line(), new ScenarioRuntimeFrame(CURRENT_RUNTIME.get()))) {
      return proceed;
    }
    PENDING.set(new Pending(p.source(), p.line(), stepText(p.node()), scenarioName()));
    return waitAction;
  }

  private Object waitForResume() {
    Pending pending = PENDING.get();
    PENDING.remove();
    if (pending == null) {
      // A JS point we did not ask to pause, or a WAIT we did not issue. Never park on one of those.
      return proceed;
    }
    return agent.pause(pending.path(), pending.line(), pending.step(), pending.scenario(),
      new ScenarioRuntimeFrame(CURRENT_RUNTIME.get())) == DebugAgent.Decision.SKIP ? skip : proceed;
  }

  /**
   * Variables and evaluation for a parked scenario, read straight off its {@code ScenarioRuntime}:
   * {@code getAllVariables()} is what Karate itself considers the scenario's state, and {@code eval}
   * is the same entry point a step uses, so an expression typed in the IDE behaves like one written
   * in the feature.
   */
  private record ScenarioRuntimeFrame(Object runtime) implements SuspendedFrame {

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> variables() {
      if (runtime == null) {
        return Map.of();
      }
      try {
        return (Map<String, Object>) runtime.getClass().getMethod("getAllVariables").invoke(runtime);
      } catch (ReflectiveOperationException | RuntimeException e) {
        return Map.of();
      }
    }

    @Override
    public Object evaluate(String expression) throws Exception {
      if (runtime == null) {
        throw new IllegalStateException("No Karate scenario is available on this thread");
      }
      try {
        return runtime.getClass().getMethod("eval", String.class).invoke(runtime, expression);
      } catch (InvocationTargetException e) {
        // Unwrap, so the IDE shows Karate's own message rather than "InvocationTargetException".
        Throwable cause = e.getCause();
        throw cause instanceof Exception checked ? checked : new IllegalStateException(cause);
      }
    }
  }

  private static String stepText(Object step) {
    if (step == null) {
      return "";
    }
    try {
      Object prefix = step.getClass().getMethod("getPrefix").invoke(step);
      Object text = step.getClass().getMethod("getText").invoke(step);
      return ((prefix == null ? "" : prefix + " ") + (text == null ? "" : text)).trim();
    } catch (ReflectiveOperationException | RuntimeException e) {
      return "";
    }
  }

  private static String scenarioName() {
    Object runtime = CURRENT_RUNTIME.get();
    if (runtime == null) {
      return "";
    }
    try {
      Object scenario = runtime.getClass().getMethod("getScenario").invoke(runtime);
      Object name = scenario.getClass().getMethod("getName").invoke(scenario);
      return name == null ? "" : String.valueOf(name);
    } catch (ReflectiveOperationException | RuntimeException e) {
      return "";
    }
  }

  private static Object action(Class<?> actionClass, String name) {
    for (Object constant : actionClass.getEnumConstants()) {
      if (name.equals(String.valueOf(constant))) {
        return constant;
      }
    }
    throw new IllegalStateException("RunInterceptor.Action has no constant " + name);
  }

  private static ClassLoader classLoader() {
    return Thread.currentThread().getContextClassLoader();
  }
}
