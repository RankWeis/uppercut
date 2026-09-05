# Karate 2.x support — state of `claude/karate2-spike`

Read `docs/KARATE2.md` first for the architecture and the verified Karate 2 API facts.
This file records what is done, what is deliberately not done, and the traps already paid for.

## Current state

Everything below is committed on this branch and verified green locally (unit tests, checkstyle,
and the IDE integration test) against IntelliJ IDEA Ultimate 2026.2 on Windows.

Working pipeline: per-module classpath detection (`KarateRunConfiguration#karateLibraryRoots` ->
`isKarateV2`, override in Settings > Tools > Karate) -> `--karate-major-version 2` ->
`KarateV2TestRunner` (reflection on `io.karatelabs.core.Runner` + a `RunListener` proxy) ->
`<<UPPERCUT-V2>>` JSON event lines on stdout -> `KarateV2EventProcessor` -> id-based SM test tree.

### Fixtures

`testProjects/karate-versions/` is a standalone project (NOT in settings.gradle) with a module per
Karate major: `v1` on karate-junit5 1.5.1 and `v2` on karate-junit6 2.1.1. One project on purpose -
it mirrors a monorepo mid-migration, guards per-module detection, and lets one IDE session cover
both runner paths. `./gradlew -p testProjects/karate-versions :v2:eventProbe` dumps the live v2
event stream.

### Tests

- Unit: `KarateV2EventProcessorTest`, `KarateVersionDetectionTest` — written JUnit4-style because
  the root `test` task does not use the JUnit platform launcher.
- Integration: `Karate2UITest` boots one IDE and performs four checks — the v2 feature (tree shape,
  called-feature nesting, `locationHint` resolution, console text), the v2 failing feature (failure
  mapping, no converter state carried between runs), the v1 feature (the other runner entirely),
  and the settings override displacing detection. Also asserts the plugin logged no errors.
- CI runs `integrationTest` only on push, not on pull requests (`.github/workflows/build.yml`).

## First Linux CI run (both failures fixed)

The integration-test job ran for the first time on Linux (previously Windows-only) and both tests
failed — for reasons in the test harness, not the plugin. Both are fixed on this branch; the
diagnosis is recorded here so it is not rediscovered:

- **`Karate2UITest` → `initializationError`.** `@BeforeAll` ran
  `CommandChain().waitForSmartMode().importGradleProject().waitForSmartMode()` to win a race against
  project-SDK registration. On headless Linux the *startup auto-import wins that race and finishes*
  (idea.log: resolution task "executed in 66405 ms", JDK_17 applied), so the explicit re-import is
  redundant and the platform cancels the second sync ("Could not build GradleSourceSetModel model.
  Build cancelled."), which surfaces as "Gradle sync failed" and, from `@BeforeAll`, fails all seven
  tests as one error. Fix: the explicit re-import is now wrapped in try/catch and falls back to
  `waitForSmartMode()` — the guard still helps when auto-import loses, but a cancellation no longer
  sinks the class.
- **`UppercutUITest` → `NoSuchElementException: List is empty`.** The legacy v1 test launches the run
  by clicking the gutter icon; driver clicks are physical (screen coordinates) and land nowhere under
  headless xvfb, so the gutter lookup is empty. It has been fragile since before this branch. Its v1
  run path is now covered click-free by `Karate2UITest.v1ModuleRunsThroughTheV1Runner`, so it is
  `@Disabled` (not deleted) pending a rewrite of `clickRunTest` to `invokeAction("RunClass")`.

## Bugs this work surfaced (all fixed here)

- **Logback was mandatory.** The shared runner installed its `<<UPPERCUT>>` console appender
  unconditionally. Karate 1.x provided logback transitively; 2.x does not, so the runner JVM failed
  to link and every Karate 2 run died as "Test framework quit unexpectedly" over an empty tree.
- **Version detection was project-wide.** One module's karate-junit6 forced the v2 runner onto
  every module. Detection now reads the run's own module classpath.
- **Called scenarios inflated the totals.** They are reported as suites now: still nested and
  navigable, but the count matches the scenarios the user asked to run.

## Gotchas already paid for (do not re-break)

- `TestFrameworkType.Starter` must be routed with
  `configurationName = "integrationTestImplementation"` or Starter classes don't resolve.
- Kotlin version must align with the Starter framework's pinned kotlin-reflect; a stdlib/reflect
  mismatch fails at runtime with `ClassNotFoundException: kotlin.jvm.internal.KotlinGenericDeclaration`.
- `org.jetbrains.teamcity:serviceMessages` is needed at integration-test runtime (Starter's
  TeamCityReporter) and is not pulled in transitively.
- JDK extraction needs `--add-exports=java.base/sun.nio.fs=ALL-UNNAMED`; IntelliJ's
  MultiRoutingFileSystem implements those internals.
- `path.to.build.plugin` points at the prepareSandbox plugin **directory** ->
  `PluginConfigurator.installPluginFromDir` (NOT installPluginFromPath).
- `useRelease()` = latest IU release; fine while untilBuild is 262.*. If untilBuild ever lags the
  newest IDE the plugin silently disables and the gutter never appears - pin `useRelease("<version>")`.
- The v1 `RuntimeHook` proxy CANNOT work on Karate 2 (class removed); don't try to unify the runners.
- More driver traps (physical clicks, gutter locators, `@Remote` module ids) live in
  `.claude/skills/ide-integration-tests/`. Runner debugging technique: `.claude/skills/debug-test-runner/`.

## Not done

1. Editor-level UI coverage: completion offering Karate keywords, go-to-definition on
   `call read('called.feature')`, no error annotations on a valid feature. These exercise PSI and
   the conditional `plugin-withJs.xml` loading, which `LightPlatformTestCase` cannot reach.
2. Phase 3 polish (see docs/KARATE2.md): all done. The README/marketplace mention landed with the 3.0.0
   release commit (the marketplace description is generated from the README's plugin-description block).
   ~~Refreshing the embedded karate-js sources~~ is done - the modern-JS syntaxes were added
   surgically to `io.karatelabs.js` (see `KarateJsModernSyntaxTest` and
   `src/test/testData/js_modern.feature`). ~~`karate-base.js`/`karate-boot.js` recognition~~ turned
   out to be nothing: the plugin has no filename-keyed config handling to extend (KARATE2.md, 3b).
3. ~~The integration-test CI job has never run.~~ It has now run on Linux; see "First Linux CI run"
   above. `Karate2UITest` is fixed to pass headless and `UppercutUITest` is disabled pending a
   click-free rewrite.

## Known issues, deliberately unfixed

- The v1 converter labels anonymous scenarios with a generated id (`270885978##`) where v2 shows
  `called.feature:4`. Cosmetic, pre-existing.
- The version override is application-level, not per-project — awkward when working across projects
  on different majors. Less pressing now that detection is per-module.

## Decisions (2026-09-05)

- The bundled-Karate fallback stays v1-only. Reasoning in `docs/risks/karate-v1-regressions.md`,
  "Decisions on record"; revisit only on real reports of Karate 2 users with `karate-core` but no
  runnable JUnit artifact.
- Marketplace/changelog wording: "early access", as already used in CHANGELOG.md and the README's
  plugin-description block. Not "experimental".
- `releaseDraft` stays gated on `integration-test` (`needs: [ build, test, verify, integration-test ]`
  in `.github/workflows/build.yml`). A failing IDE integration test is meant to block a release draft.
- Phase-3 task 4 (pause-only debugging for v2) is shelved: too much scope for the release that carries
  Karate 2 early access. The design in `docs/karate2-pause-debugging.md` (PR #342 branch) stands for later.
