---
name: ide-integration-tests
description: Run and debug the IDE Starter/Driver UI tests (Karate2UITest, UppercutUITest) that boot a real IntelliJ. Use when running integrationTest, when a driver test fails or hangs, or when adding UI test coverage. Covers the run command, where the real logs live, and the driver API traps that cost hours.
---

# IDE integration tests

These tests boot a real IntelliJ (IDE Starter), install the built plugin, and drive it (Driver).
One run is ~45s in-IDE plus a ~4 GB first-time download into `out/ide-tests/`.

`Karate2UITest` is the suite that matters: one IDE, seven ordered tests (v2 pass, v2 fail, v1,
Scenario Outline, go-to-definition, version override, no plugin errors logged). `UppercutUITest`
is `@Disabled` - it clicks. The IDE it boots is the `platformVersion` the plugin is built against
(`useRelease(platformVersion())`), not the newest release.

## Running

```bash
./gradlew integrationTest --tests com.rankweis.uppercut.karate.ui.Karate2UITest
```

**Use the fully-qualified class name.** PowerShell expands `--tests "*Karate2*"` against the
filesystem, so it silently becomes a directory name and Gradle reports "No tests found".

Run it in the background and tee the output - the interesting parts scroll past the tail:

```bash
./gradlew integrationTest --tests <FQN> --console=plain 2>&1 | tee run.log
```

## Where the truth is when it fails

The Gradle output shows the assertion; the *cause* is usually in the IDE's own log.

| What | Where |
|---|---|
| IDE log (exceptions, click failures, run config execution) | `out/ide-tests/tests/IU-*/<contextName>/log/idea.log` |
| Screenshots | `out/ide-tests/tests/IU-*/<contextName>/log/screenshots/` |
| The SDK-setup IDE's separate log | `.../<contextName>/setupSdk/log/idea.log` |

Screenshots capture the **entire desktop**, not just the IDE - check before sharing them anywhere.

Two IDEs launch per run: a brief one that registers the JDK (`setupSdk`), then the real driver
session. That is expected.

## Fixture import — four ways it silently fails

`Karate2UITest` opens a copy of `testProjects/karate-versions` in a fresh IDE and needs Gradle to
import it. When import fails, all run tests fail with a one-entry classpath and a misleading
`ClassNotFoundException` - read `idea.log` before trusting the assertion. Four failure modes are
already wired around; watch for them if the fixture ever grows a new one.

- **Auto-import race on Linux CI.** An unconditional `importGradleProject()` in `@BeforeAll` races
  with the startup auto-import: on headless Linux the auto-import wins and finishes, and the
  platform cancels the second sync ("Build cancelled" → "Gradle sync failed"). Fix: wait for the
  auto-import's own indicators, check whether the v1/v2 modules exist (`driver.getModules()`), and
  import explicitly only if not.
- **Project-close race on macOS.** The same unconditional re-import collides with the project
  rename mid-sync ("project is closed") and commits a model without the v1/v2 modules. Same fix as
  above.
- **`Invalid Gradle JDK configuration found` on macOS.** The IDE picks a linked project's Gradle
  JVM from the project SDK (racing `setupSdk`), then `org.gradle.java.home`, then `$JAVA_HOME` -
  which the IDE process on a Mac does not have. Two fixes, both needed: write
  `org.gradle.java.home=<downloaded JDK>` into the fixture copy's `gradle.properties`, **and copy
  the fixture without `.idea/`, `.gradle/`, `build/`, `out/`** - a gitignored `.idea/gradle.xml`
  from opening the fixture in an IDE by hand carries a bad `gradleJvm=#JAVA_HOME` that the
  starter would reuse verbatim.
- **`cannot find resource: classpath:outline.feature` on macOS.** The temp dir is `/var/…`, a
  symlink to `/private/var/…`; Gradle canonicalises roots, so the project index can't place the
  feature under any module. `Files.createTempDirectory(...).toRealPath()`.

## Before guessing at xpaths: use the live hierarchy

The Starter already launches the IDE with `-Dexpose.ui.hierarchy.url=true`, so while a driver test
is running (or paused at a breakpoint) the whole Swing tree is browsable at
`http://localhost:<port>/api/remote-driver/` - the port is in the IDE's JVM options in `idea.log`.
Read the tree there instead of inferring locators from failures. `RobotService.saveHierarchy(dir)`
dumps the same thing to `ui.html` for a post-mortem.

The remote-driver README (`platform/remote-driver/README.md` in JetBrains/intellij-community) is
the upstream reference for the driver API; the SDK docs at
<https://plugins.jetbrains.com/docs/intellij/welcome.html> cover the platform side.

## Structuring a test class

Booting the IDE dominates cost, so share one across tests rather than writing one giant test:
hold a `BackgroundRun` in a companion, start it in `@BeforeAll`, and `run.closeIdeAndWait()` in
`@AfterAll` (the pattern the README documents). Order the tests with
`@TestMethodOrder(MethodOrderer.OrderAnnotation::class)` when later ones depend on earlier state,
and reach the driver through `run.driver`. A failure then names the behavior that broke.

## Driver API traps

- **Never click.** Driver clicks are physical screen coordinates, so any window over the IDE
  swallows them - `idea.log` says `Click was unsuccessful`, and the test is unrunnable on a
  machine someone is using. Assert the UI element exists via the API, then trigger behavior with
  `driver.invokeAction("<ActionId>")`. `RunClass` is the context-run action gutter icons delegate to.
- **Popups are separate windows.** `driver.ui.popup()` finds them; searching inside `ideFrame()`
  never will.
- **`gutter()` is a singleton locator** and throws once a second editor exists (a run console
  contributes its own `EditorGutterComponentImpl`). Query
  `frame.xx("//div[@class='EditorGutterComponentImpl']", GutterUiComponent::class.java).list()`
  and search across all of them.
- **`xQuery { byType("SomeSwingClass") }` frequently matches nothing.** Prefer the SDK's own
  helper for the component (`gutter()`, `popup()`, `list()`), or an explicit
  `//div[@class='...']` xpath.
- **`waitForIndicators(waitSmartLongEnough = true)`** blocks for 10 quiet seconds after the IDE
  goes idle. Worth it once after the Gradle import; pure dead time on later launches.
- **`@Remote` needs the owning module** for classes outside the core classloader, or the driver
  answers `No such class ... in plugin null`. Example:
  `@Remote("...SMTestProxy", plugin = "intellij.testRunner.plugin/intellij.platform.smRunner")`.
- **Process exit is not console settled.** `ConsoleViewImpl.text` reads the editor document, which
  fills from a deferred buffer on a timer, and the results form clears and reprints the console
  when the finished run selects a node. A sub-second run can have `isProcessTerminated()` true and
  an empty document. Poll for the text you expect (`waitFor { console.getText().contains(...) }`)
  before asserting on it - `awaitResults` does.
- **The test tree is not the Gradle tree.** If a run shows `SampleTest` → `testSample` → dynamic
  test names, it went through Gradle's "Tests in ..." configuration, not the plugin. The plugin's
  folder configuration is named "Karate tests in '<folder>'".
- **Read `idea.log` before the assertion when every run test fails with a one-entry classpath.**
  That shape always means the fixture did not import; the four failure modes are in the
  "Fixture import" section above.
  Confirm a module name exists before guessing:
  `grep -o "intellij\.[a-zA-Z.]*" out/ide-tests/cache/builds/IU-*/product-info.json | sort -u`
- **Attach process listeners the moment the run descriptor appears.** Attaching after the fact
  loses all output from a process that died early - which is exactly the case you need to debug.
- **Match run descriptors by configuration name.** A second run appends to
  `getAllDescriptors()` rather than replacing, so `.first()` returns the earlier run.

## Adding coverage

New checks belong in the shared-IDE class above - another `@Test` costs seconds, a new class costs
another ~40s boot.

Assert on tree *shape*, not just counts - walk `SMTestRunnerResultsFormRef.getTestsRootNode()`.
`getLocationUrl()` on a node verifies navigation actually resolves; counts alone pass on a
right-sized, wrong-shaped tree.
