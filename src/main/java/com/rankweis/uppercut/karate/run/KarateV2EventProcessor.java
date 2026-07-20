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
  private final List<Integer> lastScenarioAtDepth = new ArrayList<>();
  private final List<Integer> lastFeatureAtDepth = new ArrayList<>();

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
    final int parent = depth == 0 ? 0 : atDepth(lastScenarioAtDepth, depth - 1);
    if (depth == 0) {
      topFeatureIdByPath.put(path, id);
    }
    setAtDepth(lastFeatureAtDepth, depth, id);
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
    final int parent = depth == 0
      ? topFeatureIdByPath.getOrDefault(featurePath, 0)
      : atDepth(lastFeatureAtDepth, depth);
    scenarioIdByKey.put(scenarioKey(json, featurePath, depth), id);
    setAtDepth(lastScenarioAtDepth, depth, id);
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
      int atDepth = atDepth(lastFeatureAtDepth, depth);
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

  private static int atDepth(List<Integer> list, int depth) {
    return depth >= 0 && depth < list.size() && list.get(depth) != null ? list.get(depth) : 0;
  }

  private static void setAtDepth(List<Integer> list, int depth, int id) {
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
