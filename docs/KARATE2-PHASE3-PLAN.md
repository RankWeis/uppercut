# Karate 2 phase-3 execution plan

Handoff for a session with the **full build/test toolchain** (local machine). It was authored in a
sandbox that could not run Gradle or the IDE, so every step here ends in a verification command the
author could not run — **run them.** All API facts were verified with `javap` against
`karate-core-2.1.1.jar` / `karate-js-2.1.1.jar` from Maven Central.

## Context transfer — read these first

This repo *is* the handoff; the chat that produced it does not travel with you. Before starting, read:

- `docs/karate2-pause-debugging.md` — full design for Task 4 (pause-only debugging).
- `docs/KARATE2.md` — architecture, verified v2 API surface, phase table.
- `docs/KARATE2-HANDOFF.md` — current state, gotchas already paid for.
- `.claude/skills/karate-version-parity/` — how to diff the plugin's syntax knowledge against the jar.
- `.claude/skills/inspect-dependency-apis/` — the `javap` recipes used below (docs for intent, jars for truth).

Work on `claude/karate2-spike` (PR #334) or feature branches that PR into it — this is Karate 2
feature work, not the (merged) integration-tests fix.

**Verification loop you have and the author didn't:** `./gradlew test` (runs `UppercutLexerTest` +
the conformance suite), `./gradlew runIde` (eyeball highlighting/behavior), `./gradlew integrationTest`
(the IDE-driver suite). Use them after every task; nothing below is verified.

## Priority order (per the user)

1. **Karate 2 new JS syntaxes** — the priority. Detailed below.
2. `karate-base.js` / `karate-boot.js` recognition.
3. README / marketplace mention of Karate 2.
4. Pause-only debug prototype (see the separate design note).

---

## Task 1 — Karate 2 new JS syntaxes (embedded lexer/parser)

### Why
The plugin vendors a copy of the karate-js engine at `src/main/java/io/karatelabs/js/` and uses it to
lex/parse JS **only on the fallback path when the IntelliJ JavaScript plugin is absent** (see
`KarateJsNoPluginExtension.java`). That copy predates several JS features karate-js 2.1.1 supports, so
modern JS in `.feature` files mis-highlights on that path.

### Verified gap (do not re-derive)
`io.karatelabs.js.Token` (vendored) = **95** token constants. karate-js 2.1.1 reorganized tokens to
`io.karatelabs.parser.TokenType` = **118**. The 23-token delta is:

**The 9 JS-syntax tokens that matter — add these:**

| Token | Syntax | Notes |
|---|---|---|
| `QUES_DOT` | `?.` optional chaining | New operator; also `?.[` and `?.(` forms. Lexer + member/call grammar. |
| `QUES_QUES_EQ` | `??=` nullish assignment | New assignment op. Vendored already has `QUES` and `QUES_QUES`. |
| `CLASS` | `class` declaration | Keyword + a class-body grammar rule. Largest single addition. |
| `EXTENDS` | `extends` | Keyword; only meaningful inside a class heritage clause. |
| `SUPER` | `super` | Keyword; `super.x` / `super(...)` expression. |
| `THIS` | `this` | Keyword; primary expression. |
| `CONTINUE` | `continue` | Keyword; statement, parallels existing `BREAK`. |
| `VOID` | `void` | Keyword; unary operator, parallels existing `TYPEOF`/`DELETE`. |
| `BIGINT` | `123n` literal | Number-like literal; lexer rule beside `NUMBER`. |

(`EOF` is a structural token — add if the ported lexer expects it. The other 13 new constants are all
`G_*` — karate-js 2.1.1's *own Gherkin* tokens: `G_FEATURE`, `G_SCENARIO`, `G_TABLE_CELL`, etc. **The
plugin lexes Gherkin itself** — see the `karate/lexer/` package — so the embedded engine's Gherkin
tokens are irrelevant here. Do **not** pull them in.)

### Approach — surgical, not wholesale
**Add the 9 tokens to the existing `io.karatelabs.js` structure.** Do **not** wholesale re-vendor from
2.1.1: it moved tokens to `io.karatelabs.parser.TokenType`, split into `io.karatelabs.common` /
`io.karatelabs.parser` packages, and would collide with the plugin's own additions (e.g.
`KarateJsNoPluginExtension`). You only need the fallback lexer/parser to *recognize* the new syntax so
it highlights and doesn't red-underline valid code — not the whole engine reorg.

Get the reference behavior from karate-js 2.1.1 **sources** (try the Maven `:sources` classifier, else
GitHub `karatelabs/karate-js` at the 2.1.1 tag) and port the per-token lexer/parser handling.

### Files to touch
- `src/main/java/io/karatelabs/js/Token.java` — add the 9 enum constants (keywords vs punctuation;
  mirror how `BREAK`/`TYPEOF`/`QUES_QUES` are declared).
- `src/main/java/io/karatelabs/js/Lexer.java` — tokenize `?.`, `??=`, the new keywords, and the `n`
  BigInt suffix. Watch ordering: `?.` must not be mis-split into `QUES`+`DOT`, and `??=` must win over
  `QUES_QUES`; longest-match ordering matters (see how `GT_GT_GT_EQ` is handled).
- `src/main/java/io/karatelabs/js/Terms.java` + `Parser.java` — grammar/AST rules: optional-chaining
  member/call access, `class`/`extends` declaration + body, `this`/`super` primaries, `continue`
  statement, `void` unary, BigInt literal.
- `src/main/java/io/karatelabs/js/Interpreter.java` — only if you want the fallback engine to *evaluate*
  them; for highlighting-only, parsing without eval is enough. Decide based on whether the fallback
  engine is used for anything beyond syntax highlighting (grep usages of `Engine`/`Interpreter` in
  `src/main`).
- Highlighter: the JS syntax-highlighter that maps these tokens to colors — likely under
  `src/main/kotlin/com/rankweis/uppercut/karate/highlight/`. New keywords must map to the keyword color;
  new operators to the operator color.
- Preserve plugin-only files in the package (e.g. `KarateJsNoPluginExtension.java`) — do not overwrite.

### Acceptance
- Add fixtures under `src/test/testData/` exercising each: `a?.b`, `a ??= b`, `class A extends B { constructor(){ super() } m(){ return this.x } }`, `for(...){ continue }`, `void 0`, `10n`.
- `./gradlew test` green — especially `UppercutLexerTest` and the conformance suite (parity skill:
  "Add any new action word to a fixture so the lexer test actually sees it").
- `./gradlew runIde`, open a `.feature` with these in a JS block **with the IntelliJ JavaScript plugin
  disabled** (the fallback path), and confirm correct highlighting and no false error annotations.

---

## Task 2 — `karate-base.js` / `karate-boot.js` recognition

Karate 2 introduces `karate-base.js` and `karate-boot.js` as bootstrap config files alongside
`karate-config.js`. Make the plugin treat them like `karate-config.js`.

- Grep for `karate-config.js` across `src/main` — extend every special-case (config-file detection,
  navigation, run-classpath, any completion/inspection that keys off the name) to also match
  `karate-base.js` and `karate-boot.js`. Prefer a single shared constant/set over scattered literals.
- Confirm against karate-js/karate 2 docs what each does (base = shared config, boot = startup) so the
  handling is right, not just name-matched.
- Acceptance: `./gradlew runIde`, add the files to a v2 project, confirm they're recognized (navigation
  / no "unknown config" behavior). Add a unit test if there's an existing config-detection test.

---

## Task 3 — README / marketplace mention of Karate 2

- `README.md`: add a short "Karate 2 (early access)" section — per-module detection, run/report works,
  debugging limitation. Mirror the CHANGELOG `[Unreleased]` early-access wording (already written).
- Plugin marketplace description: the `<description>` in `src/main/resources/META-INF/plugin.xml` (or
  the `patchPluginXml { pluginDescription }` block in `build.gradle.kts` if that's the source of truth —
  check which wins). Keep it honest about "early access".
- Acceptance: `./gradlew buildPlugin` / `verifyPlugin` still pass; description renders (no broken HTML).

---

## Task 4 — Pause-only debug prototype

Follow `docs/karate2-pause-debugging.md` in full. Summary: wire
`Runner.builder().debugSupport(RunInterceptor, DebugPointFactory)` in `KarateV2TestRunner` (reflection/
proxy, as with the existing `RunListener`); pause a `GHERKIN_STEP` by returning `WAIT` from
`beforeExecute` and blocking in `waitForResume`; a thin socket carries breakpoints in and "paused"/
"resume" out; a minimal `XDebugProcess` + `.feature` `XLineBreakpointType` + a v2 debug `ProgramRunner`
show the paused line and a Resume button. **Force `parallel(1)` under debug.** No variables/stepping/eval.

Prototype the runner↔IDE socket first (the one real unknown), then the XDebugger UI. Verify with
`./gradlew runIde`: set a breakpoint on a step, confirm the run pauses there and Resume continues. Keep
the v1 debugger (`debugging/KaratePositionManager`, `KarateDebugAware`) untouched — it's the v1/JDI path.

---

## Suggested sequencing

Task 1 is the priority and is self-contained — do it first, get `./gradlew test` green, PR it. Tasks 2
and 3 are small and low-risk. Task 4 is the big one; treat its walking skeleton as its own PR.
