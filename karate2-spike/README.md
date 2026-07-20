# Karate 2 Spike Project

Standalone sample project (NOT part of the root build) used to verify how Uppercut should
integrate with Karate 2.x. See `docs/KARATE2.md` for the full plan and the verified API notes.

## Run

```bash
# baseline: idiomatic karate-junit6 run
../gradlew -p karate2-spike test

# dump the v2 RunListener event stream (what Uppercut's runner will consume)
../gradlew -p karate2-spike eventProbe
```

Requires Java 21+ (Karate 2 uses virtual threads).

## What to verify with eventProbe

1. **Event payloads** — which fields `RunEvent.toJson()` provides per type
   (feature path? scenario name? line number? failure message?).
2. **Called-feature nesting** — `users.feature` calls `called.feature`; how does that
   show up in the event stream? (v1 needed a caller-chain hack via engine internals.)
3. **Listener semantics** — does returning `true` from `onEvent` consume the event
   (suppressing Karate's own console log) or simply acknowledge it?
4. **Scenario line filtering** — v2 `path()` accepting `path/to.feature:12` for
   single-scenario runs (Uppercut's SINGLE_SCENARIO mode).
5. **karate-config.js discovery** — confirm it's picked up from the classpath root and/or
   working directory as documented.

## Testing through the plugin

1. `./gradlew runIde` from the repo root.
2. Open this `karate2-spike` project in the sandbox IDE (import the Gradle project so
   karate-junit6 lands on the classpath).
3. Click the gutter run icon in `users.feature` — the run configuration should pass
   `--karate-major-version 2` and the console should show `<<UPPERCUT-V2>>` event lines.
