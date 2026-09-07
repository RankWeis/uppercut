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
      (261) - the UI test pins `platformVersion`. CI's verifyPlugin now covers every RELEASE, EAP
      and RC from since-build upward by default, so this is a spot-check of the install rather than
      of the API surface.

## 6b. Feature-file debugging (both majors)

Nothing automated covers the debug session itself (see `docs/DEBUGGER.md`); the agent, the channel and
the value rendering have unit tests, and `:v1:debugHarness` / `:v2:debugHarness` cover the runner
halves against real suites, but the IDE half has only this list. Run these in
`testProjects/karate-versions`. **Verified 2026-09-06** on macOS / IDEA 2026.2 - v2 against phase 2,
then the whole list again on v1 after phase 4 moved it onto the same debugger.

- [x] Gutter breakpoint on a step line shows one red dot, on both modules. *(A leftover `java-line`
      breakpoint from a previous version draws a second one on that line alone - see the refusal
      below.)*
- [x] Debug on a project that still holds a Java line breakpoint in a feature file refuses to start
      and names the file, in a balloon in the corner.
- [x] After deleting it, Debug pauses before the breakpointed step, brings frames and variables
      forward, and the frame names the step and scenario.
- [x] Variables show what Karate holds - `id`, `num`, `env`, and a called feature's `result` as a map
      that opens - not Java frames from inside karate-core.
- [x] Evaluate runs Karate expressions: `id`, `result.greeting`. A deliberately wrong expression
      shows Karate's own error **and the run still finishes with nothing failed** - on Karate 1 that
      is the failed-reason restore in `KarateV1DebugAdapter`.
- [x] Resume continues to the next breakpoint or to the end; the test tree finishes normally.
- [x] A breakpoint added while the run is suspended is picked up. *(Note: a run started from a single
      scenario only executes that scenario, so a breakpoint in another scenario of the same file will
      not be reached - that is not a fault.)*
- [x] The step buttons continue to the next breakpoint and say so.
- [x] Stop while paused ends the run with no java process left behind (`jps`).
- [x] Karate 1 and Karate 2 both open a single Debug tab; no JVM debugger attaches. *(Unless the run
      configuration asks for one - see 6c.)*

A breakpoint in the `@Karate.Test` JUnit class proves nothing here - a Karate run configuration
launches the plugin's runner, not that class.

## 6c. The opt-in JVM debugger

Off by default, so none of 6b changes when it is not ticked. Most of this is now automated:
`Karate2UITest.theJvmDebuggerIsOptInAndRunsBesideTheKarateOne` runs `sample/javacall.feature` twice
in a real IDE, unticked and ticked, and `JdwpClashProbe` covers the mechanism headlessly on both
majors (`:v1:jdwpClash`, `:v2:jdwpClash`). What is left by hand is what an assertion cannot see.

Automated - listed so a failure is read against what it was meant to prove, not re-walked:

- [x] Unticked: Debug opens one tab and no "Karate JVM debugger" tab exists.
- [x] Ticked: a second tab appears and the Java breakpoint in `Helper.compute` suspends the run there.
- [x] The Karate tab still stops on its own feature-line breakpoint in the same run.
- [x] A breakpoint on the first Java the run touches binds - `Helper` is loaded a step before the
      Karate breakpoint, so this only passes because the handshake held the run for the attach.
- [x] Stop the Java tab alone: it detaches and the Karate run finishes.
- [x] **Each tab comes forward when its own session stops** - the JVM debugger's, and the Karate tab
      when a feature-line breakpoint hits. Both directions, because asserting only one let the fix
      for that one re-break the other. A tab that does not come forward also means the editor never
      moves to the stopped line, which is how it was noticed. Confirmed by hand as well, 2026-09-07.
- [x] A v2 run stops in the **v2** module's helper. Both modules declare `sample.Helper` at the same
      line on purpose: asserted on the file and not only the line, because a line-only check could
      not have failed either way. What makes it resolve is the run's module on the remote
      configuration - remove that and this is the test that goes red.

Still by hand. **Walked end to end on 2026-09-07**, which is what found five bugs no assertion had:
the JVM tab not coming forward, the Karate tab then not coming forward either, a v2 run resolving to
v1's source, the attach racing JVM startup, and a latent enablement flake in the suite itself. Each
is asserted now.

- [x] Ticked: the Java tab stops in `Helper.compute` *and* the Karate tab stops on the step - both,
      in one run.
- [x] **Attach the JVM debugger too** appears in the run configuration's Test Options and survives a
      close and reopen of the dialog. *(The setting's round-trip is unit-tested; this is the widget.)*
      **Walked 2026-09-07.** Note for anyone repeating it: the option is per run configuration, and
      the gutter creates one per feature and per scenario, so **Edit configuration templates > Karate**
      is where to set it once for everything created afterwards.
- [x] The Java tab shows real frames and locals - `seed`, and `doubled` after one Step Over - not
      just a suspended session.
- [x] While the Java tab is stopped, the Karate tab's variables, Evaluate and Resume do nothing, and
      it comes back when the Java tab resumes. **This is the expected behaviour**, documented on the
      troubleshooting page; the point of the check is that it recovers rather than staying dead.
      Automating it would mean asserting on a timeout, which is the flakiest thing this suite could
      own - and `JdwpClashProbe` already pins the mechanism. *Not exercised by hand: issuing a Karate
      Resume during the freeze and finding it acted on afterwards. The probe covers that half.*
- [x] Resuming the Karate breakpoint carries the run on into the Java one, in the same run.
- [x] Stop the Karate tab: the run ends and the Java tab ends with it. No `KarateTestRunner` left in
      `jps` afterwards.

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
