# Handoff: Karate 2.x support — continue on branch `claude/karate2-spike`

Context transfer from a Claude Code cloud session (2026-07-20) to a local session.
Read `docs/KARATE2.md` first for the architecture and the verified Karate 2 API facts.

## Current state (all committed on this branch)

- Branch is **rebased onto main** post-PR-#332 (IntelliJ 2026.2 support, Java 25 toolchain,
  Gradle wrapper 9.6.1, IntelliJ Platform Gradle Plugin 2.18.1).
- Working pipeline: classpath detection (`KarateRunConfiguration.isKarateV2`, override in
  Settings > Tools > Karate) -> `--karate-major-version 2` -> `KarateV2TestRunner`
  (reflection on `io.karatelabs.core.Runner` + `RunListener` proxy) -> `<<UPPERCUT-V2>>` JSON
  event lines -> `KarateV2EventProcessor` -> id-based SM test tree.
- Unit tests pass (`KarateV2EventProcessorTest`, `KarateVersionDetectionTest`) - written
  JUnit4-style because the root `test` task does not use the JUnit platform launcher.
- `testProjects/karate-v2/` is a standalone sample project (NOT in settings.gradle) with karate-junit6
  2.1.1; `./gradlew -p testProjects/karate-v2 eventProbe` dumps the live v2 event stream.
- Integration test `Karate2UITest` (IDE Starter/Driver, Kotlin): opens a temp copy of
  testProjects/karate-v2, installs the built plugin, clicks the feature gutter, asserts 3 passed
  scenarios, takes screenshots to `build/reports/integrationTest/screenshots/karate2/`.

## What is NOT yet verified (do these first)

1. **Compile on the rebased base**: `gradlew compileJava compileTestJava
   compileIntegrationTestKotlin` - the cloud session ran out of disk before verifying.
   Risk spots: Kotlin 2.3.20 with `jvmToolchain(25)` (build.gradle.kts ~line 130); if
   "Unknown JVM target 25", try keeping toolchain 25 but setting kotlin jvmTarget lower.
2. **Full check**: `gradlew check -x integrationTest` (unit tests + checkstyle).
3. **The IT run**: `gradlew integrationTest --tests "*Karate2*"`. Never completed in the
   cloud (disk + proxy limits). It downloads IU (latest release, 2026.2) + Corretto 21 JDK
   into `out/ide-tests/` (~4 GB first run). A real IDE window opens - do not touch it.

## Session learnings / gotchas already fixed (do not re-break)

- `TestFrameworkType.Starter` must be routed with
  `configurationName = "integrationTestImplementation"` or Starter classes don't resolve.
- Kotlin version must align with the Starter framework's pinned kotlin-reflect
  (2.3.20); stdlib/reflect mismatch fails at runtime with
  `ClassNotFoundException: kotlin.jvm.internal.KotlinGenericDeclaration`.
- `path.to.build.plugin` points at the prepareSandbox plugin **directory** ->
  `PluginConfigurator.installPluginFromDir` (NOT installPluginFromPath: "Archive uppercut
  is not supported").
- `useRelease()` = latest IU release; fine now that untilBuild is 262.*. If the plugin's
  untilBuild ever lags the newest IDE, the plugin silently disables and the gutter never
  appears - pin `useRelease("<version>")` in that case.
- The v1 `RuntimeHook` proxy CANNOT work on Karate 2 (class removed); don't try to unify
  the runners. See docs/KARATE2.md for the verified v2 API surface.
- `src/test/resources/karate.log` is test-run output, now gitignored - don't commit it.
- Old `UppercutUITest` was dead code until this branch re-added the Kotlin plugin; it
  compiles again and its plugin-install call was fixed too. Its GitHub sample project
  (RankWeis/uppercutTestProject) is v1 - it exercises the v1 path.

## Next steps (in order)

1. Verify compile + check on this base (above).
2. Run `Karate2UITest`; iterate until green; review the screenshots.
3. Consider a `Karate2UITest` failure-case variant (spike2/broken.feature - assert 1 failed).
4. Phase 3 polish (see docs/KARATE2.md): karate-base.js/karate-boot.js recognition,
   README/marketplace description mention of Karate 2, possibly refresh the embedded
   karate-js sources under src/main/java/io/karatelabs/js/.
5. Open a PR from `claude/karate2-spike` when green (user creates PRs; branch pushed).

## Open questions for the user

- Should the v1 bundled-karate fallback also exist for v2 (currently intentionally not)?
- Marketplace/changelog wording for "experimental" Karate 2 support.
