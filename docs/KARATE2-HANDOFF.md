# Karate 2.x — decisions on record

Karate 2 support shipped as early access in 3.0.0. This file keeps the handful of decisions from
that work that don't live anywhere more discoverable. For anything else:

- [`site/status.md`](../site/status.md) — user-facing "what works, per Karate version"
- [`docs/KARATE2.md`](KARATE2.md) — architecture and the verified Karate 2 API facts
- [`.claude/skills/ide-integration-tests/`](../.claude/skills/ide-integration-tests/SKILL.md) —
  IDE Starter/Driver traps, including the four fixture-import failure modes
- [`.claude/skills/debug-test-runner/`](../.claude/skills/debug-test-runner/SKILL.md) —
  command-line runner replay when a run in the IDE misbehaves
- `git log KarateV2*` and the CHANGELOG — the bugs paid for during the release

## Decisions

- **The bundled Karate 1 fallback stays v1-only.** Karate 2's `karate-junit6` brings
  `karate-core` with it, so a v2 project always has `karate-core` when it has any v2 dependency
  at all — there's no gap to fill, and a bundled runner would test against a different Karate
  than the build declares. Reasoning in
  [`docs/risks/karate-v1-regressions.md`](risks/karate-v1-regressions.md) → "Decisions on
  record". Revisit only on real reports of Karate 2 users with `karate-core` but no runnable
  JUnit artifact.
- **Marketplace / changelog wording is "early access"**, not "experimental". Matches the
  README's plugin-description block and CHANGELOG.
- **The Karate version override (Settings > Tools > Karate) is application-level, not
  per-project.** Awkward when working across projects on different majors; less pressing since
  detection is per-module. Revisit only on a real user report.
- **Karate 2 feature-file debugging is not planned.** Karate 1's debugger works because each
  step is a discrete Java method (`StepRuntime.findMethodsMatching`) that JDI can bind to.
  Karate 2 runs steps through the karate-js interpreter on virtual threads — no per-step
  bytecode, and `findMethodsMatching` was removed. Karate Labs' own DAP backend
  (`io.karatelabs.debug.Main`) needs a jar (`io/karatelabs/karate-ide`) that isn't on Maven
  Central, so the plugin can never match the v1 UX for v2. A pause-only walking-skeleton design
  (commit [`0b99a31`](../../commit/0b99a31), `docs/karate2-pause-debugging.md` on that commit)
  exists as archive; the delivered scope — pause + resume only — is too thin to ship without
  users expecting it to grow. See [`site/status.md`](../site/status.md#debugging) and the
  linked GitHub issue for the full reasoning. A v2 Debug run prints a one-line console notice
  at startup so users don't wait for a breakpoint that will never fire; Java breakpoints in
  step-definition code still work because the JVM is a plain Java debug target. Revisit only
  if the `karate-ide` jar reaches a public Maven repo, or if a v2 API surfaces that gives us
  the v1 UX under the virtual-thread runtime.

## Known cosmetic issue, deliberately unfixed

- The v1 converter labels anonymous scenarios with a generated id (`270885978##`) where v2
  shows `called.feature:4`. Pre-existing.
