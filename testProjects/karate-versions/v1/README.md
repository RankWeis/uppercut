# Karate 1 Sample Project

Standalone sample project (NOT part of the root build) covering the **Karate 1.x** runner path:
the `RuntimeHook` proxy, the regex-based console converter, and the `<<UPPERCUT>>` logback
appender. Deliberately mirrors `../v2` so the two paths can be compared like for like.

Karate 1.x ships `karate-junit5`; 2.x renamed it to `karate-junit6`, which is what Uppercut's
classpath detection keys on (`KarateRunConfiguration.isKarateV2`).

## Run

```bash
./gradlew -p testProjects/karate-versions :v1:test
```

Requires Java 17+.

## Layout

- `src/test/java/sample/users-v1.feature` — two passing scenarios; the first `call`s
  `called.feature`, exercising the v1 caller-chain reflection.
- `src/test/java/broken/broken-v1.feature` — one failing + one passing scenario for the failure path.

## Testing through the plugin

1. `./gradlew runIde` from the repo root.
2. Open this project in the sandbox IDE (import the Gradle project so karate-junit5 lands on the
   classpath).
3. Click the gutter run icon in `users-v1.feature` — the run configuration should **not** pass
   `--karate-major-version 2`, and the console should show `<<UPPERCUT>>`-prefixed log lines.
