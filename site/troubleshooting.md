---
title: Troubleshooting
nav_order: 4
---

# Troubleshooting

The messages the plugin writes on purpose, what each one means, and what to do. For anything else, [open an issue](https://github.com/RankWeis/uppercut/issues) with the run's console output - the test runner's own command line and stack trace are in it.
{: .fs-5 }

## "No libraries found on the classpath - the project may still be importing"

The run configuration's module has no libraries at all. Almost always the Gradle or Maven import hasn't finished, or it failed - check the Build tool window. Once the import is done, run again. (In earlier versions this launched anyway and died with the message below.)

## "This feature file is not inside any module, so the run has no classpath"

The IDE doesn't consider the feature part of any module, so there is nothing to build a classpath from. Usually the project was opened from its `pom.xml` or build file instead of being imported - open the project *folder*, or reload it from the Maven or Gradle tool window - and occasionally the project is open through a symlinked path that differs from the one the build tool reports. Either way, once the feature's directory belongs to a module, the run gets its classpath.

## "Module '...' has no Karate on its classpath"

The feature's module exists but declares no Karate dependency of its own. Add `karate-junit5` (Karate 1) or `karate-junit6` (Karate 2) to that module, or run the feature from the module that has it. The plugin no longer launches a run it knows will fail this way.

## "Must have karate-core on the classpath to use uppercut"

The test JVM started but couldn't load Karate. Either the module really doesn't have `karate-core`, or the run went down the wrong path for the Karate it has - a `ClassNotFoundException: io.karatelabs.core.Runner` beneath it means the Karate 2 path was taken on a Karate 1 classpath, or the reverse. Check **Settings > Tools > Karate > Karate version**: on `AUTO`, make sure the module (not just a sibling) declares its Karate dependency; if you've pinned a version, pin the other one or go back to `AUTO`.

## "Karate version is pinned to V1 ... but this module's classpath only has Karate 2"

You pinned a version in Settings > Tools > Karate that can't run on this module. The message names the setting; switch it to `AUTO` or to the other version. The pin is only refused when the classpath has *nothing* the pinned version could use - with both majors present, the pin wins.

## "Test framework quit unexpectedly" with an empty test tree

The test JVM exited before it reported anything. Scroll up in the console: the runner prints the full `java` command line and the exception. The usual causes are the two messages above, a `karate-config.js` that throws, or a feature path Karate couldn't resolve - see the next section.

## `RuntimeException: not found: <feature>` and the run never starts

The feature files aren't on the test classpath. The plugin passes each feature to Karate as its path relative to the source root that holds it, prefixed with `classpath:` - so `src/test/java/orders/checkout.feature` is handed over as `classpath:orders/checkout.feature`. Karate then resolves that against the module's *built* test output, and reports the path without the prefix when it finds nothing. The stack trace comes back wrapped in an `InvocationTargetException` from the test runner, which buries the real line; it is the `Caused by: java.lang.RuntimeException: not found: ...` that matters.

Look in the build output rather than in `src`. For Maven that is `target/test-classes`, for Gradle `build/resources/test`. If the feature you ran isn't there, nothing on the classpath can find it.

**Maven.** Feature files kept next to the Java sources under `src/test/java` are only copied if that directory is declared as a test resource:

```xml
<build>
  <testResources>
    <testResource><directory>src/test/java</directory></testResource>
    <testResource><directory>src/test/resources</directory></testResource>
  </testResources>
</build>
```

Having it declared is not enough on its own - the copy happens in Maven's `process-test-resources` phase, and the IDE's **Build > Rebuild Project** compiles the sources without necessarily running it. Run Maven itself once:

```
mvn process-test-resources
```

and confirm the features appear under `target/test-classes`. A later IDE rebuild clears the output directory again, so if this keeps coming back, turn on **Settings > Build, Execution, Deployment > Build Tools > Maven > Runner > Delegate IDE build/run actions to Maven** and every build runs the full lifecycle.

**Gradle.** Features under `src/test/resources` are copied by `processTestResources` with no extra configuration. Features kept under `src/test/java` need that directory added to the test resources:

```kotlin
sourceSets.test {
    resources.srcDir("src/test/java")
}
```

Then check `build/resources/test`.

Either way, a `call read('classpath:...')` inside a feature fails for exactly the same reason, while a plain relative `call read('helper.feature')` keeps working - that form resolves against the calling feature's own location on disk and never touches the classpath. A suite where the relative form works and the `classpath:` form doesn't is this problem, not a syntax error.

## A step shows "Uppercut could not find a version of karate-junit5 in the classpath. It is using a provided one"

Karate 1 only. The module has `karate-core` but not `karate-junit5`, so the plugin ran the tests with its own bundled copy of Karate 1.5.1. Results are usually right, but you're testing against a different Karate than your build declares - add `karate-junit5` to the module to run with yours. There is no bundled Karate 2; `karate-junit6` brings `karate-core` with it.

## JavaScript in a feature is flagged as invalid, but Karate runs it

If the IDE has the JavaScript plugin, that's JetBrains' JavaScript support reporting, and its messages ("Unresolved variable") are about what it can see, not what Karate does. To make features use the plugin's Karate-aware engine instead, turn on **Use Karate JavaScript engine** and restart. If you're on IDEA Community, or that setting is already on, and valid Karate JavaScript is still marked red, please report it with the snippet - the parser is meant to accept everything Karate 2 accepts.

## A `class { ... }` on a `* def` line is flagged

Inline `{ ... }` on a step line is treated as JSON. Put JavaScript classes, and anything else that starts with a brace but isn't JSON, in a `"""` block.

## Karate 2: `SLF4J(W): No SLF4J providers were found` and no console output

Karate 2 depends on `slf4j-api` only - logback is bundled in Karate's fat jar, not in the `karate-core` library that `karate-junit6` brings. Without a logging provider on the classpath SLF4J falls back to its no-op logger, and Karate's console log - HTTP request/response lines, `print` - goes nowhere. Karate 1 didn't have this because `karate-core` 1.x pulled logback in transitively.

Add a provider to the module, e.g. `testRuntimeOnly("ch.qos.logback:logback-classic:1.5.38")` in Gradle or the equivalent `test`-scope dependency in Maven, and the console comes back. The plugin's test tree is not affected: on Karate 2 it takes each step's output from Karate's own step result, so step logs show under the step nodes with or without logback. Running the tests through Gradle or Maven (IntelliJ's "Tests in '...'" configuration) instead of the Karate run configuration shows only what Karate logs, so that is where the silence is most visible.

## The test tree shows the run but no scenarios

On Karate 2 the tree is built from Karate's live event stream. If the run prints results in the console but the tree stays empty, the plugin isn't seeing the events - most often because output is being redirected or wrapped by a custom logging setup. Please report it with the console output; the lines beginning `<<UPPERCUT-V2>>` are the ones the tree is built from.

## A breakpoint on a step in a Karate 2 `.feature` file never pauses

By design, and not planned to change - see the [debugging table](status#debugging) for why. Karate 1 maps a Gherkin step to a discrete Java method and sets a JDI breakpoint on it; Karate 2 runs steps through the karate-js interpreter on virtual threads, so there is no Java bytecode location to bind to, and Karate Labs' own DAP server isn't available on Maven Central. A v2 debug run prints a one-line notice about this at the top of the console.

Java breakpoints in step-definition code (`@When`, custom Java methods, anything the JVM debugger can reach) do still stop under Karate 2 Debug - the JVM launches with JDWP and IntelliJ attaches to it as normal. Only breakpoints placed on lines inside the `.feature` file itself are skipped.
