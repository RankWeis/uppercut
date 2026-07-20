package com.rankweis.uppercut.karate.run;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.intellij.execution.testframework.sm.ServiceMessageBuilder;
import com.intellij.openapi.diagnostic.Logger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Translates {@code <<UPPERCUT-V2>>} event lines - emitted by the KarateV2TestRunner from Karate 2.x's
 * {@code RunListener} API - into the id-based service-message test tree.
 *
 * <p>Line format: {@code <<UPPERCUT-V2>> EVENT_TYPE {json}} where the json is the event's
 * {@code RunEvent.toJson()} payload. Verified payload shapes (karate-core 2.1.1):</p>
 * <ul>
 *   <li>{@code FEATURE_ENTER}: path, name, line, callDepth, tags</li>
 *   <li>{@code SCENARIO_ENTER}: feature, name (null for anonymous), line, refId, callDepth, tags</li>
 *   <li>{@code SCENARIO_EXIT}: adds passed, skipped, durationMillis and error (on failure)</li>
 *   <li>{@code FEATURE_EXIT}: name, relativePath, callDepth, ...</li>
 * </ul>
 *
 * <p>Called features arrive between the calling step's events with {@code callDepth} incremented; a
 * last-open-node-per-depth table reproduces the nesting. With {@code parallelism > 1} the depth
 * attribution of called features is heuristic (v2 events carry no thread id); top-level features and
 * scenarios are always exact because their events carry the owning feature path.</p>
 */
public class KarateV2EventProcessor {

  public static final String EVENT_PREFIX = "<<UPPERCUT-V2>>";

  private static final Logger LOG = Logger.getInstance(KarateV2EventProcessor.class);

  /** Where translated service messages go; implemented by the output converter. */
  interface EventSink {

    void testsStarted();

    void emit(@NotNull ServiceMessageBuilder message, int nodeId, int parentNodeId);

    /**
     * Maps a runtime feature path (relative to the working directory, e.g.
     * {@code build/resources/test/x.feature}) and line to a {@code locationHint} value, or null.
     */
    @Nullable String resolveLocation(@NotNull String featurePath, int line);
  }

  private final EventSink sink;
  private int nextId = 1;
  private final Map<String, Integer> topFeatureIdByPath = new HashMap<>();
  private final Map<String, Integer> scenarioIdByKey = new HashMap<>();
  private final Map<Integer, String> nameById = new HashMap<>();
  // Per-thread, because Karate runs scenarios in parallel and their events interleave on one stream.
  // The runner stamps every event with its emitting thread; events without one share a single key,
  // which is the correct behaviour for a sequential run.
  private final Map<String, List<Integer>> lastScenarioAtDepth = new HashMap<>();
  private final Map<String, List<Integer>> lastFeatureAtDepth = new HashMap<>();

  public KarateV2EventProcessor(@NotNull EventSink sink) {
    this.sink = sink;
  }

  /** Returns true if the line was consumed as a v2 event. */
  public boolean process(@NotNull String line) {
    String text = line.trim();
    if (!text.startsWith(EVENT_PREFIX)) {
      return false;
    }
    text = text.substring(EVENT_PREFIX.length()).trim();
    int space = text.indexOf(' ');
    if (space < 0) {
      return true;
    }
    String type = text.substring(0, space);
    // Anything carrying the protocol prefix is ours: a false return would have the SM framework print
    // the raw line into the console. That covers the runner's own EMIT_ERROR marker, event types added
    // to the runner before this switch learns them, and malformed payloads alike - consume and log.
    JsonObject json;
    try {
      json = JsonParser.parseString(text.substring(space + 1)).getAsJsonObject();
    } catch (JsonSyntaxException | IllegalStateException e) {
      LOG.warn("Unparseable Karate 2 event payload for type " + type, e);
      return true;
    }
    switch (type) {
      case "SUITE_ENTER" -> sink.testsStarted();
      case "FEATURE_ENTER" -> featureEnter(json);
      case "SCENARIO_ENTER" -> scenarioEnter(json);
      case "SCENARIO_EXIT" -> scenarioExit(json);
      case "STEP_EXIT" -> stepExit(json);
      case "FEATURE_EXIT" -> featureExit(json);
      case "SUITE_EXIT", "OUTLINE_ENTER", "ERROR" -> {
        // ERROR details also arrive on SCENARIO_EXIT as 'error'; outlines show through their examples.
      }
      default -> LOG.warn("Unrecognized Karate 2 event type: " + type);
    }
    return true;
  }

  private void featureEnter(JsonObject json) {
    String path = getString(json, "path");
    if (path == null) {
      return;
    }
    int depth = getInt(json, "callDepth", 0);
    String title = getString(json, "name");
    String display = title != null ? title : basename(path);
    int id = nextId++;
    String thread = threadOf(json);
    final int parent = depth == 0 ? 0 : atDepth(lastScenarioAtDepth, thread, depth - 1);
    if (depth == 0) {
      topFeatureIdByPath.put(path, id);
    }
    setAtDepth(lastFeatureAtDepth, thread, depth, id);
    nameById.put(id, display);
    ServiceMessageBuilder msg = ServiceMessageBuilder.testSuiteStarted(display);
    String location = sink.resolveLocation(path, Math.max(getInt(json, "line", 1), 1));
    if (location != null) {
      msg.addAttribute("locationHint", location);
    }
    sink.emit(msg, id, parent);
  }

  private void scenarioEnter(JsonObject json) {
    String featurePath = getString(json, "feature");
    if (featurePath == null) {
      return;
    }
    int depth = getInt(json, "callDepth", 0);
    int line = getInt(json, "line", 1);
    String name = getString(json, "name");
    String display = name != null ? name : basename(featurePath) + ":" + line;
    int id = nextId++;
    String thread = threadOf(json);
    final int parent = depth == 0
      ? topFeatureIdByPath.getOrDefault(featurePath, 0)
      : atDepth(lastFeatureAtDepth, thread, depth);
    scenarioIdByKey.put(scenarioKey(json, featurePath, depth), id);
    setAtDepth(lastScenarioAtDepth, thread, depth, id);
    nameById.put(id, display);
    // Scenarios reached through a call are reported as suites, not tests: they stay visible (and
    // navigable) under the calling step, but the run's totals keep counting the scenarios the user
    // actually asked to run rather than inflating with every called scenario.
    ServiceMessageBuilder msg = depth == 0
      ? ServiceMessageBuilder.testStarted(display)
      : ServiceMessageBuilder.testSuiteStarted(display);
    String location = sink.resolveLocation(featurePath, line);
    if (location != null) {
      msg.addAttribute("locationHint", location);
    }
    sink.emit(msg, id, parent);
  }

  private void scenarioExit(JsonObject json) {
    String featurePath = getString(json, "feature");
    if (featurePath == null) {
      return;
    }
    int depth = getInt(json, "callDepth", 0);
    Integer id = scenarioIdByKey.remove(scenarioKey(json, featurePath, depth));
    if (id == null) {
      return;
    }
    String display = nameById.remove(id);
    if (depth > 0) {
      // Opened as a suite in scenarioEnter, so it has to be closed as one. A failure here still
      // reaches the tree: the calling scenario fails too and reports the error itself.
      sink.emit(ServiceMessageBuilder.testSuiteFinished(display), id, 0);
      return;
    }
    boolean passed = getBoolean(json, "passed");
    ServiceMessageBuilder msg;
    if (passed) {
      msg = ServiceMessageBuilder.testFinished(display);
      int duration = getInt(json, "durationMillis", -1);
      if (duration >= 0) {
        msg.addAttribute("duration", String.valueOf(duration));
      }
    } else {
      String error = getString(json, "error");
      msg = ServiceMessageBuilder.testFailed(display)
        .addAttribute("message", error != null ? error : "failed");
    }
    sink.emit(msg, id, 0);
  }

  /**
   * Renders a finished step as output on the scenario that ran it, so a Karate 2 run shows what it
   * did rather than only whether it passed. The v1 path gets this for free from its log lines; v2
   * reports structurally, so without this the test tree has no step detail at all.
   *
   * <p>Attributed to the deepest currently-open scenario: steps of a called feature then land on
   * that feature's scenario node rather than on the caller's.
   */
  private void stepExit(JsonObject json) {
    String text = getString(json, "text");
    if (text == null) {
      return;
    }
    int scenarioId = deepestOpenScenario(threadOf(json));
    String scenarioName = scenarioId == 0 ? null : nameById.get(scenarioId);
    if (scenarioName == null) {
      return;
    }
    String prefix = getString(json, "prefix");
    String status = getString(json, "status");
    StringBuilder line = new StringBuilder();
    line.append(prefix == null ? "*" : prefix).append(' ').append(text);
    if (status != null && !"passed".equals(status)) {
      line.append("   [").append(status).append(']');
    }
    line.append('\n');
    // Whatever the step printed - print, karate.log - indented beneath it, so output stays with the
    // step that produced it instead of being lost (v2 emits it nowhere else the tree can reach).
    String stepLog = getString(json, "stepLog");
    if (stepLog != null && !stepLog.isBlank()) {
      stepLog.strip().lines().forEach(l -> line.append("    ").append(l).append('\n'));
    }
    sink.emit(ServiceMessageBuilder.testStdOut(scenarioName).addAttribute("out", line.toString()),
      scenarioId, 0);
  }

  /**
   * The innermost scenario still open on this thread, so called-feature steps attach to the called
   * scenario, and concurrent scenarios never claim each other's steps.
   */
  private int deepestOpenScenario(String thread) {
    List<Integer> list = lastScenarioAtDepth.get(thread);
    if (list == null) {
      return 0;
    }
    for (int depth = list.size() - 1; depth >= 0; depth--) {
      Integer id = list.get(depth);
      if (id != null && id != 0 && nameById.containsKey(id)) {
        return id;
      }
    }
    return 0;
  }

  private void featureExit(JsonObject json) {
    int depth = getInt(json, "callDepth", 0);
    Integer id;
    if (depth == 0) {
      String path = getString(json, "path");
      if (path == null) {
        path = getString(json, "relativePath");
      }
      id = path == null ? null : topFeatureIdByPath.remove(path);
    } else {
      int atDepth = atDepth(lastFeatureAtDepth, threadOf(json), depth);
      id = atDepth == 0 ? null : atDepth;
    }
    if (id == null) {
      return;
    }
    String display = nameById.remove(id);
    if (display == null) {
      return;
    }
    sink.emit(ServiceMessageBuilder.testSuiteFinished(display), id, 0);
  }

  private static String scenarioKey(JsonObject json, String featurePath, int depth) {
    String refId = getString(json, "refId");
    if (refId == null) {
      refId = String.valueOf(getInt(json, "line", -1));
    }
    return featurePath + "|" + refId + "|" + depth;
  }

  private static String basename(String path) {
    int slash = path.lastIndexOf('/');
    return slash < 0 ? path : path.substring(slash + 1);
  }

  /** Events with no thread stamp (sequential run, or a runner older than the stamping) share a key. */
  private static String threadOf(JsonObject json) {
    String thread = getString(json, "thread");
    return thread == null ? "" : thread;
  }

  private int atDepth(Map<String, List<Integer>> byThread, String thread, int depth) {
    List<Integer> list = byThread.get(thread);
    if (list == null) {
      return 0;
    }
    return depth >= 0 && depth < list.size() && list.get(depth) != null ? list.get(depth) : 0;
  }

  private void setAtDepth(Map<String, List<Integer>> byThread, String thread, int depth, int id) {
    List<Integer> list = byThread.computeIfAbsent(thread, k -> new ArrayList<>());
    while (list.size() <= depth) {
      list.add(null);
    }
    list.set(depth, id);
  }

  private static @Nullable String getString(JsonObject json, String key) {
    JsonElement e = json.get(key);
    return e == null || e.isJsonNull() ? null : e.getAsString();
  }

  private static int getInt(JsonObject json, String key, int fallback) {
    JsonElement e = json.get(key);
    return e == null || e.isJsonNull() || !e.isJsonPrimitive() ? fallback : e.getAsInt();
  }

  private static boolean getBoolean(JsonObject json, String key) {
    JsonElement e = json.get(key);
    return e != null && !e.isJsonNull() && e.isJsonPrimitive() && e.getAsBoolean();
  }
}
