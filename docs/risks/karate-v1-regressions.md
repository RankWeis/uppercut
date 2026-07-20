# Karate 1.x regression risks on the Karate 2 branch

Living document. Every entry names the shared code that both runner paths depend on, what could
break for the v1 users who make up the current install base, and what currently guards it.
Update the status column when a risk is mitigated, accepted, or retired — don't delete rows;
a retired risk with its reasoning attached stops the next person from re-litigating it.

Baseline for "before": `main` prior to the Karate 2 work. The v1 execution path is:
`KarateRunConfiguration.createJavaParameters` → `KarateTestRunner.main` (v1 branch) →
`RuntimeHook` proxy → `<<UPPERCUT>>` logback appender → regex scrapers in
`KarateOutputToGeneralTestEventsConverter`.

| # | Risk | Mechanism | Guard | Status |
|---|------|-----------|-------|--------|
| R1 | Bundled-karate fallback fires on working v1 setups | Detection moved from a project-wide library scan to the run module's classpath. Karate jars attached to a sibling module (root-module run configs, shared test-support modules) become invisible, `--karate-provided` injects bundled karate-junit5 1.5.1 over the user's real Karate version | Module scan is only authoritative when the module has a `karate-*` jar; otherwise detection widens back to the project-wide scan (`moduleScanIsAuthoritative`). Unit-tested | **Mitigated** |
| R2 | AUTO misclassifies a v1 module as v2 | Any jar matching `karate-(core|junit6)-2\..*` in the module's *recursive* dependencies flips the run to the v2 runner, which then dies on a v1 classpath (`ClassNotFoundException: io.karatelabs.core.Runner`). A stale transitive karate-core 2.x anywhere in the tree is enough | None beyond the regex being name-anchored. The settings override (V1) is the escape hatch, and the mismatch guard produces a clear message for the pinned cases | **Accepted** — needs a real-world report to justify more machinery |
| R3 | v1 console pipeline hangs off one reflective call | The `<<UPPERCUT>>` appender is now installed via `Class.forName("...UppercutLogbackAppender")` with a blanket `catch (Throwable)`. If that class ever fails to load for a packaging reason (jar split, shading), every v1 run silently degrades to an empty tree, because all three converter regexes only match prefixed lines | The v1 leg of `Karate2UITest` runs the real packaged plugin and asserts the tree — a packaging break fails CI. The catch prints one stderr line naming the cause | **Guarded by test** |
| R4 | Version override contradicts the classpath | Pinning V1 with only Karate 2 jars (or V2 with none) used to die deep in the wrong runner with an unrelated-looking `NoSuchMethodException` after silently injecting the bundled v1 jar | `checkVersionOverrideMatchesClasspath` refuses the run up front, naming the setting. AUTO never hits it. Unit- and UI-tested | **Mitigated** |
| R5 | v2 protocol short-circuit swallows user output | `processEventText` consumes any line starting with `<<UPPERCUT-V2>>` before the v1 scrapers see it. A v1 user's application printing that literal prefix loses those lines | None. Contrived by construction — the prefix exists to be unambiguous | **Accepted** |
| R6 | Appender still installs before the version branch | `getOutputStreamAppender()` ran before `parseArgs`, so a *v2* project that supplies its own logback had its console appenders detached and output prefixed `<<UPPERCUT>>` — v1-shaped noise on the v2 console (review finding 03) | Install moved after arg parsing onto the v1 branch only; the appender still precedes all v1 test output. The v1 leg of the UI test verifies the ordering, the v2 console assertions verify the absence | **Mitigated** |
| R7 | Shared-runner changes alter v1 log forwarding semantics | `KarateTestRunner` now logs through plain slf4j instead of casting to logback's `Logger`. If a v1 project resolves a non-logback slf4j binding first, runner warnings route differently than before | Cosmetic at worst; the appender still captures the root logger | **Accepted** |

## Standing invariants (break one of these and v1 users notice)

- The v1 branch of `KarateTestRunner.main` and everything it calls (`doTest`, `createRuntimeHook`,
  caller-chain reflection) must keep working against karate-core **1.5.1** — that exact jar ships
  inside the plugin as the bundled fallback.
- The `<<UPPERCUT>>` prefix format written by `UppercutLogbackAppender` is load-bearing: the
  converter's `UPPERCUT_LOG_PATTERN`, `SCENARIO_NAME`, and `FEATURE_FILE_NAME` regexes parse it.
  Changing the encoder pattern requires changing all three in lockstep.
- `KarateSettingsState.loadState` null-coalesces the version preference to AUTO, so settings files
  written by pre-Karate-2 plugin versions must keep deserializing to AUTO.
- The v1 leg of `Karate2UITest` (`v1/src/test/java/sample/users-v1.feature`) is the only end-to-end
  execution of this path. Keep it in any test-suite restructuring; before it existed, the logback
  linkage bug shipped invisible.

## How to check this document's claims

- v1 end to end: `./gradlew integrationTest --tests com.rankweis.uppercut.karate.ui.Karate2UITest`
  (third in-IDE run is the v1 module).
- Runner against a real v1 classpath outside the IDE: see `.claude/skills/debug-test-runner/`.
- Detection logic in isolation: `KarateVersionDetectionTest`.
