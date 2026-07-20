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
2. Phase 3 polish (see docs/KARATE2.md): `karate-base.js`/`karate-boot.js` recognition,
   README/marketplace description mention of Karate 2, possibly refreshing the embedded karate-js
   sources under `src/main/java/io/karatelabs/js/`.
3. The integration-test CI job has never run: its YAML was not validated locally, and these tests
   have only ever run on Windows, never Linux.

## Known issues, deliberately unfixed

- The v1 converter labels anonymous scenarios with a generated id (`270885978##`) where v2 shows
  `called.feature:4`. Cosmetic, pre-existing.
- The version override is application-level, not per-project — awkward when working across projects
  on different majors. Less pressing now that detection is per-module.

## Open questions for the user

- Should the v1 bundled-karate fallback also exist for v2 (currently intentionally not)?
- Marketplace/changelog wording for "experimental" Karate 2 support.
- Should a failing integration test block the release draft? It does not today.
