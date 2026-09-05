---
title: Settings reference
nav_order: 3
---

# Settings reference

Two places hold settings: **Settings > Tools > Karate** for defaults that apply to every run, and the **run configuration** for one feature, scenario or tag. A run configuration field that is left empty falls back to the Tools > Karate default.
{: .fs-5 }

## Settings > Tools > Karate

### Default environment

The value of `karate.env` for runs that don't set one - the same thing `-Dkarate.env=...` does on the command line. Karate picks its config from it (`karate-config-<env>.js`). Leave empty to run with no environment, which is Karate's default.

### Default parallelism

How many scenarios run at once when a run configuration doesn't say. `1` runs them one after another and keeps the test tree in file order, which is the best setting while you're debugging a failure. Anything higher is passed straight to Karate's parallel runner; the test tree still attributes every step to the right scenario, however the threads interleave.

### Karate version

Which of the plugin's two run paths a feature goes down.

`AUTO`
: The default. The plugin looks at the module the feature belongs to: `karate-core` 2.x or `karate-junit6` on its classpath means Karate 2, anything else means Karate 1. Because it's per module, a repository can migrate one module at a time. A module with no Karate of its own defers to what the rest of the project has.

`V1`, `V2`
: Force one path for every run in the IDE. Use it when detection gets it wrong - for instance a `karate-core` 2.x that reached a Karate 1 module transitively - or when jar names carry no version. The pin wins whenever the classpath is ambiguous; it's refused only when it cannot work at all (V1 with no Karate 1 on the classpath, or the reverse), and the run says why.

This is an application-level setting: it applies to every project open in the IDE.

### Use Karate JavaScript engine (restart required)

Off by default. When on, the plugin handles the JavaScript inside feature files with its own engine even though JetBrains' JavaScript plugin is installed. Two reasons to switch it on:

- **Something's wrong with the JavaScript formatter or highlighting** inside features, and you want the plugin's Karate-aware handling instead while it's sorted out. This is the escape hatch.
- **You've moved from IDEA Community to Ultimate** and want feature files to keep looking and formatting the way they did. Community has no JavaScript plugin, so it always uses this engine.

Its engine is a port of the parser inside Karate itself, so it accepts what Karate accepts. It doesn't do JavaScript-plugin things like resolving symbols across `.js` files or reporting unused variables. Takes effect after a restart.

## Run configuration

Created for you when you run from the gutter; edit it from Run > Edit Configurations.

File to run
: The feature file. Set automatically.

Test Name
: Restricts the run to one scenario. Set automatically when you run a single scenario from the gutter; clear it to run the whole file.

Tag
: Run every scenario carrying this tag instead of a file - `@smoke`, with or without the `@`. When set, the working directory is searched for features rather than the single file.

Environment
: `karate.env` for this run. Empty means the Tools > Karate default.

Parallelism
: Threads for this run. Empty means the Tools > Karate default.

Debug port (will suspend if set)
: Only used when you start the configuration with **Debug** rather than Run. Normally the IDE picks a free port for the test JVM; set this to use a fixed one - useful when a firewall or a container only allows certain ports. The JVM starts suspended on it and waits for the IDE's debugger to attach, which happens automatically. On Karate 1, breakpoints in feature files are then hit. On Karate 2 the run still starts under the debugger, but feature-file breakpoints don't pause it yet - see the [status page](status#debugging).

There is no working-directory field: the plugin sets it to the content root that contains the feature (the module directory in a Gradle or Maven project), which is where Karate looks for `karate-config.js` and resolves `classpath:` paths from.
