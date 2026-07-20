---
name: ide-integration-tests
description: Run and debug the IDE Starter/Driver UI tests (Karate2UITest, UppercutUITest) that boot a real IntelliJ. Use when running integrationTest, when a driver test fails or hangs, or when adding UI test coverage. Covers the run command, where the real logs live, and the driver API traps that cost hours.
---

# IDE integration tests

These tests boot a real IntelliJ (IDE Starter), install the built plugin, and drive it (Driver).
One run is ~45s in-IDE plus a ~4 GB first-time download into `out/ide-tests/`.

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
  Confirm a module name exists before guessing:
  `grep -o "intellij\.[a-zA-Z.]*" out/ide-tests/cache/builds/IU-*/product-info.json | sort -u`
- **Attach process listeners the moment the run descriptor appears.** Attaching after the fact
  loses all output from a process that died early - which is exactly the case you need to debug.
- **Match run descriptors by configuration name.** A second run appends to
  `getAllDescriptors()` rather than replacing, so `.first()` returns the earlier run.

## Adding coverage

Booting the IDE dominates the cost, so prefer more assertions (or a second run) inside the
existing session over a new `@Test` that boots again.

Assert on tree *shape*, not just counts - walk `SMTestRunnerResultsFormRef.getTestsRootNode()`.
`getLocationUrl()` on a node verifies navigation actually resolves; counts alone pass on a
right-sized, wrong-shaped tree.
