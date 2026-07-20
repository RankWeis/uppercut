# Karate version fixtures

One Gradle project, two modules, two Karate majors:

| Module | Karate | Covers |
|---|---|---|
| `v1` | karate-junit5 1.5.1 | the v1 runner: `RuntimeHook` proxy, regex console converter, `<<UPPERCUT>>` log appender |
| `v2` | karate-junit6 2.1.1 | the v2 runner: `RunListener` events, `<<UPPERCUT-V2>>` JSON protocol |

They live in one project deliberately. It mirrors a monorepo mid-migration, and it lets
`Karate2UITest` exercise both runner paths in a single IDE session instead of booting the IDE
twice. It also guards the per-module detection in
`KarateRunConfiguration#karateLibraryRoots`: a project-wide scan would see v2's jars and wrongly
pick the v2 runner for the v1 module.

Each module holds the same shape, so the two paths can be compared like for like:

- `src/test/java/sample/users.feature` (v1: `users-v1.feature`) — two passing scenarios, the first calling `called.feature`
- `src/test/java/broken/broken.feature` (v1: `broken-v1.feature`) — one failing + one passing scenario

## Run

```bash
./gradlew -p testProjects/karate-versions :v1:test
./gradlew -p testProjects/karate-versions :v2:test

# dump the v2 RunListener event stream
./gradlew -p testProjects/karate-versions :v2:eventProbe
./gradlew -p testProjects/karate-versions :v2:eventProbe -PprobePath=classpath:broken
```

`v1` needs Java 17+, `v2` needs Java 21+ (virtual threads).
