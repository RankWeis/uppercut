---
name: debug-test-runner
description: Reproduce what KarateTestRunner does inside the IDE from the command line, to tell plugin bugs apart from runner bugs. Use when a run from the gutter produces an empty test tree, "Test framework quit unexpectedly", or output the IDE console swallows.
---

# Debugging the test runner outside the IDE

When a run launched from the IDE misbehaves, the child JVM's output is easy to lose. Replaying the
exact same `java` invocation from a shell makes the failure obvious in seconds - a
`NoClassDefFoundError` that the IDE reports only as "Test framework quit unexpectedly" prints
in full.

First check that a JVM was launched at all. Since 3.0.0 the run configuration refuses up front -
an error balloon, no console - when the feature is outside any module, the module has no
`karate-*` jar, the library scan is empty (import still running), or the version pin cannot match
the classpath. Those are IDE-side and explained on the troubleshooting page the message links; the
replay below is for runs that did launch.

## Find out what the IDE actually launched

The run configuration the producer created is written into the (temp) project's workspace file:

```bash
sed -n '/RunManager/,/\/component/p' <projectDir>/.idea/workspace.xml
```

That gives the main class, module, working directory, and `PreferredTest` mode. The program
arguments are built in `KarateRunConfiguration.createJavaParameters` - read it for the current
flag names (`--karate-major-version`, `--testname`, `--relpath`, `--working-dir`, `--tag`,
`--parallelism`, `--environment`).

## Replay it

Capture the sample project's test runtime classpath without editing its build file, via an init
script:

```groovy
// cp.gradle
allprojects {
    tasks.register("printTestCp") {
        doLast {
            def cp = project.sourceSets.test.runtimeClasspath.files.collect { it.absolutePath }
                .join(File.pathSeparator)
            new File(System.getProperty("cpOut")).text = cp
        }
    }
}
```

```bash
./gradlew -p testProjects/karate-versions :v2:printTestCp -I cp.gradle -DcpOut=/tmp/testcp.txt
./gradlew :KarateTestRunner:jar
```

(`:v1:printTestCp` for the Karate 1 module.) Then run the main class with that classpath **plus**
the runner jar, from the configured working directory (the module's `src/test`), passing the same
arguments the IDE passes - `:` between classpath entries on macOS/Linux, `;` on Windows:

```bash
java -cp "$(cat /tmp/testcp.txt):$PWD/KarateTestRunner/build/libs/KarateTestRunner.jar" \
  com.rankweis.uppercut.testrunner.KarateTestRunner \
  --karate-major-version 2 --testname "sample/users.feature" --relpath "sample/users.feature" --parallelism 1
```

A healthy v2 run prints `<<UPPERCUT-V2>> SUITE_ENTER {...}` and one event per feature/scenario,
then exits 0. Every event carries a `"thread"` field (`vt-<id>` - Karate 2's virtual threads are
unnamed) and `STEP_EXIT` events carry `"stepLog"`; with `--parallelism 4` the `SCENARIO_ENTER`
lines show distinct thread ids interleaving, which is the direct proof that Karate ran in parallel.

## Interpreting the result

| Symptom | Meaning |
|---|---|
| Runs fine here, empty tree in the IDE | IDE-side problem: version detection, converter wiring, or source-root resolution |
| Fails here the same way | Runner problem - and you now have the real stack trace |
| `Unable to initialize main class` | A class the runner links against is missing from the user's classpath. The runner must not hard-link anything Karate does not guarantee (this is how the logback bug happened - Karate 2 dropped logback, which 1.x provided transitively) |
| `SLF4J(W): No SLF4J providers were found` and no Karate log lines | Karate 2 project with no logging provider: karate-core 2.x depends on slf4j-api only. Harmless to the plugin (v2 step output comes from `StepResult.getLog()`, not the log); the user adds `logback-classic` to see Karate's console output |
| Runs fine, but the folder run in the IDE shows `SampleTest` → `testSample` nodes | That was Gradle's "Tests in ..." configuration, not the plugin's - karate-junit6 ran the features itself. The plugin's is "Karate tests in '<folder>'" |

## Checking the Karate side alone

To confirm Karate itself works independently of Uppercut's runner:

```bash
./gradlew -p testProjects/karate-versions :v2:eventProbe
./gradlew -p testProjects/karate-versions :v2:eventProbe -PprobePath=classpath:broken
```

`eventProbe` drives `io.karatelabs.core.Runner` directly and dumps every event's `toJson()` -
the compiled twin of what `KarateV2TestRunner` does reflectively.
