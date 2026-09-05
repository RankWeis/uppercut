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
| Debug a run from the IDE (breakpoints in feature files) | Supported | Planned |
| Fixed debug port for the test JVM | Supported | Supported |
| Pause at a breakpoint and resume | Supported | Planned - first piece of the design |
| Step over, variables view for feature steps | Not planned | Later, after pause/resume |

Karate 2 debugging is designed around Karate 2's own `debugSupport` hook - pause at a breakpoint, resume - and is the next piece of Karate 2 work. It was held back from 3.0.0 to keep that release small.

## Known limitations

- A JavaScript `class { ... }` written inline on a `* def` line is treated as JSON and flagged; put classes in a `"""` block, where they are handled as JavaScript.
- Karate 2's `karate-events.jsonl` and HTML report are Karate's own output and open outside the IDE; the test tree is built from Karate's live events instead.
