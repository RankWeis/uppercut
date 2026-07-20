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
- [ ] Double-click a scenario in the results tree → editor opens `src/test/java/...`, not
      `build/resources/...`, caret on the scenario line.
- [ ] Click a step-log line in the console → hyperlinks (if any) resolve.

## 2. Debugging (no automated coverage at all)

- [x] v1 module: set a breakpoint in a feature, Debug from the gutter → breakpoint hits,
      variables render, resume works. **Verified 2026-07-20.** This pass found the bug fixed in
      `7b18aab`: any library path containing a space (`C:\Program Files\...`, the default JDK
      location on Windows) aborted the library scan, so the debugger got no position manager.
      Re-verified after the fix — breakpoints hit.
- [x] v2 module: Debug currently behaves as plain Run (v2 debugSupport not wired). Confirm it
      degrades gracefully - runs to completion, no error dialog, no hung session.
      **Verified 2026-07-20** — degrades gracefully, matching the early-access note in the
      changelog and marketplace description.

## 3. Settings UI (the test flips the service, not the form)

- [ ] Settings > Tools > Karate shows the Karate version combo (AUTO/V1/V2); changing it and
      hitting Apply persists across IDE restart.
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
- [ ] `parallelism > 1` on the v2 module: tree correct-ish (called-feature nesting is heuristic
      under parallelism - events carry no thread id; expect right totals, possibly odd nesting).
- [ ] Scenario Outline with examples: one node per example row, failures attribute to the row.
- [ ] Stop button mid-run: process dies, tree marks unfinished, IDE usable, second run clean.

## 6. Upgrade path (nothing automated can cover this)

- [ ] Install current marketplace release in a fresh sandbox, set some settings, run a v1
      project. Then install this build over it: settings survive (version pref = AUTO),
      v1 project still runs identically.
- [ ] plugin.xml compatibility range: verify the build installs on the oldest supported IDE
      (261) - the UI test only exercises the newest.

## 7. Environment matrix

- [ ] Linux run of the whole automated suite (first CI run covers this - watch it).
- [ ] macOS: one manual gutter-run smoke on any module.
- [ ] A JDK older than the toolchain on PATH (user machines rarely match ours): runIde project
      import still resolves and runs.

## Triage notes

Anything found here that a driver API could have caught belongs in `Karate2UITest`, not just
fixed - that's how the logback and navigation bugs became permanent regression tests. Findings
that are v1-behavioral go in `docs/risks/karate-v1-regressions.md`.
