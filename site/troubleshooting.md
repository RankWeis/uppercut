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

The test JVM exited before it reported anything. Scroll up in the console: the runner prints the full `java` command line and the exception. The usual causes are the two messages above, a `karate-config.js` that throws, or a feature path Karate couldn't resolve (`cannot find resource: classpath:...`), which points at a working directory that isn't the module root.

## A step shows "Uppercut could not find a version of karate-junit5 in the classpath. It is using a provided one"

Karate 1 only. The module has `karate-core` but not `karate-junit5`, so the plugin ran the tests with its own bundled copy of Karate 1.5.1. Results are usually right, but you're testing against a different Karate than your build declares - add `karate-junit5` to the module to run with yours. There is no bundled Karate 2; `karate-junit6` brings `karate-core` with it.

## JavaScript in a feature is flagged as invalid, but Karate runs it

If the IDE has the JavaScript plugin, that's JetBrains' JavaScript support reporting, and its messages ("Unresolved variable") are about what it can see, not what Karate does. To make features use the plugin's Karate-aware engine instead, turn on **Use Karate JavaScript engine** and restart. If you're on IDEA Community, or that setting is already on, and valid Karate JavaScript is still marked red, please report it with the snippet - the parser is meant to accept everything Karate 2 accepts.

## A `class { ... }` on a `* def` line is flagged

Inline `{ ... }` on a step line is treated as JSON. Put JavaScript classes, and anything else that starts with a brace but isn't JSON, in a `"""` block.

## The test tree shows the run but no scenarios

On Karate 2 the tree is built from Karate's live event stream. If the run prints results in the console but the tree stays empty, the plugin isn't seeing the events - most often because output is being redirected or wrapped by a custom logging setup. Please report it with the console output; the lines beginning `<<UPPERCUT-V2>>` are the ones the tree is built from.
