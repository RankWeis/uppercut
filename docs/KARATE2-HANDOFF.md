# Karate 2.x support — state of the 3.0.0 release branch

Read `docs/KARATE2.md` first for the architecture and the verified Karate 2 API facts.
This file records what is done, what is deliberately not done, and the traps already paid for.

## Current state

Everything below is committed and verified green locally (unit tests, checkstyle, and the IDE
integration test) against IntelliJ IDEA Ultimate 2026.2 - first on Windows, then on macOS
(2026-09-05), which is where most of the integration-test traps below were found.

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
- Unit, run configurations: `KarateRunConfigurationProducerTest` (the folder run displaces
  Gradle's "Tests in ..." only when the folder holds features).
- Integration: `Karate2UITest` boots one IDE and runs seven ordered tests — the v2 feature (tree
  shape, called-feature nesting, `locationHint` resolution, console text), the v2 failing feature
  (failure mapping, no converter state carried between runs), the v1 feature (the other runner
  entirely), a Scenario Outline (one node per example row), go-to-definition on `call read(...)`,
  the settings override displacing detection, and finally that the plugin logged no errors.
- CI runs `integrationTest` only on push, not on pull requests (`.github/workflows/build.yml`), and
  the release draft is gated on it.

## Getting the fixture imported in the test IDE (three rounds of fixes)

`Karate2UITest` opens a copy of `testProjects/karate-versions` in a fresh IDE and needs Gradle to
import it. Every failure mode below ended the same way - all four run tests fail with a one-entry
classpath and a misleading `ClassNotFoundException` - so read `idea.log` before the assertion:

- **Linux CI, first run:** `@BeforeAll` ran an unconditional `importGradleProject()` to win a race
  against project-SDK registration. On headless Linux the startup auto-import wins and finishes,
  and the platform cancels the second sync ("Build cancelled" → "Gradle sync failed"), sinking the
  class from `@BeforeAll`.
- **macOS:** the unconditional re-import instead collided with the project rename mid-sync
  ("project is closed") and committed a model without the v1/v2 modules. Fix: wait for the
  auto-import's own indicators, check whether the `v1.test`/`v2.test` modules exist
  (`driver.getModules()`), and import explicitly only if not.
- **macOS, "Invalid Gradle JDK configuration found":** the IDE picks a linked project's Gradle JVM
  from the project SDK (racing `setupSdk`), then `org.gradle.java.home`, then `JAVA_HOME` - which
  the IDE process on a Mac does not have. Two fixes, both needed: the fixture copy gets a
  `gradle.properties` with `org.gradle.java.home=<the downloaded JDK>`, and the copy *skips*
  `.idea`, `.gradle`, `build` and `out`. That last one was the actual cause: opening the fixture by
  hand in an IDE leaves a gitignored `.idea/gradle.xml` with `gradleJvm=#JAVA_HOME`, which the test
  IDE reused verbatim instead of resolving anything.
- **macOS, `cannot find resource: classpath:outline.feature`:** the temp dir is `/var/...`, a
  symlink to `/private/var/...`; Gradle reports roots canonicalized, so the project index could
  not place the feature under any module. `Files.createTempDirectory(...).toRealPath()`.
- **The v1 test read an empty console:** `ConsoleViewImpl` paints from a deferred buffer and the
  results form clears/reprints it when the finished run selects a node; a sub-second run had the
  process terminated and the document momentarily empty. `awaitResults` now polls for Karate's
  summary before asserting on console text.

`UppercutUITest` (the legacy v1 test) is `@Disabled`, not deleted: it launches the run by clicking
the gutter icon, driver clicks are physical and land nowhere under headless xvfb, and its v1 run
path is covered click-free by `Karate2UITest.v1ModuleRunsThroughTheV1Runner`. A rewrite of
`clickRunTest` to `invokeAction("RunClass")` would revive it.

## Bugs this work surfaced (all fixed here)

- **Logback was mandatory.** The shared runner installed its `<<UPPERCUT>>` console appender
  unconditionally. Karate 1.x provided logback transitively; 2.x does not, so the runner JVM failed
  to link and every Karate 2 run died as "Test framework quit unexpectedly" over an empty tree.
- **Version detection was project-wide.** One module's karate-junit6 forced the v2 runner onto
  every module. Detection now reads the run's own module classpath.
- **Called scenarios inflated the totals.** They are reported as suites now: still nested and
  navigable, but the count matches the scenarios the user asked to run.
- **No Karate run on a Gradle package folder.** Gradle's test producer replaces every competing
  configuration when tests run through Gradle, and the Karate producer replaced back only for
  folders made entirely of features - so Karate's own layout (features beside the runner class)
  never got a Karate folder run in Gradle projects. Now any folder holding a `.feature` does.
- **Runs launched with no classpath.** A feature outside any module, or in a module with no
  `karate-*` jar, used to launch a JVM that died with "Must have karate-core on the classpath";
  `classpathProblem` refuses up front and names the cause (and the troubleshooting page).
- **Structure view depended on an unexposed platform class.** `GherkinStructureViewElement`
  extended `PsiTreeElementBase`, which Plugin Verifier reports missing from 2026.2 on; rewritten on
  the public `StructureViewTreeElement` API.
- **A stray backtick hung the editor** on the fallback JS path: the template-literal loop consumed
  nothing at end of input. Now a parse error.

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
- `useRelease(platformVersion())` pins the test IDE to the platform the plugin is built against
  (`platform.version` system property from `build.gradle.kts`). With no until-build the plugin would
  also load on the next major, where a moved `@Remote` module id would fail the release draft for
  reasons unrelated to the change being merged.
- The v2 fixture declares `logback-classic` as `testRuntimeOnly`: Karate 2 ships no logging
  provider outside its fat jar, and without one Gradle runs of the fixture print only SLF4J's
  no-providers warning. The plugin's own v2 output does not need it.
- Do not right-click the `sample` folder and pick Gradle's "Tests in 'sample'" expecting the plugin:
  that runs `SampleTest` through Gradle and karate-junit6 runs the features itself. The Karate run is
  "Karate tests in 'sample'".
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

- Plugin Verifier: CI checks the newest release and newest EAP of the platform major only;
  `-PpluginVerifierScope=all` covers every major from since-build up (the release check by hand).
  Kian's policy: support the three latest IntelliJ releases, raise since-build only when a
  deprecation would otherwise force a fork.
- CI cache: extracted IDEs (`caches/*/transforms`) are excluded and the verify job is read-only;
  the repository's 10 GB cache budget held ~23 GB of entries before, so everything was being evicted.
- User docs live on GitHub Pages (`site/`), linked from the Marketplace change notes, the README, the
  Settings page's `?` button and the run-refusal messages.

- The bundled-Karate fallback stays v1-only. Reasoning in `docs/risks/karate-v1-regressions.md`,
  "Decisions on record"; revisit only on real reports of Karate 2 users with `karate-core` but no
  runnable JUnit artifact.
- Marketplace/changelog wording: "early access", as already used in CHANGELOG.md and the README's
  plugin-description block. Not "experimental".
- `releaseDraft` stays gated on `integration-test` (`needs: [ build, test, verify, integration-test ]`
  in `.github/workflows/build.yml`). A failing IDE integration test is meant to block a release draft.
- Phase-3 task 4 (pause-only debugging for v2) is shelved: too much scope for the release that carries
  Karate 2 early access. The design in `docs/karate2-pause-debugging.md` (PR #342 branch) stands for later.
