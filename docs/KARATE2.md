# Karate 2.x Support — Verified Findings & Plan

Status: **phase-0 spike**. API facts below were verified against the real
`karate-core-2.1.1.jar` / `karate-junit6-2.1.1.jar` from Maven Central (javap), not from docs alone.

## Why the current runner cannot work on v2

Uppercut's runtime integration (`KarateTestRunner` subproject) reflects on the v1
`com.intuit.karate.junit5.Karate` builder and proxies `com.intuit.karate.RuntimeHook`,
reading engine internals (`ScenarioRuntime.featureRuntime`, `FeatureRuntime.caller`,
`ScenarioCall.parentRuntime`).

Verified against 2.1.1:

| v1 API the runner uses | Status in v2 |
|---|---|
| `junit5.Karate` no-arg constructor + `path()/workingDir()/hook()/parallel()` | **Gone.** The v2 shim only has `run(String...)`, `relativeTo`, `tags`, `karateEnv`, `systemProperty`, `configDir`, `outputDir`, `scenarioName`, `dryRun`, `toV2()` |
| `com.intuit.karate.RuntimeHook` | **Class no longer exists** |
| Engine internals (`ScenarioRuntime` fields etc.) | Rewritten (`io.karatelabs.core.*`, karate-js engine, virtual threads) |

So v1-style reflection fails at `getDeclaredConstructor()` immediately. A separate v2 path is
mandatory — but it is *simpler* than the v1 path, because v2 has a public event API.

## The v2 API we target (verified, karate-core 2.1.1)

```
io.karatelabs.core.Runner.builder() -> Runner$Builder
  .path(String...)            // classpath: and file paths
  .tags(String...)
  .workingDir(String | Path)
  .karateEnv(String)
  .configDir(String)
  .listener(io.karatelabs.core.RunListener)     // <-- the integration point
  .outputJsonLines(boolean)                     // alt: karate-events.jsonl file
  .outputHtmlReport(boolean)
  .debugSupport(RunInterceptor, DebugPointFactory)  // future: real debugging!
  .parallel(int) -> SuiteResult                 // terminal
```

```java
public interface RunListener {
  boolean onEvent(RunEvent event);              // single abstract method - proxy/lambda friendly
}
public interface RunEvent {
  RunEventType getType();   // SUITE/FEATURE/OUTLINE/SCENARIO/STEP/HTTP _ENTER/_EXIT, ERROR, PROGRESS
  long getTimeStamp();
  Map<String, Object> toJson();                 // self-serializing - no internals reflection needed
}
```

There is also a back-compat `com.intuit.karate.Runner.builder()` shim in v2 core with
`path/tags/karateEnv/workingDir(String)/parallel(int)` but **no listener()**, which is why we
target the native `io.karatelabs.core.Runner` instead.

## Architecture

```
KarateRunConfiguration (IDE side)
  scans project libraries: karate-(core|junit6)-2.* present?
    yes -> adds --karate-major-version 2
    no  -> v1 behavior (incl. bundled-karate fallback; fallback stays v1-only)

KarateTestRunner.main (user JVM side)
  --karate-major-version 2 ? KarateV2TestRunner : v1 doTest()

KarateV2TestRunner
  io.karatelabs.core.Runner.builder() via reflection (no compile-time karate dep,
  one runner jar serves both versions; v1/v2 classes cannot both be compileOnly
  deps anyway - same FQNs, different APIs)
  + RunListener proxy -> stdout lines:  <<UPPERCUT-V2>> SCENARIO_ENTER {json}

IDE console (phase 2)
  KarateV2EventsConverter parses <<UPPERCUT-V2>> lines -> ServiceMessageBuilder test tree
  (sibling of the v1 regex-based KarateOutputToGeneralTestEventsConverter)
```

## Phases

| Phase | Contents | Status |
|---|---|---|
| 0 | Spike: verify v2 API surface, runner dispatch skeleton,  `testProjects/karate-versions/` fixture | done |
| 1 | Settings override (AUTO/V1/V2) in `KarateSettingsState` | done |
| 2 | `KarateV2EventProcessor` (JSON events -> id-based test tree), nesting via callDepth, failure mapping, unit tests | done |
| 2b | `Karate2UITest` end-to-end integration test (IDE Starter/Driver); covers both runner paths, the failure path, and the settings override. Surfaced the logback and project-wide-detection bugs | done |
| 3a | Modern JS in the embedded engine: `?.`, `??`/`??=`, `class`/`extends`/`super`/`this`, `continue`, `void`, BigInt - added to `io.karatelabs.js` rather than re-vendoring | done |
| 3b | Editor niceties: `karate-base.js`/`karate-boot.js` recognition, README/marketplace mention of Karate 2 | |
| later | Real debugging via v2 `debugSupport(RunInterceptor, DebugPointFactory)` | |

## Spike results — all questions ANSWERED (live run against karate 2.1.1)

The `eventProbe` task was executed against real Karate 2.1.1; observed:

- **`toJson()` payloads are complete for the test tree.**
  `SCENARIO_ENTER`: `{feature=<path>, name, line, refId=[index:line], callDepth, tags}`;
  `SCENARIO_EXIT` adds `{passed, skipped, durationMillis}` and, on failure, `error=<message>`.
  `FEATURE_ENTER`: `{path, name, line, callDepth, tags}`. An `ERROR` event additionally fires
  with `{feature, scenario, message}`.
- **Called features nest trivially.** The called feature emits its own `FEATURE_ENTER` /
  `SCENARIO_ENTER` / ... / `FEATURE_EXIT` between the calling step's `STEP_ENTER`/`STEP_EXIT`,
  with `callDepth=1`. A simple ENTER/EXIT stack in the converter replaces the entire v1
  caller-chain reflection hack.
- **`onEvent` returning `true` keeps the suite running** and does NOT suppress Karate's own
  console output.
- **Line filtering works:** `path("classpath:spike/users.feature:12")` ran exactly the scenario
  at line 12 — SINGLE_SCENARIO mode maps directly.
- **`karate-config.js` is discovered from the classpath root** (visible in stepLog).
- Gradle projects need the classic `sourceSets.test.resources.srcDir("src/test/java")` setup for
  feature files — unchanged from v1.

Remaining to verify in the IDE (phase 2): interplay of the `<<UPPERCUT>>` logback appender with
v2's logging, and event paths (relative to workingDir/classpath output dir) mapping back to
source `.feature` files — the v1 converter's source-root lookup should transfer as-is.

## Editor impact: JS syntax only

Lexer/parser/PSI/highlighting/completion/navigation are Gherkin-generic. `@lock` tags and new
`karate.*` builtins need no grammar changes.

The one gap was the embedded `io.karatelabs.js` engine - a hand-written port of karate-js used
only when the IntelliJ JavaScript plugin is absent (`KarateJsNoPluginExtension`). It predated
several JS features karate-js 2.1.1 supports, so modern JS in a feature file drew false errors on
that fallback path. Phase 3a closed that: 9 tokens added (95 -> 104), plus the grammar for each,
and reserved words accepted as property names / object keys. The engine's other 13 new tokens are
karate-js's own Gherkin tokens (`G_*`), which the plugin does not use - it lexes Gherkin itself.
See `.claude/skills/karate-version-parity/` for how the two token sets are diffed.
