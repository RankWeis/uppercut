# uppercut

![Build](https://github.com/rankweis/uppercut/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/24736.svg)](https://plugins.jetbrains.com/plugin/24736/)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/24736.svg)](https://plugins.jetbrains.com/plugin/24736)

<!-- Plugin description -->
Full IDE support for the [Karate](https://github.com/karatelabs/karate) API testing framework — syntax
highlighting, navigation, run/debug and formatting for `.feature` files. Free and open source, Apache 2.0.

**Docs:** what works, what's in progress, and what every setting means: <https://rankweis.github.io/uppercut/>

**Features**

- **Syntax highlighting** for `.feature` files, including Karate-specific keywords and expressions.
- **Embedded JSON, JavaScript and XML** — highlighting, completion and error checking *inside* the payloads in
  your feature files, not just around them.
- **Run tests from the editor** — click the gutter arrow on any feature or scenario, or build your own run
  configurations. Results land in the standard test runner window, step by step.
- **Attach a debugger** to a running Karate test and step through it.
- **Go to definition and find usages** across feature files — ctrl-click a `call` to jump straight to the
  called feature.
- **Formatting** for feature files, wired to the usual reformat action.

**Why Uppercut**

Uppercut is a free, open source alternative to the official Karate IntelliJ plugin, released under the
Apache License 2.0 — no license key, no seat management, no EULA to route through legal. Install it on
every machine on the team, including contractors and short-term hires.

**Karate 2.x — early access**

Karate 2.x (`karate-junit6`) is supported as early access. The version is detected per module from the
classpath, so a repository can migrate one module at a time, and Karate 1.x projects keep working exactly as
before. Debugging Karate 2 features is not wired up yet.

**Work in progress.** Please [report bugs and feature requests](https://github.com/rankweis/uppercut/issues) so
we can make this plugin something we all enjoy using.

<!-- Plugin description end -->

## Installation

- Using the IDE built-in plugin system:
  
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "uppercut"</kbd> >
  <kbd>Install</kbd>
  
- Manually:

  Download the [latest release](https://github.com/rankweis/uppercut/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation

## Collaborators

<!-- readme: calvario31,rpiterman,collaborators -start -->
<table>
	<tbody>
		<tr>
            <td align="center">
                <a href="https://github.com/calvario31">
                    <img src="https://avatars.githubusercontent.com/u/39682391?v=4" width="100;" alt="calvario31"/>
                    <br />
                    <sub><b>calvario31</b></sub>
                </a>
            </td>
            <td align="center">
                <a href="https://github.com/rpiterman">
                    <img src="https://avatars.githubusercontent.com/u/1968526?v=4" width="100;" alt="rpiterman"/>
                    <br />
                    <sub><b>rpiterman</b></sub>
                </a>
            </td>
            <td align="center">
                <a href="https://github.com/RankWeis">
                    <img src="https://avatars.githubusercontent.com/u/733691?v=4" width="100;" alt="RankWeis"/>
                    <br />
                    <sub><b>RankWeis</b></sub>
                </a>
            </td>
		</tr>
	<tbody>
</table>
<!-- readme: calvario31,rpiterman,collaborators -end -->

## Contributors

<!-- readme: contributors -start -->
<table>
	<tbody>
		<tr>
            <td align="center">
                <a href="https://github.com/RankWeis">
                    <img src="https://avatars.githubusercontent.com/u/733691?v=4" width="100;" alt="RankWeis"/>
                    <br />
                    <sub><b>RankWeis</b></sub>
                </a>
            </td>
            <td align="center">
                <a href="https://github.com/claude">
                    <img src="https://avatars.githubusercontent.com/u/81847?v=4" width="100;" alt="claude"/>
                    <br />
                    <sub><b>Claude</b></sub>
                </a>
            </td>
            <td align="center">
                <a href="https://github.com/qodana-bot">
                    <img src="https://avatars.githubusercontent.com/u/139879315?v=4" width="100;" alt="qodana-bot"/>
                    <br />
                    <sub><b>Qodana</b></sub>
                </a>
            </td>
		</tr>
	<tbody>
</table>
<!-- readme: contributors -end -->