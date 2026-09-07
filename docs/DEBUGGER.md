# Debugging — one debugger for both Karate versions

Status: **phase 1 built, phases 2-5 designed.** The debug agent, its wire protocol and the Karate 2
adapter live in the `KarateTestRunner` subproject with unit tests and an end-to-end harness; nothing
in the IDE talks to them yet. Phase 0's API findings are below. Supersedes the "Karate 2 feature-file debugging is not planned"
decision in [`KARATE2-HANDOFF.md`](KARATE2-HANDOFF.md), which said to revisit "if a v2 API
surfaces that gives us the v1 UX under the virtual-thread runtime". It has: v2's
`Runner.Builder.debugSupport(...)` is public and on Maven Central. This doc also absorbs and
widens the pause-only walking skeleton designed on commit
[`0b99a31`](../../commit/0b99a31) (`docs/karate2-pause-debugging.md` on that commit): same
mechanism, but v1 moves onto it too instead of keeping its JDI path forever.

API facts were verified with `javap` and the sources jars against the real
`karate-core` 1.5.1 / 2.1.1, `karate-js` 2.1.1, and IntelliJ 2026.2's
`plugins/java/lib/modules/intellij.java.debugger.impl.jar` — not from docs.

## Where we are

`plugin.xml:177-180` registers two platform hooks and that is the whole debugger:

- `KarateDebugAware` makes `.feature` files accept **Java** line breakpoints.
- `KaratePositionManagerFactory` → `KaratePositionManager`, which reflects
  `com.intuit.karate.core.StepRuntime.findMethodsMatching(text)` to find the Java method behind a
  Gherkin step and arms a JDI breakpoint on its bytecode.
- `UppercutClassLoader` gets at `StepRuntime` by loading **the user's library jars into the IDE's
  own JVM**, through a static singleton keyed to whichever project last debugged.

It works on Karate 1 only, and it can't be extended to Karate 2: v2 has no per-step Java method
(`StepExecutor.execute` switches on the keyword into private `executeDef`/`executeSet`/…) and
`findMethodsMatching` is gone. `KaratePositionManager` also carries known dead weight — a
lambda-ordinal branch that compares `java.lang.reflect.Method` against `com.sun.jdi.Method` and so
always yields `-1`, and a `locs.get(0)` with no emptiness guard.

## The mechanism

Both Karate versions expose a synchronous, vetoable, per-step callback **on the thread executing the
step**. Blocking in it *is* a breakpoint. No bytecode locations, no JDI, no user jars in the IDE JVM.

| | Karate 1.5.x | Karate 2.x |
|---|---|---|
| registration | `Runner.Builder.hook(RuntimeHook)` — already proxied in `KarateTestRunner.createRuntimeHook` | `Runner.Builder.debugSupport(RunInterceptor<T>, DebugPointFactory<T>)`, public |
| suspend point | `RuntimeHook.beforeStep(Step, ScenarioRuntime)` → `boolean` | `RunInterceptor.beforeExecute(point)` → `PROCEED \| SKIP \| WAIT`, then one call to `waitForResume()` — block inside it |
| skip a step | return `false` | return `SKIP` |
| variables | `runtime.engine.vars` (`Map<String,Variable>`) | `runtime.getAllVariables()` |
| evaluate | `engine.evalKarateExpression(String)` | `runtime.eval(String)` |
| set variable | `engine.setVariable(String, Object)` | `runtime.setVariable(String, Object)` |
| line | `Step.getLine()` | `Step.getLine()` / `getEndLine()` |

v2's point kinds are `GHERKIN_STEP`, `JS_STATEMENT`, `JS_EXPRESSION`. Two call sites matter:

- `StepExecutor.execute` → `create(GHERKIN_STEP, step.getLine(), sourcePath, step, null)` — **no
  execution context**. Pair it with the `ScenarioRuntime` from the `STEP_ENTER` event that fires
  immediately before it on the same thread (`KarateV2TestRunner:84-95` already receives that) via a
  `ThreadLocal`.
- `Interpreter.java:2038-2089` → `create(JS_STATEMENT, line, sourcePath, node, context)` — carries a
  full `io.karatelabs.js.Context` (`getScope`, `getParent`, `getDepth`, `getThisObject`,
  `getReturnValue`). This is what makes JS-level stepping possible later; it is not needed for steps.

Karate Labs' own DAP server is still not an option — its `io/karatelabs/karate-ide` backend is not
published. We are using the same interception API their server sits on, not the server.

## Target architecture

One debug agent in the runner, two version adapters behind one interface, one `XDebugProcess` in the
IDE. The IDE side never learns which Karate version it is talking to.

```
IDE                                              test JVM (KarateTestRunner)
  KarateDebugProcess : XDebugProcess               DebugAgent (socket, breakpoint table, latches)
  XLineBreakpointType for .feature                   ├─ v1 adapter: RuntimeHook.beforeStep → block
  breakpoints  ──────────── socket ───────────────▶  └─ v2 adapter: RunInterceptor → WAIT/waitForResume
  paused {path, line, thread}  ◀───────────────────  variables/eval read off the ScenarioRuntime
  resume / skip / evaluate / setVariable  ────────▶
```

Parallel scenarios fall out naturally: one paused thread = one `XExecutionStack` in the suspend
context. Feature-call depth = frames within a stack.

### What gets deleted when v1 is rebased

`KaratePositionManager`, `KaratePositionManagerFactory`, `UppercutClassLoader`, `KarateDebugAware`
and both `plugin.xml` registrations — ~360 lines of JDI and reflection, and with them the
loading of user jars into the IDE process. That is the point of rebasing v1 rather than leaving two
debuggers: one mechanism, one set of bugs, one thing to explain on the status page.

### The one thing the rebase must not break

Today a v1 Debug run is a plain Java debug target, so **Java breakpoints in step-definition code
work**, and users rely on that (`site/status.md:60`). Our own `XDebugProcess` is a separate session
and does not provide them. So the launch keeps its JDWP wiring
(`KarateRunConfiguration:227-240`) and the Java debugger keeps attaching; the Karate session runs
alongside it. Two tabs in the Debug tool window is the honest first answer. Do not delete the JDI
path until a v1 run demonstrates parity on the manual checklist.

## Phase 0 results (2026-09-06)

Run with `testProjects/karate-versions/v2/src/test/java/sample/DebugProbe.java`, a compiled twin of
the reflective runner in the same spirit as `EventProbe`:
`../../gradlew -p testProjects/karate-versions :v2:debugProbe -PpauseSeconds=25`.

- **The pause is real.** A 25 s hold on `users.feature:9` produced `elapsed: 25.07s`, both scenarios
  passing, no timeout, warning or engine complaint. Same at 15 s with `parallel=2`.
- **`waitForResume()` is called exactly once, and only its `SKIP` result is examined.** Both call
  sites (`StepExecutor.execute` for `GHERKIN_STEP`, `Interpreter` line ~2052 for `JS_STATEMENT`) do
  `if (action == WAIT) { action = waitForResume(); if (action == SKIP) {...} }` — there is **no
  polling loop**. The agent must block inside that single call and return `PROCEED`/`SKIP` when the
  user resumes; returning `WAIT` again is a silent no-op that resumes the run. The archived design on
  `0b99a31` assumed a loop and is wrong on this point; a polling implementation looks like it works
  (the pause is logged) while never actually pausing.
- **Virtual threads park cleanly.** At `parallel=2` the pause landed on
  `VirtualThread[#34]/runnable@ForkJoinPool-1-worker-3`; the other scenario ran to completion while
  it was parked. So parallel debugging is mechanically possible — forcing `parallel(1)` stays the
  plan for UI reasons, not engine ones.
- **The `ScenarioRuntime` pairing works.** `GHERKIN_STEP` points carry `(step, null)`, but the
  `ScenarioRuntime` stashed by the `STEP_ENTER` listener in a `ThreadLocal` was present on the paused
  thread every time, on both platform and virtual threads. At the pause,
  `getAllVariables()` returned `[fn, configLoaded, id, env, num]`, `getCurrentStep()` the right step,
  and `eval("id")` the live UUID. Variables and evaluate are therefore reachable from day one.
- **JS points arrive too**, carrying an `io.karatelabs.js.CoreContext`: 20 `JS_STATEMENT` points
  against 9 `GHERKIN_STEP` points on that one feature. Expression-level stepping is real, and also a
  reason the agent must filter by point kind or it will pause far more often than the user expects.
- **The point's source path is the classpath copy** (`build/resources/test/sample/users.feature`),
  not the editor's file. Feed it through the same source-root resolver the test tree uses for
  `locationHint` (the #321 fix) before handing a position to the IDE.

## What exists (phase 1)

In `KarateTestRunner/src/main/java/com/rankweis/uppercut/testrunner/debug/`:

| | |
|---|---|
| `DebugProtocol` | the wire format, both directions, and the thread key shared with the event stream |
| `BreakpointTable` | the breakpoints, and the path matching that makes a source-path breakpoint match the path Karate reports |
| `DebugAgent` | connects to the IDE, parks the thread that hits a breakpoint, releases it on command |
| `KarateV2DebugAdapter` | the reflective `RunInterceptor`/`DebugPointFactory` proxies, and the `STEP_ENTER` → `ScenarioRuntime` pairing |

`KarateV2TestRunner` opens the channel when the IDE passes `--debug-port` and closes it in a `finally`
around the terminal `parallel(...)` call, so no thread can be left parked by a suite that ended early.

**The IDE listens and the agent connects**, the same way round as JDWP: the IDE picks a free port
before launching and nothing has to be scraped out of stdout.

**Everything fails open.** A refused connection, a silent IDE, a dropped socket, an unknown command,
a malformed line or an interrupt all end with the run proceeding. Losing breakpoints is a bad debug
session; a test JVM parked forever with nobody to release it is a bad test run, and it is the only
outcome worth going out of the way to prevent. `DebugAgentTest` pins each of those paths.

Covered by `./gradlew :KarateTestRunner:test` (19 tests; root `check` now depends on it) and, for the
half only a real run can prove, `./gradlew -p testProjects/karate-versions :v2:debugHarness` — a fake
IDE driving the real agent and adapter against a real suite: breakpoint on the source path, `PAUSED`
with the step text and scenario name, a 5 s hold, `RESUME`, suite passes.

Two things that run counter to intuition and are worth keeping in mind:

- **Karate reports whichever path the run resolved**, source-relative
  (`src/test/java/sample/users.feature`) when the run targets the file, the classpath copy
  (`build/resources/test/sample/users.feature`) when it targets a classpath entry. Neither is a
  prefix of the IDE's absolute path, which is why breakpoints match on shared trailing segments.
- **`Map.of` randomises iteration order per JVM.** Payloads are built in a `LinkedHashMap` so the
  wire output does not change shape between runs.

## Easy wins

Cheap, and each is worth shipping on its own:

1. **Pause and resume on a feature-line breakpoint, v2.** The runner side is a proxy and a latch;
   the API is designed for exactly this. Biggest single jump in what the plugin can claim.
2. **Variables view.** `getAllVariables()` / `engine.vars` are one call, and IntelliJ's
   `PositionManagerEx.createStackFrame(StackFrameDescriptorImpl)` (verified present in 2026.2) lets
   even a JDI-anchored prototype render a Karate frame — so this is reachable before the full
   `XDebugProcess` exists.
3. **Evaluate expression.** `runtime.eval` / `evalKarateExpression` already do the work; it is
   wiring an `XDebuggerEvaluator` to a channel that exists.
4. **Skip this step.** `SKIP` / `beforeStep → false` are free in both versions. Nothing else in the
   Karate tooling world offers it.
5. **Break on step failure.** No breakpoint needed — pause in the *exit* callback when the result
   failed. Arguably the feature Karate users would want most, and it is a conditional on an event we
   already receive.
6. **Conditional breakpoints in Karate expressions.** Once eval exists, a condition is eval + a
   truthiness check on the runner side.

## Where the time actually goes

- **The `XDebugProcess` and breakpoint-type boilerplate.** Breakpoint type, handler, suspend context,
  execution stacks, frames, value trees, the editor gutter behaviour. Unavoidable, well-trodden,
  and the bulk of the IDE half.
- **Stepping.** The API gives `PROCEED`/`SKIP`/`WAIT` and nothing else, so step over/into/out have to
  be synthesised as "pause at the next point at depth ≤ / = / > the current one", using feature-call
  depth (and `Context.getDepth()` for JS points). This is the fiddliest correctness work in the
  project.
- **Session lifecycle.** Abrupt process death, detach-while-paused, stop-while-paused, a socket that
  dies with threads still parked. Every one of these must end with the JVM resumed or killed, never
  parked forever.
- **Parallelism.** `parallel(1)` under Debug removes the whole problem and matches how JDWP
  `suspend=y` already behaves; supporting N suspended stacks is architecturally fine but a UI and
  protocol tax. Start forced to 1.
- **Testing.** The agent half is unit-testable headlessly against a real Karate run and should be.
  The IDE half needs `integrationTest` (IDE Starter/Driver), which is the expensive suite, plus
  manual-checklist coverage like v1 has today.
- **v1 parity before deletion.** The rebase is only a win if v1 users notice nothing; that is
  verification work, not code.

## Small plan to get started

**Phase 0 — spike. Done**, see results above. `DebugProbe` stays in the v2 fixture as the
reproduction and as the harness phase 1 grows into.

**Phase 1 — the agent, no IDE. Done**, see "What exists" above.

**Phase 2 — walking skeleton in the IDE.** `XLineBreakpointType` for `.feature` + minimal
`XDebugProcess`: breakpoint set sent at launch, paused line highlighted, Resume and Stop. Forced
`parallel(1)`. This is the first shippable thing.

**Phase 3 — the easy wins.** Variables, evaluate, skip step, break on failure, conditions — in that
order, each behind its own changelog entry.

**Phase 4 — v1 adapter.** Same agent, `RuntimeHook.beforeStep`. Run the manual checklist against v1
until parity, then delete the JDI path and its `plugin.xml` registrations.

**Phase 5 — stepping**, if the appetite is still there.

Docs move with phases 2 and 4, not at the end: `site/status.md`'s debugging table,
`site/troubleshooting.md:96`, the settings text for the debug port, and the v2 console notice in
`KarateRunConfiguration:263-270` that tells users feature breakpoints will not fire.

## Decisions still open

- ~~Two Debug tabs, or one?~~ **Decided: two.** Keeping JDWP for Java step-def breakpoints means a
  second session, and that is the shipping answer, not a placeholder. It costs nothing to revisit:
  the JDWP launch exists today and stays either way, and a later single-tab experiment would replace
  IDE-side session plumbing only — no agent, protocol or adapter work is thrown away.
- **Breakpoints mid-run.** The launch-time snapshot is much simpler. Adding and removing while
  paused needs the IDE→runner direction of the protocol, which Phase 1 should leave room for.
- **Protocol.** Own JSON lines, not DAP. DAP costs several times the wire work and buys other IDEs,
  which is not our problem; there is also no public IntelliJ DAP client to rely on.
- **JS-level stepping.** `JS_STATEMENT`/`JS_EXPRESSION` make it possible. Out of scope until steps
  work end to end.
