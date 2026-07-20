# Karate 2 pause-only debugging — design note

Status: **design, not started.** Scope deliberately limited to *pausing* — a breakpoint on a
Gherkin step suspends the run, the editor shows where it stopped, and Resume/Stop continue it.
**No variable inspection, no stepping, no expression evaluation.** Those are noted at the end as
later extensions, because the chosen API leaves the door open, but they are out of scope here.

API facts below were verified with `javap` against the real `karate-core-2.1.1.jar` and
`karate-js-2.1.1.jar` from Maven Central, not from docs.

## Goal

- A line breakpoint on a step in a `.feature` file run under the **v2** runner pauses the run before
  that step executes.
- The IDE shows the paused state and highlights the current line.
- Resume continues; Stop ends the run.
- v1 debugging is untouched (it keeps its own, entirely separate path — see below).

## Why v1's debugger cannot be reused

v1 debugging does **not** use Karate's DAP server. It uses plain JVM **JDWP** plus a custom
`KaratePositionManager`
(`src/main/java/com/rankweis/uppercut/karate/debugging/KaratePositionManager.java`) that reflects
`com.intuit.karate.core.StepRuntime.findMethodsMatching(...)` to map a Gherkin step to the **Java
bytecode location** of the step method, then arms a JDI breakpoint there. The registrations are two
platform hooks in `plugin.xml` (`debugger.javaDebugAware` line 174, `debugger.positionManagerFactory`
line 176); there is no custom breakpoint type, program runner, or DAP client.

That works only because v1 executes each step as a discrete Java method in-process. v2 executes steps
through the **karate-js interpreter** (`io.karatelabs.js.Interpreter`, virtual threads) — there are no
per-step Java methods and no stable bytecode locations to bind JDI to. `StepRuntime.findMethodsMatching`
does not exist in v2. **The v1 debugger is a dead end for v2 and must not be touched or extended for it.**

## Routes considered

| Route | Verdict |
|---|---|
| JDWP + a v2 `PositionManager` (v1's approach) | ❌ No step→bytecode mapping in v2's karate-js engine. |
| Karate 2's built-in debug server (`-Dkarate.debug.port` → `io.karatelabs.debug.Main`, DAP) | ❌ `Runner$Builder` auto-delegates to it, but that backend ships in a **`karate-ide` jar that is not on Maven Central** (`io/karatelabs/karate-ide` → HTTP 404) — Karate Labs' commercial tooling. Unusable in a free plugin, and would also require a DAP client. |
| **`debugSupport(RunInterceptor, DebugPointFactory)`** (public `karate-js` API) | ✅ **Chosen.** In-process interceptor, clean, purpose-built. |

## The chosen API (verified)

`io.karatelabs.core.Runner$Builder`:

```
public <T> Runner$Builder debugSupport(io.karatelabs.js.RunInterceptor<T>,
                                       io.karatelabs.js.DebugPointFactory<T>);
```

`io.karatelabs.js.RunInterceptor<T>` (called from `io.karatelabs.js.Interpreter`/`Engine`):

```
RunInterceptor.Action beforeExecute(T point);              // PROCEED | SKIP | WAIT
default void afterExecute(T point, Object result, Throwable error);
default RunInterceptor.Action waitForResume();             // PROCEED | SKIP | WAIT
enum Action { PROCEED, SKIP, WAIT }
```

`io.karatelabs.js.DebugPointFactory<T>`:

```
int JS_STATEMENT, JS_EXPRESSION, GHERKIN_STEP;            // point kinds
T create(int kind, int line, String source, Object a, Object b);
```

**Pause/resume mechanics (from decompiling `Interpreter`):** for each point the engine calls
`factory.create(kind, line, source, …)`, then `interceptor.beforeExecute(point)`. If it returns
`WAIT`, the engine loops on `interceptor.waitForResume()` until that returns non-`WAIT`. So:

- **Pause** = return `WAIT` from `beforeExecute`, then block inside `waitForResume` until the user
  resumes, at which point return `PROCEED`.
- For pause-only we care about `kind == GHERKIN_STEP`; JS-level points can be ignored.
- `create` can return a trivial record of just `(kind, line, source)` — **no execution context is
  needed for pausing.**

## Architecture

Two processes, one thin socket between them.

```
IDE (KarateV2DebugProcess : XDebugProcess)                 test JVM (KarateV2TestRunner)
  XLineBreakpointType<> for .feature                         RunInterceptor + DebugPointFactory
  breakpoint set  ──────────────── socket ───────────────▶   beforeExecute: is source:line a bp?
  "paused at path:line"  ◀───────────────────────────────    yes → WAIT, block in waitForResume
  Resume  ────────────────────────────────────────────────▶  waitForResume returns PROCEED
  (editor highlights path:line; Debug toolwindow Resume/Stop)
```

### Runner side (`KarateTestRunner/.../KarateV2TestRunner.java`) — small
- Add `.debugSupport(interceptor, factory)` to the reflective builder chain in `doTest`, built with
  the same dynamic-proxy style already used there for `RunListener` (no compile-time Karate dep).
- `DebugPointFactory.create` → a tiny `{kind, line, source}` holder.
- `RunInterceptor.beforeExecute`: for a `GHERKIN_STEP`, look up `source:line` in the breakpoint set
  received from the IDE; if present, send `paused` over the socket and return `WAIT`. `waitForResume`
  blocks on a latch/queue until the IDE sends `resume`, then returns `PROCEED`.
- Only wired when a new `--debug` flag (and a debug port/socket) is passed; otherwise the builder is
  unchanged, so normal runs are unaffected.

### IDE side (new; the bulk, but minimal)
- `XLineBreakpointType` for Karate `.feature` files (v2 wants XDebugger-native breakpoints, not the
  Java line breakpoints v1 rides on via `JavaDebugAware`).
- `KarateV2DebugProcess extends XDebugProcess` + an `XBreakpointHandler`. The suspend context has a
  **single stack frame that is just the source position** (`XStackFrame` with the `.feature` line, no
  children) — that frame is what highlights the paused line. No variable tree, no evaluator.
- A `ProgramRunner`/executor for the v2 **Debug** action that launches `KarateTestRunner` with the
  `--debug` flag + a socket, instead of the JDWP agent v1 uses. Route to it only when the run's module
  is v2 (reuse the per-module detection in `KarateRunConfiguration`); v1 keeps its existing path in
  `KarateRunConfiguration.getState`/`startProcess`
  (`src/main/java/com/rankweis/uppercut/karate/run/KarateRunConfiguration.java`).
- New `plugin.xml` registrations for the breakpoint type and the program runner. The existing
  `debugger.javaDebugAware` / `debugger.positionManagerFactory` entries stay v1-only.

### Protocol (minimal, one socket)
- IDE → runner: breakpoint set — `{path, line}` pairs (sent at start; optionally on change).
- runner → IDE: `paused {path, line}`.
- IDE → runner: `resume`.
- Plus session start / process-exit, which the launch already gives.

## The one decision that keeps it small

**Force `parallel(1)` when launched under Debug.** v2 runs scenarios on virtual threads and the
interceptor is shared; concurrent pauses are the hardest part of a full debugger. Single-threaded
under debug removes that entirely and matches how v1 debugging already behaves (JDWP `suspend=y`
freezes the whole VM). Bake this into the v2 debug launch.

## Source-line resolution — already solved

Mapping a paused `path:line` back to the editor is the same source-root problem the test tree already
handles for `locationHint` (the #321 fix: resolve to the source `.feature`, not the `build/resources`
copy). Reuse that resolver; do not reinvent it, and do not navigate into the build-output copy.

## What this is not, and what it enables later

Out of scope now, but the chosen API supports them, so the walking skeleton should not preclude them:

- **Variables.** `io.karatelabs.js.Context` (reachable from the point the engine passes to `create`)
  exposes `getScope()` (variables), `getParent()`+`getDepth()` (call stack), `getNode()`,
  `getThisObject()`, return/error values. A later version can populate `XStackFrame` children from
  `getScope()` and a multi-frame stack from the `getParent()` chain.
- **Stepping.** Only `PROCEED`/`SKIP`/`WAIT` exist, so step over/into/out must be synthesised IDE-side
  by "pause at the next point at depth ≤/=/> current" using `Context.getDepth()`. Fiddly; deferred.
- **Expression evaluation.** karate-js can eval in a `Context`, so an evaluator is feasible later.

## Effort & risks

- **Rough size: a few days to ~a week** for the walking skeleton (breakpoint pauses, line highlights,
  Resume/Stop). Most of that is the minimal `XDebugProcess`/breakpoint-type boilerplate and the socket
  protocol; the runner side is small.
- **No automated coverage is possible** with the current harness — debuggers can't be driven by it, so
  this needs the manual checklist (like v1 debugging), and ideally a later IDE-driver test that sets a
  breakpoint and asserts a pause.
- **Main unknown:** the exact `--debug` launch wiring and keeping the socket lifecycle clean on abrupt
  process death. Prototype the runner↔IDE channel first (it's the one true unknown) before building
  out the XDebugger UI.

## Concrete first hook points

- `KarateTestRunner/src/main/java/com/rankweis/uppercut/testrunner/KarateV2TestRunner.java` — add
  `debugSupport` to the reflective builder in `doTest`; implement the interceptor + factory + socket.
- `src/main/java/com/rankweis/uppercut/karate/run/KarateRunConfiguration.java` — a v2 debug launch that
  passes `--debug`/socket instead of the JDWP agent, gated on v2 module detection.
- `src/main/resources/META-INF/plugin.xml` — register the new `XLineBreakpointType` and program runner
  (leave the v1 `debugger.*` entries alone).
- Do **not** modify `debugging/KaratePositionManager.java`, `KarateDebugAware.java`, or
  `UppercutClassLoader.java` — those are the v1/JDI path.
