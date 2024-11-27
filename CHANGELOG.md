<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# uppercut Changelog

## [Unreleased]

## [1.3.3-2024.2] - 2024-11-27

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

[Unreleased]: https://github.com/rankweis/uppercut/compare/v1.3.3-2024.2...HEAD
[1.3.3-2024.2]: https://github.com/rankweis/uppercut/compare/v1.3.2...v1.3.3-2024.2
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
