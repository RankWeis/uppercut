package com.rankweis.uppercut.karate.completion;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Members of the {@code karate} object, per major version.
 *
 * <p>Extracted from the engines themselves rather than from documentation:
 * {@code com.intuit.karate.core.ScenarioBridge} for 1.5.1 and {@code io.karatelabs.core.KarateJs}
 * for 2.1.1. Regenerate the same way when {@code karateVersion} moves - see
 * {@code .claude/skills/karate-version-parity}.
 *
 * <p>The two differ more than the step keywords do. Karate 2 turned v1's getters into plain
 * properties ({@code karate.getEnv()} became {@code karate.env}), dropped the WebSocket and
 * perf-capture helpers, and added several builtins of its own - {@code uuid}, {@code faker},
 * {@code expect}, {@code sysenv}/{@code sysprop} among them. Offering the union would suggest
 * calls that fail at runtime, so completion uses the set matching the project's Karate version.
 */
public final class KarateBuiltins {

  private KarateBuiltins() {
  }

  /** {@code com.intuit.karate.core.ScenarioBridge}, karate-core 1.5.1. */
  private static final Set<String> V1 = Set.of(
    "abort", "append", "appendTo", "call", "callSingle", "callonce", "capturePerfEvent", "channel",
    "compareImage", "configure", "consume", "distinct", "doc", "embed", "eval", "exec", "extract",
    "extractAll", "fail", "filter", "filterKeys", "forEach", "fork", "fromString", "get", "getEngine",
    "getEnv", "getFeature", "getInfo", "getLogger", "getOs", "getPrevRequest", "getProperties",
    "getRequest", "getResponse", "getScenario", "getScenarioOutline", "getTagValues", "getTags",
    "http", "jsonPath", "keysOf", "log", "lowerCase", "map", "mapWithKey", "match", "merge", "pause",
    "pretty", "prettyXml", "proceed", "range", "read", "readAsBytes", "readAsStream", "readAsString",
    "remove", "render", "repeat", "set", "setXml", "setup", "setupOnce", "signal", "sizeOf", "sort",
    "start", "stop", "toAbsolutePath", "toBean", "toCsv", "toJava", "toJavaFile", "toJs", "toJson",
    "toList", "toMap", "toString", "typeOf", "urlDecode", "urlEncode", "valuesOf", "waitForHttp",
    "waitForPort", "webSocket", "webSocketBinary", "wrapFunction", "write", "xmlPath");

  /** {@code io.karatelabs.core.KarateJs}, karate-core 2.1.1. */
  private static final Set<String> V2 = Set.of(
    "abort", "append", "appendTo", "call", "callSingle", "callonce", "channel", "config", "configure",
    "distinct", "doc", "driver", "embed", "env", "eval", "exec", "expect", "extract", "extractAll",
    "fail", "faker", "feature", "filter", "filterKeys", "forEach", "fork", "fromJson", "fromString",
    "get", "http", "info", "jsonPath", "keysOf", "log", "logger", "lowerCase", "map", "mapWithKey",
    "match", "merge", "os", "pause", "pretty", "prettyXml", "prevRequest", "proceed", "properties",
    "range", "read", "readAsBytes", "readAsStream", "readAsString", "remove", "render", "repeat",
    "request", "response", "scenario", "scenarioOutline", "set", "setXml", "setup", "setupOnce",
    "signal", "sizeOf", "sort", "start", "stop", "suite", "sysenv", "sysprop", "tagValues", "tags",
    "toAbsolutePath", "toBean", "toBytes", "toCsv", "toJava", "toJson", "toString", "typeOf",
    "urlDecode", "urlEncode", "uuid", "valuesOf", "waitForHttp", "waitForPort", "write", "xmlPath");

  /** Members available on the {@code karate} object, sorted, for the given major version. */
  public static List<String> forVersion(boolean karateV2) {
    return List.copyOf(new TreeSet<>(karateV2 ? V2 : V1));
  }
}
