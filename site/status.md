---
title: Support status
nav_order: 2
---

# Support status

What works, what's partial, and what's coming - by feature and by Karate version. Updated with each release; the changelog on the [Marketplace page](https://plugins.jetbrains.com/plugin/24736) has the details per version.
{: .fs-5 }

**Legend.** Supported: covered by tests and in daily use. Early access: works end to end, less mileage. Partial: some of it. Planned: designed or scheduled. Not planned: a deliberate decision, with the reason.
{: .note }

## Versions

| | Status |
|:--|:--|
| Karate 1.x (`karate-junit5`) | Supported. Unchanged by the Karate 2 work; remains the default for existing projects |
| Karate 2.x (`karate-junit6`) | Early access since 3.0.0 |
| IntelliJ IDEA Ultimate | Supported, 2026.1 and newer |
| IntelliJ IDEA Community | Supported, 2026.1 and newer. JavaScript inside features is handled by the plugin's own engine |
| Newer IDE releases | No upper version bound. The plugin stays installable when a new IDE ships; if a platform change breaks it, the Marketplace's verifier flags that version and a fix follows |

## Editor

These are the same for Karate 1 and Karate 2 - the language is the same.

| Feature | Status | Notes |
|:--|:--|:--|
| Syntax highlighting | Supported | Karate, embedded JSON, XML and JavaScript |
| Step action completion | Supported | Including `form`, `multipart`, `soap` since 3.0.0 |
| `karate.*` member completion | Supported | Members matched to the Karate version on the module's classpath |
| Go to declaration | Supported | Called features, variables, step definitions |
| Find usages, rename | Supported | |
| Formatting | Supported | Feature files, embedded JavaScript and JSON |
| Inspections | Supported | Undefined step, tables, examples, background placement, JSON validity |
| Scenario → Scenario Outline | Supported | Intention action |
| Modern JavaScript without the JavaScript plugin | Supported | Optional chaining, `??`/`??=`, `class`, `this`, `continue`, `void`, BigInt. Fixed in 3.0.0 for IDEA Community |
| Karate 2 config files (`karate-base.js`, `karate-boot.js`) | Supported | Nothing to configure - the plugin never keyed off the config file name |

## Running

| Feature | Karate 1 | Karate 2 |
|:--|:--|:--|
| Run a feature, scenario or tag from the gutter | Supported | Early access |
| Live test tree with per-scenario steps and output | Supported | Early access |
| Called features nested under the calling scenario | Supported | Early access |
| Navigate from the test tree to the source line | Supported | Early access |
| Scenario Outline examples shown individually | Supported | Early access |
| Environment and parallelism per run | Supported | Early access |
| Version detected per module | n/a | Early access. Mixed-version repositories migrate one module at a time |
| Settings pin (V1 / V2) | Supported | Supported. The pin wins whenever the classpath is ambiguous |
| Run without Karate's JUnit artifact on the classpath | Supported | Not planned. The plugin bundles Karate 1's runner for projects that only have `karate-core`; Karate 2's `karate-junit6` brings `karate-core` with it, so there is no gap to cover, and running a bundled copy would test against a different Karate than the build declares |

## Debugging

| Feature | Karate 1 | Karate 2 |
|:--|:--|:--|
| Breakpoints on a step in a `.feature` file | Supported | Early access |
| Variables of the paused scenario | Early access | Early access |
| Evaluate a Karate expression while paused | Early access | Early access |
| Pin the debug port | Supported | Supported |
| Pause on a failed step | Early access, on by default | Early access, on by default |
| Step to the next step | Early access | Early access |
| Step over vs into a called feature | Not yet - stepping enters the called feature | Not yet - stepping enters the called feature |
| Breakpoint conditions | Early access | Early access |
| Skip the step the run is stopped on | Early access | Early access |
| Java breakpoints during a Karate run | Opt-in, second tab | Opt-in, second tab |

**One debugger, both majors.** Put a breakpoint on a step in a `.feature` file and press Debug: the
run stops before that step, the line is highlighted, and the debugger shows the scenario's variables -
`response`, and anything a `def` has set - as Karate holds them. Evaluate and the watches panel run
*Karate* expressions in the paused scenario, so `response.items[0]` means there what it means in the
feature. Resume continues to the next breakpoint or to the end. Scenarios run one at a time while
debugging, whatever the parallelism setting says, so a suspended run is followable.

**Java breakpoints are off unless you ask for them.** Karate has no user-written step definitions -
the DSL lives inside karate-core - so the JVM debugger is not how you stop on a step; the plugin does
that itself on both majors, without JDWP, which is what lets it show Karate's own variables. The JVM
debugger is still the only way to reach Java a feature calls through `Java.type(...)`, or to step
into karate-core, so a run configuration can ask for it: tick **Attach the JVM debugger too** and
Debug opens a second tab, an ordinary Remote JVM Debug session on the same test JVM. Java breakpoints
in your `.java` files then stop the run as they always did, and the Karate tab keeps its own
breakpoints on the feature.

One thing to expect when both are on: **while the Java tab is stopped, the Karate tab does not
answer.** A Java breakpoint suspends every thread in the JVM, including the one the Karate debugger
talks over, so its variables, Evaluate and Resume do nothing until you resume the Java tab. Nothing
is lost - whatever you clicked is acted on as soon as the JVM is running again - but it is why the
second debugger is off by default. If you only want Java breakpoints, running the `@Karate.Test`
JUnit class through IntelliJ's ordinary Java or Gradle test configuration is still the simpler
answer; that run has no feature-file breakpoints.

**Karate 1 changed here.** Its breakpoints used to be Java breakpoints bound to the bytecode of a
step-definition method found by matching the step's text, which stopped inside karate-core with Java
locals in view and silently failed to bind for steps no method matched. They now stop on the step
itself, with the scenario's variables.

**A failed step stops the run**, so the scenario is still standing when you look at it: the step is
marked in red, the error is shown, and the variables are as the failure left them - usually the
question the debugger was opened for. Turn it off with the **Pause on Failed Step** toggle on the
Debug toolbar, which takes effect on the next step rather than the next run, or under Settings >
Tools > Karate.

**Stepping runs to the next step.** All three step buttons do that. A step that calls another feature
stops on the called feature's first step rather than after the call: telling step over from step into
needs call-depth tracking that is not built yet.

**Breakpoints can be conditional.** Put a Karate expression in the breakpoint's Condition field and
the run stops there only when it holds - `id == 'x'`, or just `response.error` for "when there is
one". Anything that is not `false`, `null` or an empty string counts as true. A condition that cannot
be evaluated stops the run and shows why, rather than quietly never stopping.

**Skip Step**, on the debugger toolbar, continues without running the step the run is stopped on -
for when a step is failing for an uninteresting reason and the part you are debugging is further
down. It uses Karate's own skip, so the run continues exactly as if that step had been left out.

**Early access** means the mechanism is proven and covered by tests end to end, but has far less
mileage than the years behind Karate 1's run support.

## Known limitations

- A JavaScript `class { ... }` written inline on a `* def` line is treated as JSON and flagged; put classes in a `"""` block, where they are handled as JavaScript.
- Karate 2's `karate-events.jsonl` and HTML report are Karate's own output and open outside the IDE; the test tree is built from Karate's live events instead.
