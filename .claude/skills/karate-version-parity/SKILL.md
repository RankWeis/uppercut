---
name: karate-version-parity
description: Check that the plugin's syntax knowledge (step actions, match markers, JS tokens) matches what the karate version on the classpath actually supports. Run whenever karateVersion changes in gradle.properties, when adding Karate-version support, or when a user reports a keyword not highlighting.
---

# Karate syntax parity check

The plugin hardcodes its knowledge of Karate's language in three places. Karate defines the truth
in its jars. When `karateVersion` moves, diff them - do not trust release notes.

## Where the plugin's knowledge lives

| Surface | Location |
|---|---|
| Step action keywords (highlight/completion) | `src/main/resources/i18n.json` (the `"en"` section; other languages only localize Gherkin structure words) **and** `src/main/java/.../psi/PlainKarateKeywordProvider.java` - both must be updated together |
| Gherkin structure words | same files (`feature`, `scenario`, ...) - Cucumber-standard, effectively frozen |
| Embedded JS lexer/parser | `src/main/java/io/karatelabs/js/` (generated from `js.jflex`; used when the IntelliJ JS plugin is absent) |

## Where Karate's truth lives

| Version | Step actions | Match markers | JS tokens |
|---|---|---|---|
| 1.x | `com.intuit.karate.ScenarioActions` (method names ≈ actions) | `com.intuit.karate.MatchOperation` | n/a (GraalVM JS) |
| 2.x | `io.karatelabs.core.StepExecutor` (string constants) | `io.karatelabs.match.Validators` | `io.karatelabs.parser.TokenType` in karate-js |

Extraction commands are in `.claude/skills/inspect-dependency-apis/` - the string-constants and
version-diff recipes. Example for 2.x actions:

```bash
J=$(ls ~/.gradle/caches/modules-2/files-2.1/io.karatelabs/karate-core/<ver>/*/karate-core-<ver>.jar)
T=$(mktemp -d); unzip -q "$J" -d "$T" "io/karatelabs/core/StepExecutor.class"
javap -c "$T/io/karatelabs/core/StepExecutor.class" | grep -oE 'String [a-z]+' | sort -u
```

Compare against the plugin:

```bash
sed -n '/"en": {/,/^\t},/p' src/main/resources/i18n.json | grep -o '"[a-z]*": \[' | tr -d '":[' | sort
```

## Findings as of karate 2.1.1 (2026-07, don't re-derive)

- v2 step actions are a strict **subset** of v1's - nothing new; `robot`, `listen`,
  `compareImage` removed (plugin keeps them for v1 users).
- Match markers identical between 1.5.1 and 2.1.1.
- `@lock` tag: lexed generically, no grammar work.
- The plugin was missing `form`, `multipart`, `soap` since the v1 days - fixed on the karate2
  branch. That gap survived years because nothing diffed the lists; hence this skill.
- **Known open gap**: karate-js 2.1.1 supports `?.`, `??=`, `class`/`extends`/`super`/`this`,
  `continue`, `void`, and BigInt literals; the embedded lexer (95 tokens vs the engine's 118)
  predates them. Modern JS in feature files mis-highlights on the no-JS-plugin fallback path.
  Fix = refresh `src/main/java/io/karatelabs/js/` from current karate-js (KARATE2.md phase 3).

## After changing keyword lists

`./gradlew test` covers the lexer/parser; `UppercutLexerTest` and the conformance suite catch
regressions. Add any new action word to a fixture in `src/test/testData/` so the lexer test
actually sees it.
