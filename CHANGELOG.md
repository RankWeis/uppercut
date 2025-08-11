<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# uppercut Changelog

## [Unreleased]

## [2.4.9] - 2025-08-11

### Modified

- Adds some additional parsing ability and support for 2025.2 intelliJ

### Fixed

- Run configs sometimes producing incorrect config.

## [2.4.8] - 2025-04-17

### Changes

- Updates for 2025.1 IntelliJ

## [2.4.7] - 2025-04-10

### Modified

- Highlighting is a little more reliable now.
- Expression syntax in json snippets (`#{variable}` and `"#{variable}"`) render better.

## [2.4.3] - 2025-04-03

### Fixed

- karate-config.js would sometimes fail silently, now it provides explicit warnings.
- Helpful improvements to js expression error checking.

## [2.4.2] - 2025-03-30

### Added

- If karate-junit5 is not in the classpath, adds a default one (for version 1.5.1) into the classpath
- NOTE: This can cause inconsistent results, and you should attempt to provide a default karate-junit5 of your own.

### Fixed

- Syntax highlight issue.
- Prematurely loading of classes should no longer happen
- karate local variable no longer marks as unresolved

### Modified

- Display of test results in console
- Nesting structure for running tests (If feature A calls feature B, B will nest under A)
- Test Runner Console links to classpath files

## [2.3.3] - 2025-02-28

### Fixed

- Logs should now display in full when testing.
- No longer overrides your logging settings.
- Debugger no longer suspends job (unless you want it to)

## [2.3.1] - 2025-02-07

### Fixed

- Issue where karate-config tests would not terminate on test runs.

## [2.3.0] - 2025-02-07

### Fixed

- Major bug that would run each karate process twice

## [2.2.1] - 2025-02-06

### Updated

- Default env no longer set to 'DEV'

## [2.2.0] - 2025-01-28

### Updated

- Made default parallelism configurable (Settings -> Tools -> Karate)
- Made default parallelism 1 instead of 5

## [2.1.3] - 2025-01-28

### Updated

- Fixed issue where java 21 was necessary to run certain tests (Java 17+ is now required)

## [2.1.2] - 2025-01-24

### Updated

- Compatibility changes for intellij 2025.*

## [2.1.1] - 2025-01-18

### Updated

- Ensuring compatibility with future intellij versions.

## [2.1.0] - 2024-12-16

### Added

- Better formatting of JavaScript files in non-ultimate versions.
- Better formatting of JavaScript files in ultimate.
- Better handling of running tests (better tree and the right messages go to the right scenario)
- Better handling of running tagged tests (Doesn't run things from target/ directory now) 

### Fixed

- Potential crash when parsing js files in non-ultimate versions.

## [2.0.2] - 2024-12-11

### Added

- Major rewrite of parsing algorithm.
- Ability to auto-complete action keywords.
- Most requested feature! Javascript highlighting in IntelliJ Community
- Formatting for JavaScript, JSON and XML snippets.

### Request

- I have tested this to the best of my ability, but please create an issue at [here](https://github.com/RankWeis/uppercut) if you notice something amiss!

### Mentions

- Another special thanks to @calvario-31 on GitHub for contributing time and effort into making this plugin better! These features were all requests by him.

## [2.0.0] - 2024-12-07

### Added

- Major rewrite of parsing algorithm.
- Ability to auto-complete action keywords.
- Most requested feature! Javascript highlighting in IntelliJ Community
- Formatting for JavaScript, JSON and XML snippets.

### Request

- I have tested this to the best of my ability, but please create an issue at [here](https://github.com/RankWeis/uppercut) if you notice something amiss!

### Mentions

- Another special thanks to @calvario-31 on GitHub for contributing time and effort into making this plugin better! These features were all requests by him.

### Fixed

- Auto rename functionality
- Exception that could sometimes occur during formatting.

## [1.3.4] - 2024-11-28

### Fixed

- Exception that could sometimes occur during formatting.

## [1.3.3] - 2024-11-27

### Added

- Ability to format injected languages (xml, json, javascript) in the editor.

## [1.3.2] - 2024-11-26

### Added

- Ability to go-to links that aren't only 'classpath:'

### Fixed

- Issue where <text> in angle brackets would be improperly formatted.
- Issue where [key] in brackets would be parsed as JSON.
- Issue where code folding would not persist after restart.
- Issue where some xml would not be parsed as xml.

## [1.3.1] - 2024-11-19

### Added

- Support for JSON in IntelliJ CE.

## [1.3.0] - 2024-11-17

### Fixed

- Overhaul of the syntax engine to be more stable.
- No longer conflicts with Gherkin plugin.
- Autoformatting bugs.
- Issue where karate HTML reports were poorly formatted.

### Mentions

- Special thanks to @calvario-31 on GitHub for contributing time and effort into making this plugin better!

## [1.2.7] - 2024-11-16

### Fixed

- No longer conflicts with Gherkin plugin.

### Mentions

- Special thanks to @calvario-31 on GitHub for contributing time and effort into making this plugin better!

## [1.2.6] - 2024-11-15

### Fixed

- Autoformatting bugs.

### Mentions

- Special thanks to @calvario-31 on GitHub for contributing time and effort into making this plugin better!

## [1.2.4] - 2024-11-15

### Fixed

- Issue where karate HTML reports were poorly formatted.

## [1.2.3] - 2024-11-15

### Fixed

- Issue where some action keywords would involuntarily get spaces added to them.
- Issue where karate.log may not be generated if logback-test.xml was present.

## [1.2.2] - 2024-11-12

### Modified

- Increased support for new intellij versions.

## [1.2.0] - 2024-11-11

### Added

- Support for new intelliJ versions.
- More keyword support.

## [1.1.5] - 2024-11-07

### Modified

- Autoformatting has significant improvements and bug fixes.
- Allows for changing of step signifiers separately from comments in color settings.

## [1.1.4] - 2024-11-04

### Modified

- karate.log populated when running tests from the plugin.

## [1.1.3] - 2024-08-29

### Modified

- Fewer intellij exceptions when not using karate tests.

## [1.1.1] - 2024-07-15

### Modified

- Logo for plugin

## [1.1.0] - 2024-07-10

### Added

- New icon for the plugin, much easier to see at low resolutions.

## [1.0.2] - 2024-07-02

### Fixed

- Streamlining creating references, avoids an intelliJ error.
- JSON syntax sticks even when making edits to the JSON.

## [1.0.1]

### Modified

- Small QoL improvements.
- Much smaller file size, no longer bundling Karate.

## [1.0.0]

### Added

- Official release version.
- Better symbol matching.

## [0.0.9]

### Added

- Better symbol matching.

## [0.0.8]

### Added

- Ability to run tests from a default environment. Simple select your environment from
"Settings -> Tools -> Karate"
- Ability to run plugin on community editions.

## [0.0.7]

### Added

- References! Now you can go to declaration/find usages on many variables.

## [0.0.6]

### Added

- Some better syntax highlighting/fixes
- Better js/json parsing.

## [0.0.5]

### Added

- Some better syntax highlighting/fixes

## [0.0.4]

### Added

- Dependency on karate 1.5.0.RC3 for ease of development

### Removed

- Requirement for JUnit plugin

## [0.0.3]

### Added

- Fixes compatibility issues with intelliJ
- Allows for running of tests from gutter

## [0.0.2]

### Added

- Initial plugin with syntax highlighting and clickable links.

[Unreleased]: https://github.com/rankweis/uppercut/compare/v2.4.9...HEAD
[2.4.9]: https://github.com/rankweis/uppercut/compare/v2.4.8...v2.4.9
[2.4.8]: https://github.com/rankweis/uppercut/compare/v2.4.7...v2.4.8
[2.4.7]: https://github.com/rankweis/uppercut/compare/v2.4.3...v2.4.7
[2.4.6]: https://github.com/rankweis/uppercut/compare/v2.4.5...v2.4.6
[2.4.5]: https://github.com/rankweis/uppercut/compare/v2.4.3...v2.4.5
[2.4.4]: https://github.com/rankweis/uppercut/compare/v2.4.3...v2.4.4
[2.4.3]: https://github.com/rankweis/uppercut/compare/v2.4.2...v2.4.3
[2.4.2]: https://github.com/rankweis/uppercut/compare/v2.3.3...v2.4.2
[2.4.1]: https://github.com/rankweis/uppercut/compare/v2.3.7...v2.4.1
[2.4.0]: https://github.com/rankweis/uppercut/compare/v2.3.7...v2.4.0
[2.3.7]: https://github.com/rankweis/uppercut/compare/v2.3.3...v2.3.7
[2.3.6]: https://github.com/rankweis/uppercut/compare/v2.3.3...v2.3.6
[2.3.5]: https://github.com/rankweis/uppercut/compare/v2.3.3...v2.3.5
[2.3.3]: https://github.com/rankweis/uppercut/compare/v2.3.1...v2.3.3
[2.3.2]: https://github.com/rankweis/uppercut/compare/v2.3.1...v2.3.2
[2.3.1]: https://github.com/rankweis/uppercut/compare/v2.3.0...v2.3.1
[2.3.0]: https://github.com/rankweis/uppercut/compare/v2.2.1...v2.3.0
[2.2.1]: https://github.com/rankweis/uppercut/compare/v2.2.0...v2.2.1
[2.2.0]: https://github.com/rankweis/uppercut/compare/v2.1.3...v2.2.0
[2.1.3]: https://github.com/rankweis/uppercut/compare/v2.1.2...v2.1.3
[2.1.2]: https://github.com/rankweis/uppercut/compare/v2.1.1...v2.1.2
[2.1.1]: https://github.com/rankweis/uppercut/compare/v2.1.0...v2.1.1
[2.1.0]: https://github.com/rankweis/uppercut/compare/v2.0.2...v2.1.0
[2.0.2]: https://github.com/rankweis/uppercut/compare/v2.0.0...v2.0.2
[2.0.0]: https://github.com/rankweis/uppercut/compare/v1.3.4...v2.0.0
[1.3.4]: https://github.com/rankweis/uppercut/compare/v1.3.3...v1.3.4
[1.3.3]: https://github.com/rankweis/uppercut/compare/v1.3.2...v1.3.3
[1.3.2]: https://github.com/rankweis/uppercut/compare/v1.3.1...v1.3.2
[1.3.1]: https://github.com/rankweis/uppercut/compare/v1.3.0...v1.3.1
[1.3.0]: https://github.com/rankweis/uppercut/compare/v1.2.7...v1.3.0
[1.2.7]: https://github.com/rankweis/uppercut/compare/v1.2.6...v1.2.7
[1.2.6]: https://github.com/rankweis/uppercut/compare/v1.2.4...v1.2.6
[1.2.4]: https://github.com/rankweis/uppercut/compare/v1.2.3...v1.2.4
[1.2.3]: https://github.com/rankweis/uppercut/compare/v1.2.2...v1.2.3
[1.2.2]: https://github.com/rankweis/uppercut/compare/v1.2.0...v1.2.2
[1.2.0]: https://github.com/rankweis/uppercut/compare/v1.1.5...v1.2.0
[1.1.5]: https://github.com/rankweis/uppercut/compare/v1.1.4...v1.1.5
[1.1.4]: https://github.com/rankweis/uppercut/compare/v1.1.3...v1.1.4
[1.1.3]: https://github.com/rankweis/uppercut/compare/v1.1.1...v1.1.3
[1.1.1]: https://github.com/rankweis/uppercut/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/rankweis/uppercut/compare/v1.0.2...v1.1.0
[1.0.2]: https://github.com/rankweis/uppercut/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/rankweis/uppercut/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/rankweis/uppercut/compare/v0.0.9...v1.0.0
[0.0.9]: https://github.com/rankweis/uppercut/compare/v0.0.8...v0.0.9
[0.0.8]: https://github.com/rankweis/uppercut/compare/v0.0.7...v0.0.8
[0.0.7]: https://github.com/rankweis/uppercut/compare/v0.0.6...v0.0.7
[0.0.6]: https://github.com/rankweis/uppercut/compare/v0.0.5...v0.0.6
[0.0.5]: https://github.com/rankweis/uppercut/compare/v0.0.4...v0.0.5
[0.0.4]: https://github.com/rankweis/uppercut/compare/v0.0.3...v0.0.4
[0.0.3]: https://github.com/rankweis/uppercut/compare/v0.0.2...v0.0.3
[0.0.2]: https://github.com/rankweis/uppercut/commits/v0.0.2
