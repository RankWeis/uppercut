# Debugging — one debugger for both Karate versions

Status: **shipping in 3.1.0: one debugger, both majors, one tab.** A JVM
debugger in a second tab is opt-in per run configuration - see "Phase 6" below. A breakpoint on a `.feature` step pauses the
run, highlights the line, shows the scenario's variables and evaluates Karate expressions in it - on
Karate 1 through `RuntimeHook.beforeStep`, on Karate 2 through `Runner.debugSupport`. The JDI path
and JDWP are gone; so are Java breakpoints during a Karate run. Deliberately not built: breakpoint
conditions, skip-step, break-on-step-failure and real stepping. Phase 0's API findings are below. Supersedes the "Karate 2 feature-file debugging is not planned"
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

In `src/main/java/com/rankweis/uppercut/karate/debugging/agent/` (phase 2):

| | |
|---|---|
| `KarateDebugChannel` | the IDE end: listens on a loopback port, speaks the same `DebugProtocol`, and is free of debugger UI so it can be tested against a plain socket |
| `KarateBreakpointType` | `.feature` line breakpoints, offered only on a Karate 2 classpath |
| `KarateDebugProcess` | the session: breakpoint handler, suspend context, one stack frame at the paused line, resume |
| `KarateDebugEditorsProvider` | what expression fields are edited as |

Variables and evaluation are served from the parked thread through `SuspendedFrame`, which the v2
adapter implements over `ScenarioRuntime.getAllVariables()` and `eval` - so an expression typed into
the IDE behaves exactly like one written in the feature, errors included
(`ReferenceError: nosuchvariable is not defined` comes straight from karate-js). `DebugValues` renders
one level at a time and the tree asks for children by path: a Karate scenario routinely holds a whole
response body, and sending it eagerly would put megabytes on the wire for a panel nobody opened.

`KarateRunConfiguration` opens the channel in `createJavaParameters` (so the port can be passed to the
JVM) and starts the session in `startProcess` once there is a process to attach it to. Under Debug on
Karate 2 it also forces `--parallelism 1`.

**The session cannot exist before the process**, so the agent can connect - and in principle pause -
before anything is listening. The channel buffers those events and replays them when the session
attaches; a dropped pause would be a parked test JVM with no UI to resume it.

**The empty breakpoint set matters.** The channel sends `CLEAR`/`BREAKPOINTS_END` the moment the agent
connects even when there are no breakpoints, because `BREAKPOINTS_END` is also the handshake: without
it every debug run would stall for the agent's full 15-second timeout before starting.

**The IDE listens and the agent connects**, the same way round as JDWP: the IDE picks a free port
before launching and nothing has to be scraped out of stdout.

**Everything fails open.** A refused connection, a silent IDE, a dropped socket, an unknown command,
a malformed line or an interrupt all end with the run proceeding. Losing breakpoints is a bad debug
session; a test JVM parked forever with nobody to release it is a bad test run, and it is the only
outcome worth going out of the way to prevent. `DebugAgentTest` pins each of those paths.

Covered by `./gradlew :KarateTestRunner:test` (19 tests; root `check` now depends on it),
`KarateDebugChannelTest` on the IDE side, and, for the half only a real run can prove,
`./gradlew -p testProjects/karate-versions :v2:debugHarness` — a fake IDE driving the real agent and
adapter against a real suite: breakpoint on the source path, `PAUSED` with the step text and scenario
name, a 5 s hold, `RESUME`, suite passes.

**What automated tests now cover, and what they do not.** `Karate2UITest.debuggerPausesOnAFailedStep`
debugs the fixture's broken feature in a real IDE and asserts the session suspends on the failing
step, steps, and resumes - it needs no breakpoint UI, which is why it was the first one written.
`theJvmDebuggerIsOptInAndRunsBesideTheKarateOne` (phase 6) does set breakpoints, through the
`ToggleLineBreakpoint` action with the caret placed - the same path the gutter takes - so breakpoint
registration is covered on both a feature step and a Java line. The variables tree and the conditions
field are still only exercised by hand. That is the same gap
v1 debugging has always had, and it is why `docs/manual-test-checklist.md` has a debugger section -
walked end to end on 2026-09-06, which is what found the three phase-2 bugs: two breakpoint types
claiming one line, `XDebuggerEditorsProvider.createDocument` throwing `AbstractMethodError` before
the first pause, and no execution-line highlight until the Karate tab was selected by hand.

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

**Phase 2 — walking skeleton in the IDE. Done**, see "What exists" above. Not yet reflected on the
site: `site/status.md` and `site/troubleshooting.md` still say feature-file breakpoints do not pause
on v2, and should stay that way until phase 3 makes the session worth documenting - a debugger that
stops but cannot show a variable is not yet the thing those pages would be promising.

**Phase 3 — the easy wins. Done.** Variables, evaluate, pause on a failed step (v2 in the `STEP_EXIT`
listener, v1 in `afterStep`; both fire on the thread that ran the step, before it moves on), a
`Pause on Failed Step` toolbar toggle, breakpoint conditions (a fourth token on the `BREAKPOINT` line,
evaluated through the same frame that serves the variables view) and `Skip Step`.

Two things worth keeping in mind here. On Karate 1 a condition runs on **every** hit of its line
through `evalKarateExpression`, so without phase 4's failed-reason restore a false condition would
fail the scenario it was watching. And anything the session sends before the agent connects is
dropped: the breakpoint set and the pause-on-failure flag are both held and replayed on connect, and
both were shipped broken once for want of that.

**Phase 4 — v1 adapter. Done.** `KarateV1DebugAdapter` pauses in `RuntimeHook.beforeStep` and reads
`ScenarioEngine.vars`; `KarateDebugRunner` owns the Debug executor so no JVM debugger attaches;
`KaratePositionManager`, `KaratePositionManagerFactory`, `UppercutClassLoader`, `KarateDebugAware`
and the JDWP wiring are deleted. The v1 parity harness lives at `:v1:debugHarness`.

**One thing v1 needed that v2 did not:** `ScenarioEngine.evalKarateExpression` records a failure on
the engine as well as throwing it, so a mistyped expression in the Evaluate window failed the
scenario - the run reported "passed: 1 | failed: 1" for a typo made while looking around. The adapter
captures and restores the engine's failed reason around every evaluation. `KarateV1DebugAdapterTest`
pins it against a stand-in engine.

**Phase 5 — stepping. Partly done.** All three step buttons run to the next step, which is the only
place a Karate run can stop. What is left is telling step *over* from step *into*: a step that calls
another feature stops on the called feature's first step rather than after the call. Both majors
expose what is needed - v2's `callDepth`, v1's `ScenarioCall.caller` chain the runner already walks -
so it is a follow-up, not a rewrite.

Docs move with phases 2 and 4, not at the end: `site/status.md`'s debugging table,
`site/troubleshooting.md:96`, the settings text for the debug port, and the v2 console notice in
`KarateRunConfiguration:263-270` that tells users feature breakpoints will not fire.

## Phase 6 - an opt-in JVM debugger (2026-09-07)

Phase 4 deleted JDWP outright. The question this spike answers is whether it can come back as an
**opt-in second tab** - not for feature lines, which the agent owns on both majors now, but for the
one thing the agent cannot reach: Java a feature calls through `Java.type`, and karate-core itself.

Wiring JDWP back on is a dozen lines; it was never the hard part. The hard part was
`KaratePositionManager` mapping a Gherkin step to bytecode through
`StepRuntime.findMethodsMatching`, and `UppercutClassLoader` loading the user's jars into the IDE's
own JVM to do it. None of that is needed for Java breakpoints in Java files, and with feature lines
belonging to one breakpoint type there is no second claim on a line either. So the only real
question is what two debuggers do to each other inside one JVM.

`JdwpClashProbe` answers it, in both fixture modules: a parent process that is a stand-in for the
IDE with **both tabs open** - it listens on the Karate debug channel and attaches JDI to the child -
against a child that is a real Karate suite under the real agent, launched with JDWP the way an
opt-in Debug run would launch it.

```
./gradlew :KarateTestRunner:classes
../../gradlew -p testProjects/karate-versions :v1:jdwpClash
../../gradlew -p testProjects/karate-versions :v2:jdwpClash
../../gradlew -p testProjects/karate-versions :v2:jdwpClash -Pparallelism=2
```

`sample/javacall.feature` and `sample/Helper.java` are new in both modules and exist for this: a
feature that calls the user's own Java, which is the whole case an opt-in JVM debugger would serve.

**`Java.type` reaches user Java on both majors.** Karate 2's own JS engine handles it as Karate 1's
Graal does - `Helper.compute(20)` ran and returned 41 under both. The feature is not v1-only.

**The two debuggers coexist.** JDWP attached, the agent connected, breakpoints on both sides bound,
the suite passed. Launching `suspend=y` is safe: the child runs nothing until JDI resumes it, so the
agent's handshake window does not start until the Java side has attached. No ordering hazard.

**A Java breakpoint freezes the Karate tab, on both majors.** This is the finding. The default
suspend policy is all-threads, and one of those threads is `uppercut-debug-reader`. With the VM
suspended the channel answered nothing in 5 s, having answered in 2 ms a moment earlier. Stopped at
the real breakpoint in `Helper.compute` it was frozen again. Variables, evaluate and resume in the
Karate tab all stop working for as long as the Java tab holds the VM.

**Nothing is lost, though.** Commands sent during the suspension sit in the socket and are acted on
2 ms after the VM resumes - the `VARIABLES` reply and a `RESUME` issued mid-suspension both landed.
So the failure mode is a tab that appears dead and then catches up, not a dropped command or a
parked JVM. Recoverable, and the user's way out - resume the Java tab - is the obvious one.

**Karate 2's virtual threads are the sharp edge.** A Java breakpoint on a scenario running on a
virtual thread *does* hit (`virtual: true`, at `parallelism=2`). But `VirtualMachine.allThreads()`
never lists them - 0 of 4 at attach, and the paused scenario thread was not visible to JDI at all -
so the Java tab's thread list is empty of exactly the threads the run is using until one stops. And
JDI's id for the thread that did stop has nothing to do with the `vt-38` key the Karate tab shows,
so the two tabs cannot be lined up by thread. Forcing `parallel(1)` under Debug, which the run
configuration already does, puts the scenario back on `main` and makes the Java tab coherent - which
turns a UI preference into a requirement for this feature.

### What that means for the design

Worth doing, opt-in and off by default, with the freeze documented rather than engineered around:
it is the ordinary behaviour of a Java breakpoint, the Karate tab recovers by itself, and a user who
opted into a second debugger has some reason to expect a second debugger's semantics. Two things
follow from the spike:

- **Routed through a stock Remote JVM Debug session** (`RemoteConfigurationType` +
  `ProgramRunnerUtil.executeConfiguration`) rather than attaching one ourselves.
  `KarateDebugRunner.canRun` already claims the Debug executor ahead of `GenericDebuggerRunner`, so
  the alternative is `DebuggerManagerEx.attachVirtualMachine` and `RemoteConnectionBuilder` - the
  `com.intellij.debugger.impl` package that commit `9211463` just finished getting off. The stock
  session is public API, is genuinely a separate tab, and detaching it leaves the run alive.
- **`--parallelism 1` is load-bearing** once JDWP is on, for the virtual-thread reason above, not
  only for the "a suspended run is easier to follow" reason it was chosen for.

### What was built

`KarateJvmDebuggerAttach` runs a stock `RemoteConfigurationType` configuration against a JDWP port
`KarateRunConfiguration.openJvmDebugPort` opens for the launch, gated on the run configuration's
`attachJvmDebugger` flag. Three things are worth knowing about it:

- **`server=y,suspend=n`.** `suspend=y` would guarantee that nothing runs before the attach, at the
  price of a test JVM parked forever if the attach never happens - the one outcome this debugger goes
  out of its way to avoid. The guarantee comes from the Karate handshake instead:
  `KarateDebugChannel.holdForJvmDebugger` withholds `BREAKPOINTS_END`, which the agent waits for
  before its first step, until the remote session starts. So the JVM itself never waits.
- **The hold has its own backstop, and it is short.** Eight seconds, against the agent's fifteen.
  Holding past the agent's own timeout would start the suite with no breakpoint set at all - a JVM
  debugger that never arrives has to cost the user its own breakpoints, never Karate's. The process
  handler releases it early if the run dies first.
- **A port per launch, not a field.** The **Debug port** field is the Karate channel's, and pinning
  both through one setting would be a worse answer than a free port for the one that needs no pinning.

Still open: what a Stop in one tab should do to the other. Today they are independent - stopping the
Java tab detaches it and leaves the run going, which is the useful direction and is asserted by the
UI test; stopping the Karate tab kills the JVM under the Java tab, which is honest but abrupt.

## Decisions still open

- ~~Two Debug tabs, or one?~~ **Shipping: one, with a second one opt-in.** The question was asked
  twice and answered twice. Phase 4 settled the default - a Karate run attaches no JVM debugger, so
  there is one tab - and the phase 6 spike above settles the follow-up: a JVM debugger can come back
  as an opt-in second tab, off unless asked for, because its only real cost is a Karate tab that goes
  unresponsive while the Java tab holds the VM, and that recovers by itself. Built; see phase 6.
- **Breakpoints mid-run.** The launch-time snapshot is much simpler. Adding and removing while
  paused needs the IDE→runner direction of the protocol, which Phase 1 should leave room for.
- **Protocol.** Own JSON lines, not DAP - already built. Worth recording that 2026.2 does ship an
  `intellij.platform.dap.jar`, so the "no DAP client in the platform" half of the original argument is
  wrong; whether any of it is public API for plugins is unverified. The rest of the argument stands:
  DAP is several times the wire work and buys other IDEs, which is not our problem.
- **JS-level stepping.** `JS_STATEMENT`/`JS_EXPRESSION` make it possible. Out of scope until steps
  work end to end.
