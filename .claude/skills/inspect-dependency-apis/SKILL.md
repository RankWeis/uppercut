---
name: inspect-dependency-apis
description: Answer questions about what a dependency or the IntelliJ platform actually ships by interrogating the jars - Gradle cache, IDE distribution, javap, sources jars. Use before trusting any claim about a library's API, emitted output, keywords, or module layout, and whenever a driver/@Remote/platform API needs its real signature.
---

# Interrogate the artifact, not the docs

Every hard question in this repo gets answered faster from the jars than from documentation or
memory. Claims about what a dependency does that were "verified" only by reading docs have been
wrong here before (a review finding died to one grep of live output). Rules of thumb:

- A claim about an **API shape** → `javap` the class.
- A claim about **what something emits or accepts** → extract string constants (`javap -c`) or run
  the thing and read its output.
- A claim about **where a class lives** (classloader/module) → `product-info.json`.

## Where the artifacts are

| What | Where |
|---|---|
| Dependency jars + sources | `~/.gradle/caches/modules-2/files-2.1/<group>/<artifact>/<version>/<hash>/` |
| IDE distribution used by integration tests | `out/ide-tests/cache/builds/IU-<build>/` |
| Platform module registry | `out/ide-tests/cache/builds/IU-*/product-info.json` |
| Bundled-plugin jars (e.g. SM runner) | `out/ide-tests/cache/builds/IU-*/plugins/<plugin>/lib/` |
| The plugin as the IDE actually loads it | `out/ide-tests/tests/IU-*/<context>/plugins/uppercut/lib/` |

## Recipes

Find a class's owning jar inside a distribution:

```bash
for j in $(find <dir> -name "*.jar"); do unzip -l "$j" 2>/dev/null | grep -q "Some/Class.class" && echo "$j"; done
```

Public API of a class (no sources needed):

```bash
T=$(mktemp -d); unzip -q <jar> "path/To/Class.class" -d "$T" && javap -p "$T/path/To/Class.class"
```

String constants a class dispatches on (step keywords, event types, config keys):

```bash
javap -c "$T/path/To/Class.class" | grep -oE 'String [a-zA-Z_#]+' | sort -u
```

Read the actual source when a sources jar is cached (driver SDK, Starter - usually are):

```bash
J=$(find ~/.gradle/caches/modules-2/files-2.1 -name "<artifact>*<version>*sources.jar" | head -1)
T=$(mktemp -d); (cd "$T" && unzip -q "$J" "com/path/**") && grep -rn "thing" "$T"
```

Resolve a `@Remote` plugin/module id (never guess - a wrong id fails as "No such class ... in
plugin null"):

```bash
grep -o "intellij\.[a-zA-Z.]*" out/ide-tests/cache/builds/IU-*/product-info.json | sort -u | grep -i <hint>
```

Compare what two versions of a library support (keywords, tokens, methods):

```bash
javap <v1-class> | grep -oE "<pattern>" | sort > /tmp/a
javap <v2-class> | grep -oE "<pattern>" | sort > /tmp/b
comm -3 /tmp/a /tmp/b
```

## When a jar is not enough

If the question is "what does it *do* at runtime" (event payloads, log lines, exit codes), run it:
`testProjects/karate-versions` has `:v2:eventProbe` for the Karate event stream, and
`.claude/skills/debug-test-runner/` covers replaying the IDE's exact java invocation.

## Staleness trap

When behavior contradicts the code you just changed, verify which artifact actually loaded before
debugging the logic: `javap` the class inside the jar at its *deployment* location (sandbox dir,
test-context plugins dir), not the one in `build/libs`. Two hours have been lost to a fix that was
built but not the copy being run.
