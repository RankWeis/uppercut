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
- **Karate 2 feature-file debugging: decision reversed, see
  [`DEBUGGER.md`](DEBUGGER.md).** It was ruled out because Karate 1's debugger binds JDI to a
  discrete Java method per step (`StepRuntime.findMethodsMatching`), v2 has no such method, and
  Karate Labs' DAP backend (`io/karatelabs/karate-ide`) is not on Maven Central — with the caveat
  "revisit if a v2 API surfaces that gives us the v1 UX". It did: `Runner.Builder.debugSupport(
  RunInterceptor, DebugPointFactory)` is public in karate-core 2.x and its `karate-js` types are on
  Maven Central. `DEBUGGER.md` designs one debugger over that hook for both majors, with v1 rebased
  off JDI onto it. Until it ships, the shipped behaviour stands: a v2 Debug run prints a console
  notice that feature-file breakpoints will not fire, and Java breakpoints in step-definition code
  still work because the JVM is a plain Java debug target.

## Known cosmetic issue, deliberately unfixed

- The v1 converter labels anonymous scenarios with a generated id (`270885978##`) where v2
  shows `called.feature:4`. Pre-existing.
