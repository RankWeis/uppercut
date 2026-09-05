# Manual test checklist — Karate 2 release

What the automated suite cannot verify. Run against `./gradlew runIde` (sandbox IDE) unless the
row says otherwise. The fixture project is `testProjects/karate-versions` — open it as a Gradle
project so both modules import.

Automated already (don't re-test by hand): launch → test tree for v2 pass/fail and v1, called
scenario nesting/counts, location URLs, console text, protocol hygiene, override refusal,
per-module detection. See `Karate2UITest`.

## 1. Real interaction (the UI test deliberately avoids the mouse)

- [ ] Click the gutter icon on `v2/.../users.feature` line 1 → popup shows Run/Debug → Run works.
- [ ] Same on a single `Scenario:` line → only that scenario runs (SINGLE_SCENARIO).
- [ ] Right-click in the editor → context menu offers the Karate run configuration.
- [x] Right-click the `v2/.../sample` *folder* in the Project view → the menu offers
      **Run 'Karate tests in 'sample''** (not only Gradle's "Tests in 'sample'"), and the run
      goes through the plugin (feature nodes with scenarios beneath, not `SampleTest` →
      `testSample`). **Verified 2026-09-05** on macOS; this is what the producer's `shouldReplace`
      change fixed.
- [ ] Cmd+7 / Alt+7 (Structure) on a feature lists the feature, its scenarios and their steps, and
      clicking a node moves the caret. The element was rewritten on the public API for 2026.2.
- [ ] Double-click a scenario in the results tree → editor opens `src/test/java/...`, not
      `build/resources/...`, caret on the scenario line.
- [ ] Click a step-log line in the console → hyperlinks (if any) resolve.

## 2. Debugging (no automated coverage at all)

- [x] v1 module: set a breakpoint in a feature, Debug from the gutter → breakpoint hits,
      variables render, resume works. **Verified 2026-07-20.** This pass found the bug fixed in
      `7b18aab`: any library path containing a space (`C:\Program Files\...`, the default JDK
      location on Windows) aborted the library scan, so the debugger got no position manager.
      Re-verified after the fix — breakpoints hit.
- [ ] v2 module: **feature-file breakpoints are not planned**, see the GitHub issue and
      `site/status.md`. Confirm on a v2 module that: Debug prints a one-line notice at the top
      of the console ("Karate 2: feature-file breakpoints will not pause the run. Java
      breakpoints in step definitions still work."), the run completes normally (no error
      dialog, no hung session), a breakpoint set on a Gherkin step is silently skipped, and a
      Java breakpoint in step-definition code (if the fixture has any) does pause.

## 3. Settings UI (the test flips the service, not the form)

- [ ] Settings > Tools > Karate shows the Karate version combo (AUTO/V1/V2); changing it and
      hitting Apply persists across IDE restart.
- [ ] The **?** button on that page and the "What each setting means" link both open
      https://rankweis.github.io/uppercut/settings in the browser.
- [ ] Pin V1, run the v2 feature → error balloon/dialog names the setting and says how to fix it
      (not a stack trace).
- [ ] Set back to AUTO → same feature runs green with no restart.

## 4. Other build layouts (fixture is Gradle-only)

- [ ] Maven Karate 2 project (`target/test-classes` instead of `build/resources/test`):
      run from gutter, tree builds, navigation opens `src/test/java`.
- [ ] Feature files under `src/test/resources` instead of `src/test/java`.
- [ ] Project with spaces and non-ASCII in its path (Windows: `C:\Users\...\my proj (v2)\`).
- [ ] Single-module project that has BOTH karate 1.x and 2.x jars (the ambiguous case the
      override exists for): AUTO picks v2 (junit6 present); pinning V1 is refused only if no
      v1 jars are present - with both present it must run v1.

## 5. Scale and concurrency (fixture is 2-3 scenarios)

- [ ] A feature with 30+ scenarios: tree stays responsive, order stable.
- [x] `parallelism > 1` on the v2 module: tree correct - the runner stamps every event with its
      thread and the processor keys scenarios on it, so nesting holds under parallelism.
      **Verified 2026-09-05** at parallelism 4 over the `sample` folder. To *prove* concurrency
      rather than eyeball it: a throwaway feature with four scenarios each doing
      `* karate.pause(2000)` runs in ~8 s at parallelism 1 and ~2 s at 4 (Karate's own
      `efficiency` figure in the summary is per feature, so ignore it for a single-feature probe).
- [ ] Scenario Outline with examples: one node per example row, failures attribute to the row.
- [ ] Stop button mid-run: process dies, tree marks unfinished, IDE usable, second run clean.

## 6. Upgrade path (nothing automated can cover this)

- [ ] Install current marketplace release in a fresh sandbox, set some settings, run a v1
      project. Then install this build over it: settings survive (version pref = AUTO),
      v1 project still runs identically.
- [ ] plugin.xml compatibility range: verify the build installs on the oldest supported IDE
      (261) - the UI test pins `platformVersion`, and CI's verifyPlugin only covers the newest
      release and EAP. `./gradlew verifyPlugin -PpluginVerifierScope=all` covers every major from
      since-build up; run it before a release.

## 7. Environment matrix

- [ ] Linux run of the whole automated suite (first CI run covers this - watch it).
- [x] macOS: one manual gutter-run smoke on any module. **Verified 2026-09-05**, and `Karate2UITest`
      passes 7/7 on macOS after the fixture-import fixes recorded in
      `.claude/skills/ide-integration-tests/SKILL.md` ("Fixture import" section).
- [ ] A JDK older than the toolchain on PATH (user machines rarely match ours): runIde project
      import still resolves and runs.

## Triage notes

Anything found here that a driver API could have caught belongs in `Karate2UITest`, not just
fixed - that's how the logback and navigation bugs became permanent regression tests. Findings
that are v1-behavioral go in `docs/risks/karate-v1-regressions.md`.
