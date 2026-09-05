---
title: What it does
nav_order: 1
---

# Uppercut: Karate in IntelliJ IDEA

Uppercut makes `.feature` files first-class in IntelliJ IDEA. It works in IDEA Community and Ultimate, from 2026.1 on, with **Karate 1.x fully supported** and **Karate 2.x in early access** since version 3.0.0.
{: .fs-5 }

[Support status](status){: .btn .btn-primary } [Settings reference](settings){: .btn } [Troubleshooting](troubleshooting){: .btn }

## Full Karate syntax support

Feature files get real language support, not Gherkin with the Karate parts painted grey. Step actions, match markers, embedded JSON, XML and JavaScript are all understood. In IDEA Community, where JetBrains' JavaScript plugin isn't available, the plugin brings its own JavaScript engine for the code inside your features - including the syntax Karate 2 accepts, such as optional chaining, `??`, classes and BigInt literals.

Highlighting
: Karate keywords, step actions, match markers and tags, with embedded JSON, XML and JavaScript highlighted in their own right.

Completion
: Step actions as you type, and `karate.*` members matched to the Karate version on your module's classpath, so `karate.` offers what your Karate actually has.

Navigation
: Jump from a `call read('other.feature')` to the feature, from a variable to its `def`, and from a step to its definition. Find usages and rename work across features.

Formatting
: Reformat feature files, including the JavaScript and JSON inside them.

Inspections and quick fixes
: Undefined steps, broken or misaligned tables, missing `Examples`, a misplaced `Background`, JSON that isn't valid, and an intention to turn a `Scenario` into a `Scenario Outline`.

## Run from the gutter

Click the run icon next to a feature, a scenario or a tag, or right-click a folder that holds feature files and choose **Karate tests in '...'** - in a Gradle project that entry takes the place of Gradle's own "Tests in" for that folder, since the folder's Java runner class is still runnable on its own. Results appear in IntelliJ's test tree as they happen, with each scenario's steps and their output beneath it, called features nested under the scenario that called them, and a double-click that takes you to the source line. Environment and parallelism come from the run configuration or from the defaults you set once.

Karate 1 and Karate 2 projects both work, and the version is detected **per module**, so a repository can migrate one module at a time. If you'd rather decide yourself, one setting pins it.

## Debugging

Press Debug on a Karate 1 run and breakpoints in your feature files are hit, with the usual stepping and variables view for the Java underneath. Karate 2 debugging is designed and next in line; see the [status page](status) for where that stands.
