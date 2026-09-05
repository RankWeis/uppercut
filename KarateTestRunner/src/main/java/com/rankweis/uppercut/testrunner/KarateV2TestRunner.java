package com.rankweis.uppercut.testrunner;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Runs Karate 2.x tests. Unlike the v1 path in {@link KarateTestRunner} - which proxies
 * {@code com.intuit.karate.RuntimeHook} and reflects deep into engine internals - this runner uses v2's public
 * listener API: {@code io.karatelabs.core.Runner.builder().listener(RunListener)}. Every {@code RunEvent}
 * self-serializes via {@code toJson()}, so no engine internals are touched at all.
 *
 * <p>Events are emitted to stdout as one JSON object per line, prefixed with {@code <<UPPERCUT-V2>>} and the
 * event type, for the IDE-side output converter to parse.</p>
 *
 * <p>All Karate classes are accessed reflectively so this jar has no compile-time dependency on any Karate
 * version and can sit on a v1 or v2 user classpath.</p>
 */
public class KarateV2TestRunner {

  public static final String EVENT_PREFIX = "<<UPPERCUT-V2>>";

  /** Event types forwarded to the IDE; STEP_*, HTTP_* and PROGRESS are noise for the test tree. */
  // STEP_EXIT is forwarded so the IDE can show a per-step trace under each scenario, the way the v1
  // path does from its log lines. STEP_ENTER adds nothing the exit event does not already carry.
  private static final Set<String> FORWARDED_EVENT_TYPES = Set.of(
    "SUITE_ENTER", "SUITE_EXIT", "FEATURE_ENTER", "FEATURE_EXIT",
    "OUTLINE_ENTER", "SCENARIO_ENTER", "SCENARIO_EXIT", "STEP_EXIT", "ERROR");

  private final Map<String, List<String>> params;

  public KarateV2TestRunner(Map<String, List<String>> params) {
    this.params = params;
  }

  void doTest() throws Exception {
    String[] testNames =
      Optional.ofNullable(params.get("testname")).orElse(List.of()).stream()
        .map(KarateTestRunner::withDefaultScheme)
        .toList()
        .toArray(new String[0]);
    String[] workingDirectories =
      Optional.ofNullable(params.get("working-dir")).orElse(List.of()).toArray(new String[0]);
    String[] tags =
      Optional.ofNullable(params.get("tag")).orElse(List.of())
        .stream().filter(s -> !s.isBlank())
        .map(s -> !s.startsWith("@") ? "@" + s : s)
        .toList()
        .toArray(new String[0]);

    // Verified against karate-core 2.1.1: io.karatelabs.core.Runner.builder() returns Runner$Builder with
    // path(String...), tags(String...), workingDir(String), karateEnv(String), listener(RunListener),
    // outputHtmlReport(boolean) and terminal parallel(int) -> SuiteResult.
    Class<?> runnerClass = Class.forName("io.karatelabs.core.Runner");
    Object builder = runnerClass.getMethod("builder").invoke(null);
    Class<?> builderClass = builder.getClass();

    if (tags.length > 0) {
      String[] scanRoots = KarateTestRunner.tagScanRoots(params, workingDirectories);
      if (scanRoots.length > 0) {
        builderClass.getMethod("path", String[].class).invoke(builder, (Object) scanRoots);
      }
      builderClass.getMethod("tags", String[].class).invoke(builder, (Object) tags);
    } else if (testNames.length > 0) {
      builderClass.getMethod("path", String[].class).invoke(builder, (Object) testNames);
    }
    if (workingDirectories.length > 0) {
      builderClass.getMethod("workingDir", String.class).invoke(builder, workingDirectories[0]);
    }
    Optional<String> env =
      Optional.ofNullable(params.get("environment")).orElse(List.of())
        .stream().filter(s -> !s.isBlank())
        .findFirst();
    if (env.isPresent()) {
      builderClass.getMethod("karateEnv", String.class).invoke(builder, env.get());
    }

    Class<?> listenerClass = Class.forName("io.karatelabs.core.RunListener");
    Object listener = Proxy.newProxyInstance(
      Thread.currentThread().getContextClassLoader(),
      new Class<?>[]{listenerClass},
      (proxy, method, args) -> switch (method.getName()) {
        case "onEvent" -> emitEvent(args[0]);
        case "toString" -> "UppercutV2RunListener";
        case "hashCode" -> System.identityHashCode(proxy);
        case "equals" -> proxy == args[0];
        default -> null;
      });
    builderClass.getMethod("listener", listenerClass).invoke(builder, listener);

    int parallelism =
      Optional.ofNullable(params.get("parallelism"))
        .map(l -> l.get(0))
        .map(Integer::parseInt)
        .orElse(1);
    builderClass.getMethod("parallel", int.class).invoke(builder, parallelism);
  }

  /**
   * Serializes a {@code RunEvent} to a single {@code <<UPPERCUT-V2>> TYPE {json}} stdout line. Returns
   * {@code true} so the suite keeps running regardless of how v2 interprets the listener's boolean.
   */
  private boolean emitEvent(Object runEvent) {
    try {
      Method getType = runEvent.getClass().getMethod("getType");
      getType.setAccessible(true);
      String type = String.valueOf(getType.invoke(runEvent));
      if (!FORWARDED_EVENT_TYPES.contains(type)) {
        return true;
      }
      Method toJson = runEvent.getClass().getMethod("toJson");
      toJson.setAccessible(true);
      Object json = toJson.invoke(runEvent);
      if ("STEP_EXIT".equals(type)) {
        json = withStepLog(runEvent, json);
      }
      // v2 events carry no thread id, but this listener runs on the thread executing the step, so
      // stamp it here: with parallelism > 1 the IDE cannot otherwise tell whose step it is reading.
      json = withThread(json);
      // println is atomic per call, safe from Karate's virtual threads
      System.out.println(EVENT_PREFIX + " " + type + " " + toJsonString(json));
    } catch (Exception e) {
      System.out.println(EVENT_PREFIX + " EMIT_ERROR " + toJsonString(Map.of("message", String.valueOf(e))));
    }
    return true;
  }

  /**
   * Adds the step's captured log to a STEP_EXIT payload as {@code stepLog}.
   *
   * <p>Karate keeps what a step printed - {@code print} output, {@code karate.log} calls - on
   * {@code StepResult.getLog()}, but {@code StepRunEvent.toJson()} omits it; it only reaches the
   * aggregate emitted at FEATURE_EXIT, by which time the IDE has already closed the scenario nodes.
   * Reading it here is what lets the test tree show a step's output beside the step.
   *
   * <p>Best effort: any reflection failure returns the original payload, so a Karate version that
   * reshapes these types costs the log line, not the run.
   */
  /** Stamps the emitting thread onto the payload; the IDE keys its per-scenario state on it. */
  @SuppressWarnings("unchecked")
  private static Object withThread(Object json) {
    if (!(json instanceof Map)) {
      return json;
    }
    Map<String, Object> enriched = new LinkedHashMap<>((Map<String, Object>) json);
    // Karate 2 runs scenarios on virtual threads, which are unnamed - getName() returns "" for all
    // of them, which would collapse every thread onto one key. The id is always unique.
    Thread current = Thread.currentThread();
    String name = current.getName();
    enriched.put("thread", name == null || name.isBlank() ? "vt-" + current.getId() : name);
    return enriched;
  }

  @SuppressWarnings("unchecked")
  private static Object withStepLog(Object runEvent, Object json) {
    try {
      if (!(json instanceof Map)) {
        return json;
      }
      Method result = runEvent.getClass().getMethod("result");
      result.setAccessible(true);
      Object stepResult = result.invoke(runEvent);
      if (stepResult == null) {
        return json;
      }
      Method getLog = stepResult.getClass().getMethod("getLog");
      getLog.setAccessible(true);
      Object log = getLog.invoke(stepResult);
      if (log == null || String.valueOf(log).isBlank()) {
        return json;
      }
      Map<String, Object> enriched = new LinkedHashMap<>((Map<String, Object>) json);
      enriched.put("stepLog", String.valueOf(log));
      return enriched;
    } catch (Exception e) {
      return json;
    }
  }

  /** Minimal JSON writer for the Map/List/String/Number/Boolean shapes produced by {@code RunEvent.toJson()}. */
  static String toJsonString(Object o) {
    if (o == null) {
      return "null";
    }
    if (o instanceof String s) {
      return quote(s);
    }
    if (o instanceof Number || o instanceof Boolean) {
      return o.toString();
    }
    if (o instanceof Map<?, ?> map) {
      StringBuilder sb = new StringBuilder("{");
      boolean first = true;
      for (Map.Entry<?, ?> e : map.entrySet()) {
        if (!first) {
          sb.append(',');
        }
        first = false;
        sb.append(quote(String.valueOf(e.getKey()))).append(':').append(toJsonString(e.getValue()));
      }
      return sb.append('}').toString();
    }
    if (o instanceof Collection<?> collection) {
      StringBuilder sb = new StringBuilder("[");
      boolean first = true;
      for (Object item : collection) {
        if (!first) {
          sb.append(',');
        }
        first = false;
        sb.append(toJsonString(item));
      }
      return sb.append(']').toString();
    }
    return quote(String.valueOf(o));
  }

  private static String quote(String s) {
    StringBuilder sb = new StringBuilder("\"");
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    return sb.append('"').toString();
  }
}
