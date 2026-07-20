# Karate 2 Sample Project

Standalone sample project (NOT part of the root build) used by `Karate2UITest` and for verifying
Uppercut's Karate 2.x integration by hand. See `docs/KARATE2.md` for the architecture and the
verified API notes.

## Run

```bash
# baseline: idiomatic karate-junit6 run (from the repo root)
./gradlew -p testProjects/karate-versions :v2:test

# dump the v2 RunListener event stream (what Uppercut's runner consumes)
./gradlew -p testProjects/karate-versions :v2:eventProbe

# probe the deliberately failing feature's event payloads
./gradlew -p testProjects/karate-versions :v2:eventProbe -PprobePath=classpath:broken
```

Requires Java 21+ (Karate 2 uses virtual threads).

## Layout

- `src/test/java/sample/users.feature` — two passing scenarios; the first `call`s
  `called.feature`, which exercises the nested-feature event stream.
- `src/test/java/broken/broken.feature` — one failing + one passing scenario, used by the
  UI test's failure-path assertions. Outside `classpath:sample` so default runs skip it.

## Testing through the plugin

1. `./gradlew runIde` from the repo root.
2. Open this `testProjects/karate-versions` project in the sandbox IDE (import the Gradle project so
   karate-junit6 lands on the classpath).
3. Click the gutter run icon in `users.feature` — the run configuration should pass
   `--karate-major-version 2` and the test tree should show both scenarios with the called
   feature nested beneath the first.
