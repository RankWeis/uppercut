package com.rankweis.uppercut.karate.run;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.intellij.execution.testframework.sm.ServiceMessageBuilder;
import com.intellij.openapi.diagnostic.Logger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * <p>Called features arrive between the calling step's events with {@code callDepth} incremented. They
 * get no nodes: the tree holds only the features and scenarios the user ran, and a called feature's
 * steps are listed, indented, in the output of the scenario that called it. Each thread tracks its
 * open scenario and current call depth, so parallel scenarios never claim each other's steps.</p>
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
  // Whether passed steps are listed in the run output. Off by default: a listing of every step that
  // passed repeats the feature file and buries the output that matters (#404). Steps that did not
  // pass are always listed, and what any step printed is always kept.
  private final boolean showPassedSteps;
  private int nextId = 1;
  private final Map<String, Integer> topFeatureIdByPath = new HashMap<>();
  private final Map<String, Integer> scenarioIdByKey = new HashMap<>();
  private final Map<Integer, String> nameById = new HashMap<>();
  // Per-thread, because Karate runs scenarios in parallel and their events interleave on one stream.
  // The runner stamps every event with its emitting thread; events without one share a single key,
  // which is the correct behaviour for a sequential run.
  private final Map<String, Integer> openScenarioByThread = new HashMap<>();
  // How deep in calls each thread currently is: 0 inside a scenario the user ran, 1 inside a feature
  // that scenario called, and so on. Called steps are indented by it in the caller's output.
  private final Map<String, Integer> callDepthByThread = new HashMap<>();
  // Output of called steps not yet placed, per thread and call depth. A called feature's steps finish
  // before the step that called it, so they wait here until that step's line has been written.
  private final Map<String, List<StringBuilder>> calledOutputByThread = new HashMap<>();
  // Feature titles already in the tree, so a second feature with the same title can be told apart.
  private final Set<String> featureTitles = new HashSet<>();

  public KarateV2EventProcessor(@NotNull EventSink sink) {
    this(sink, false);
  }

  public KarateV2EventProcessor(@NotNull EventSink sink, boolean showPassedSteps) {
    this.sink = sink;
    this.showPassedSteps = showPassedSteps;
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
    try {
      dispatch(type, json);
    } catch (RuntimeException e) {
      // The payload shapes are Karate's, not ours; a field that changed type in a newer 2.x must cost
      // one event, not the rest of the run.
      LOG.warn("Failed to process Karate 2 event " + type, e);
    }
    return true;
  }

  private void dispatch(String type, JsonObject json) {
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
  }

  private void featureEnter(JsonObject json) {
    String path = getString(json, "path");
    if (path == null) {
      return;
    }
    // A called feature gets no node: the tree lists only what the user ran, and the call's steps are
    // shown in the calling scenario's output instead (see stepExit). Nodes for called features, under
    // the caller or at the top level, turned every chain of calls into a staircase or a long run of
    // identical siblings (#404).
    if (getInt(json, "callDepth", 0) > 0) {
      return;
    }
    String title = getString(json, "name");
    String display = title != null ? title : basename(path);
    // Two features with the same title are two identical rows otherwise. The file name goes on the
    // second and later ones only: the first is in the tree before anyone knows a twin is coming.
    if (title != null && !featureTitles.add(title)) {
      display = title + " (" + basename(path) + ")";
    }
    int id = nextId++;
    final int parent = 0;
    topFeatureIdByPath.put(path, id);
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
    String thread = threadOf(json);
    callDepthByThread.put(thread, depth);
    if (depth > 0) {
      // A called scenario: no node, its steps go to the calling scenario (see featureEnter).
      return;
    }
    int line = getInt(json, "line", 1);
    String name = getString(json, "name");
    String display = name != null ? name : basename(featurePath) + ":" + line;
    // An outline example is named from the outline's title with its placeholders filled in, so
    // "greeting <name>" gives "greeting alice". A title with no placeholders gives every example the
    // same name; number those, by example row, which is stable however the examples are scheduled.
    if (getBoolean(json, "isOutlineExample") && name != null && name.equals(outlineTitle(json, featurePath))) {
      display = name + " [" + (getInt(json, "exampleIndex", 0) + 1) + "]";
    }
    int id = nextId++;
    final int parent = topFeatureIdByPath.getOrDefault(featurePath, 0);
    scenarioIdByKey.put(scenarioKey(json, featurePath, depth), id);
    openScenarioByThread.put(thread, id);
    calledOutputByThread.remove(thread);
    nameById.put(id, display);
    ServiceMessageBuilder msg = ServiceMessageBuilder.testStarted(display);
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
    String thread = threadOf(json);
    // Back in the caller. A failure inside the call needs no node of its own: the call step fails,
    // and so does the calling scenario, with Karate's error.
    callDepthByThread.put(thread, Math.max(depth - 1, 0));
    Integer id = scenarioIdByKey.remove(scenarioKey(json, featurePath, depth));
    if (id == null) {
      return;
    }
    openScenarioByThread.remove(thread, id);
    // Called output whose call step never reported (an aborted call) still belongs to this scenario.
    String unplaced = takeCalledOutput(thread, 1, true);
    if (!unplaced.isEmpty()) {
      sink.emit(ServiceMessageBuilder.testStdOut(nameById.get(id)).addAttribute("out", unplaced), id, 0);
    }
    String display = nameById.remove(id);
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
   * Renders a finished step as output on the scenario that ran it. A step that did not pass is listed
   * with its status, so a failure shows where it happened; a passed step is listed only when
   * {@code showPassedSteps} is on. What a step printed is kept either way: beneath the step's line
   * when it is listed, on its own when it is not.
   *
   * <p>Attributed to the scenario the user ran on this thread, including the steps of any feature it
   * called, which are indented two spaces per call level. A called feature's steps finish before the
   * step that called it, so they are held until that step's line is written and listed beneath it.
   */
  private void stepExit(JsonObject json) {
    String text = getString(json, "text");
    if (text == null) {
      return;
    }
    String thread = threadOf(json);
    Integer openId = openScenarioByThread.get(thread);
    int scenarioId = openId == null ? 0 : openId;
    String scenarioName = scenarioId == 0 ? null : nameById.get(scenarioId);
    if (scenarioName == null) {
      return;
    }
    int depth = callDepthByThread.getOrDefault(thread, 0);
    String indent = "  ".repeat(depth);
    String status = getString(json, "status");
    boolean passed = "passed".equals(status);
    boolean listed = showPassedSteps || !passed;
    StringBuilder line = new StringBuilder();
    if (listed) {
      String prefix = getString(json, "prefix");
      line.append(indent).append(prefix == null ? "*" : prefix).append(' ').append(stepText(json, text));
      if (!passed && status != null) {
        line.append("   [").append(status).append(']');
      }
      line.append('\n');
    }
    // Whatever the step printed - print, karate.log - indented beneath it when the step is listed, so
    // output stays with the step that produced it. Kept when the step is not listed: a project with no
    // logging provider has a silent console, and this is the only place its print output appears.
    String stepLog = getString(json, "stepLog");
    if (stepLog != null && !stepLog.isBlank()) {
      String logIndent = listed ? indent + "    " : indent;
      stepLog.strip().lines().forEach(l -> line.append(logIndent).append(l).append('\n'));
    }
    // Steps of whatever this step called, already finished, go beneath it.
    line.append(takeCalledOutput(thread, depth + 1, false));
    if (depth > 0) {
      // Itself a called step: wait for the step that called it.
      calledOutput(thread, depth).append(line);
      return;
    }
    if (line.isEmpty()) {
      return;
    }
    sink.emit(ServiceMessageBuilder.testStdOut(scenarioName).addAttribute("out", line.toString()),
      scenarioId, 0);
  }

  /** The held output of called steps at this call depth on this thread, created if absent. */
  private StringBuilder calledOutput(String thread, int depth) {
    List<StringBuilder> byDepth = calledOutputByThread.computeIfAbsent(thread, k -> new ArrayList<>());
    while (byDepth.size() <= depth) {
      byDepth.add(new StringBuilder());
    }
    return byDepth.get(depth);
  }

  /**
   * Takes the held output at this depth, or with {@code deeper} at this depth and below in nesting
   * order (deepest last), leaving those depths empty; "" when nothing is held.
   */
  private String takeCalledOutput(String thread, int depth, boolean deeper) {
    List<StringBuilder> byDepth = calledOutputByThread.get(thread);
    if (byDepth == null) {
      return "";
    }
    StringBuilder out = new StringBuilder();
    int last = deeper ? byDepth.size() - 1 : Math.min(depth, byDepth.size() - 1);
    for (int d = depth; d <= last; d++) {
      out.append(byDepth.get(d));
      byDepth.get(d).setLength(0);
    }
    return out.toString();
  }

  /**
   * The step as written: keyword and text. Karate 2 sends {@code * def id = response.id} as text
   * {@code id = response.id}; the runner adds the {@code keyword} it dropped. Steps without one - a
   * bare expression, Given/When prose - send none and are written as their text.
   */
  private static String stepText(JsonObject json, String text) {
    String keyword = getString(json, "keyword");
    if (keyword == null || keyword.isBlank()) {
      return text;
    }
    return text.isEmpty() ? keyword : keyword + " " + text;
  }

  /** The outline's own title, from the example's outlineSlug ({@code <feature path>:<title>}). */
  private static @Nullable String outlineTitle(JsonObject json, String featurePath) {
    String slug = getString(json, "outlineSlug");
    String prefix = featurePath + ":";
    return slug != null && slug.startsWith(prefix) ? slug.substring(prefix.length()) : null;
  }

  private void featureExit(JsonObject json) {
    if (getInt(json, "callDepth", 0) > 0) {
      return;
    }
    String path = getString(json, "path");
    if (path == null) {
      path = getString(json, "relativePath");
    }
    Integer id = path == null ? null : topFeatureIdByPath.remove(path);
    if (id == null) {
      return;
    }
    String display = nameById.remove(id);
    if (display == null) {
      return;
    }
    sink.emit(ServiceMessageBuilder.testSuiteFinished(display), id, 0);
  }

  /**
   * Identifies a scenario between its ENTER and EXIT. The thread is part of the key: under
   * parallelism, two scenarios can be calling the same feature at the same time, so path, refId and
   * depth alone collide - the second ENTER would overwrite the first's id and the first EXIT would
   * close the wrong node. Karate runs a scenario, including everything it calls, on one thread, so
   * ENTER and EXIT always carry the same stamp.
   */
  private static String scenarioKey(JsonObject json, String featurePath, int depth) {
    String refId = getString(json, "refId");
    if (refId == null) {
      refId = String.valueOf(getInt(json, "line", -1));
    }
    return threadOf(json) + "|" + featurePath + "|" + refId + "|" + depth;
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

  /** A non-primitive (a future payload shape) is rendered as JSON text rather than thrown on. */
  private static @Nullable String getString(JsonObject json, String key) {
    JsonElement e = json.get(key);
    if (e == null || e.isJsonNull()) {
      return null;
    }
    return e.isJsonPrimitive() ? e.getAsString() : e.toString();
  }

  private static int getInt(JsonObject json, String key, int fallback) {
    JsonElement e = json.get(key);
    if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) {
      return fallback;
    }
    try {
      return e.getAsInt();
    } catch (NumberFormatException | UnsupportedOperationException ex) {
      return fallback;
    }
  }

  private static boolean getBoolean(JsonObject json, String key) {
    JsonElement e = json.get(key);
    return e != null && !e.isJsonNull() && e.isJsonPrimitive() && e.getAsBoolean();
  }
}
