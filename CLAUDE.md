# CLAUDE.md - Uppercut Project Guide

## Project Overview

Uppercut is an IntelliJ IDEA plugin providing comprehensive IDE support for the **Karate testing framework**. It adds syntax highlighting, code completion, debugging, navigation, inspections, and run configurations for `.feature` (Karate/Gherkin) files. The `.featurejs` file type is a diagnostic hook, not a Karate file type: it hands a whole file to the embedded KarateJs parser so the JavaScript engine can be exercised in isolation from the Gherkin lexer.

- **Plugin ID:** `com.rankweis`
- **Group:** `com.rankweis.uppercut`
- **License:** Apache 2.0
- **Repository:** https://github.com/rankweis/uppercut

## Build System

**Gradle 9.7** with Kotlin DSL (`build.gradle.kts`) and the IntelliJ Platform Gradle Plugin 2.x. Uses the Gradle wrapper (`./gradlew`).

### Key Commands

```bash
# Build the plugin
./gradlew buildPlugin

# Run all checks (unit tests + checkstyle + kover)
./gradlew check

# Run unit tests only
./gradlew test

# Run platform tests (JUnit Vintage engine)
./gradlew platformTest

# Run the IDE integration tests (boots a real IntelliJ; see .claude/skills/ide-integration-tests/)
./gradlew integrationTest --tests com.rankweis.uppercut.karate.ui.Karate2UITest

# Launch IntelliJ with the plugin loaded for manual testing
./gradlew runIde

# Verify plugin compatibility against the newest release + newest EAP (what CI runs)
./gradlew verifyPlugin

# ... against every supported major, since-build up to the newest EAP (before a release)
./gradlew verifyPlugin -PpluginVerifierScope=all

```

Tests in CI run under `xvfb` for headless display:
```bash
xvfb-run --auto-servernum --server-args="-screen 0 1920x1080x24" ./gradlew check -x integrationTest
```

### JDK Requirements

- **Main project:** Java 25 (jvmToolchain)
- **KarateTestRunner subproject:** Java 17 (jvmToolchain)

### Gradle Properties

Key version properties are in `gradle.properties`:
- `pluginVersion` - Current plugin version (SemVer)
- `platformVersion` - Target IntelliJ platform version
- `pluginSinceBuild` - Minimum supported build. There is deliberately no `until-build` (see the `ideaVersion` block in `build.gradle.kts`)
- `karateVersion` - Karate 1.x version bundled as the v1 fallback runner (1.5.1). Karate 2 is never bundled; the v2 path reflects on whatever `karate-core` 2.x the user's module has
- `logbackVersion` - logback used by the plugin itself

Dependency versions are managed in `gradle/libs.versions.toml`.

**When updating `platformVersion`:** Always run `./gradlew verifyPlugin test` to ensure plugin compatibility and tests pass against the new platform version.

## Project Structure

```
uppercut/
├── build.gradle.kts              # Root build configuration
├── settings.gradle.kts           # Multi-project settings
├── gradle.properties             # Version properties
├── gradle/libs.versions.toml     # Dependency version catalog
├── config/checkstyle/            # Checkstyle configuration (Google Java Style)
├── KarateTestRunner/             # Subproject: custom Karate test runner
│   └── build.gradle.kts
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── com/rankweis/uppercut/
│   │   │   │   ├── karate/       # Core plugin logic (packages below)
│   │   │   │   ├── help/         # WebHelpProvider: routes IDE context help to the docs site
│   │   │   │   └── settings/     # Plugin settings UI & persistence
│   │   │   └── io/karatelabs/js/ # Embedded JS lexer/parser (hand-written port of karate-js)
│   │   └── resources/
│   │       ├── META-INF/
│   │       │   ├── plugin.xml        # Main plugin descriptor
│   │       │   └── plugin-withJs.xml # JavaScript support (optional dependency)
│   │       ├── colorSchemes/     # Darcula and Default color schemes
│   │       ├── icons/            # Plugin icons (SVG)
│   │       ├── messages/         # Localization bundles
│   │       └── i18n.json         # Internationalization keywords
│   ├── test/                     # Unit tests
│   │   ├── java/                 # Test classes
│   │   └── testData/             # Test fixture files (.feature)
│   └── integrationTest/kotlin/   # IDE integration tests (IDE Starter + Driver) - the only Kotlin in the repo
├── testProjects/karate-versions/ # Standalone Gradle fixture (NOT in settings.gradle.kts): v1 + v2 modules
├── site/                         # User docs (GitHub Pages): what works, status by Karate version, settings
├── docs/                         # Contributor notes: Karate 2 design/handoff, manual checklist, risk register
└── .github/workflows/
    ├── build.yml                 # CI: build, test, integration-test, verify, draft release
    ├── docs.yml                  # Publish site/ to https://rankweis.github.io/uppercut/
    ├── release.yml               # Publish to JetBrains Marketplace
    ├── contrib.yml               # Contributor attribution
    └── run-ui-tests.yml          # Cross-platform UI tests
```

### Main Source Packages (`com.rankweis.uppercut.karate`)

| Package | Purpose |
|---------|---------|
| `psi/` | PSI elements, parser definitions, file types |
| `psi/impl/` | PSI element implementations |
| `psi/parser/` | Parser builders (KarateJs parser) |
| `psi/annotator/` | Syntax error annotations |
| `psi/formatter/` | Code style settings providers |
| `structure/` | File structure view (public `StructureViewTreeElement` API - the platform's `PsiTreeElementBase` is not exposed to this plugin from 2026.2) |
| `psi/refactoring/` | Rename/refactoring support |
| `psi/i18n/` | Internationalization support |
| `lexer/` | Lexer interface and implementations |
| `lexer/impl/` | Karate language lexer |
| `lexer/karatelabs/` | JavaScript lexer |
| `actions/` | IDE actions (refactoring, selection) |
| `codeinsight/` | Enter handler, typed handler |
| `completion/` | Code completion contributors |
| `debugging/` | Debug session and breakpoint management |
| `extension/` | JSON, JavaScript, inspection extensions |
| `format/` | Code formatting model builders |
| `highlight/` | Syntax highlighting (Gherkin, JavaScript) |
| `inspections/` | Code inspections (undefined steps, broken tables, etc.) |
| `intentions/` | Quick fix actions (ScenarioToOutline) |
| `manipulator/` | PSI element manipulation |
| `navigation/` | Go-to-definition, find references, symbol contributors |
| `run/` | Run configurations, execution, console. Two runner paths: v1 (`RuntimeHook` proxy + `<<UPPERCUT>>` logback appender + regex converter) and v2 (`KarateV2TestRunner` → `<<UPPERCUT-V2>>` JSON event lines → `KarateV2EventProcessor`); see `docs/KARATE2.md` |
| `spellchecker/` | Spell checker integration |
| `steps/` | Step definition reference and completion |
| `steps/reference/` | Reference contributors for step definitions |
| `steps/search/` | Step search utilities |

## Code Conventions

### Language Mix

Production and unit-test code is **all Java** (`src/main/java`, `src/test/java`). Kotlin exists only in `src/integrationTest/kotlin`, because the IDE Starter/Driver SDK is Kotlin-first. New code should follow the existing language of the source set.

### Style Rules

- **Indentation:** 2 spaces (enforced by Checkstyle)
- **Max line length:** 120 characters for Java (Checkstyle enforced)
- **Braces:** Opening brace on same line (K&R / Google style)
- **Imports:** No star imports; sorted alphabetically with static imports first
- **Naming:**
  - Classes: `PascalCase`
  - Methods/variables: `camelCase` (minimum 2 chars for members)
  - Constants: handled per standard Java conventions
  - Implementation classes: `*Impl` suffix (e.g., `GherkinFileImpl`)
  - Visitor classes: `*Visitor` suffix
  - Utility classes: `*Util` suffix with private constructor
  - Abstract base classes: `Abstract*` prefix
- **Annotations:** Always use `@Override`, `@NotNull`, `@Nullable` (JetBrains annotations)
- **Lombok:** Used in settings classes (`@Getter`, `@Setter`)

### Checkstyle

Google Java Style enforced via Checkstyle 10.23.0. Configuration at `config/checkstyle/checkstyle.xml`.

**Suppressions** (`config/checkstyle/suppressions.xml`):
- All checks suppressed for `io/karatelabs/js/` (generated/external code)
- All checks suppressed for files matching `*Cucumber*` and `*Gherkin*` (legacy naming)
- Javadoc checks suppressed for all `.java` files
- `LocalVariableName` suppressed for `KarateTestRunner`
- `AbbreviationAsWordInName` suppressed for `BDDFrameworkType`

Inline suppression is available via comments:
```java
// CHECKSTYLE.OFF: CheckName
// CHECKSTYLE.ON: CheckName
// CHECKSTYLE.SUPPRESS: CheckName for +N lines
```

### Run configurations on folders

`KarateRunConfigurationProducer.shouldReplace` returns true for any folder that directly holds a `.feature` file. It has to: Gradle's test producer replaces every competing configuration when tests run through Gradle (the default), and the platform keeps only one side, so without replacing back a Karate folder run is never offered on Karate's own layout (features beside their JUnit runner class under `src/test/java`). Folders with no features are left to the build tool. `KarateRunConfigurationProducerTest` pins both.

### Refusing a run early

`KarateRunConfiguration.createJavaParameters` refuses, with a message ending in the troubleshooting page's URL, before launching a JVM that cannot work: no libraries at all (import not finished), a feature outside any module, a module with no `karate-*` jar (`classpathProblem`), or a version pin the classpath cannot satisfy (`checkVersionOverrideMatchesClasspath`). Each message has a section on `site/troubleshooting.md`; keep them in step.

### Architecture Patterns

1. **PSI (Program Structure Interface):** Core IntelliJ pattern. Interface + `Impl` class pairs for AST elements (e.g., `GherkinFile` / `GherkinFileImpl`)
2. **Visitor Pattern:** `GherkinElementVisitor` for inspections and annotations
3. **Extension Points:** Plugin defines `cucumberJvmExtensionPoint` and `karateJavascriptParsingExtensionPoint`
4. **Services:** `@Service` + `@State` for persistent application settings, accessed via `getInstance()` factory
5. **Cached Values:** `CachedValuesManager` for expensive computations in PSI
6. **Smart Pointers:** `SmartPsiElementPointer` for safe PSI element references

### The embedded JavaScript engine

`src/main/java/io/karatelabs/js/` is a hand-written port of the karate-js lexer and parser (it replaced an earlier JFlex-generated lexer; there is no `.jflex` source any more, and nothing regenerates these files). It is used only when the IntelliJ JavaScript plugin is absent, and only to lex and parse - `Interpreter.java` and friends are unused. Edit `Lexer.java`, `Parser.java`, `Token.java` and `Type.java` directly; `.claude/skills/karate-version-parity/` describes how to keep the token set in step with karate-js, and `KarateJsModernSyntaxTest` is the unit-level guard.

## Testing

### Test Suites

| Suite | Source Set | Engine | Command |
|-------|-----------|--------|---------|
| Unit tests | `src/test/` | JUnit 4 on the Vintage engine (both `BasePlatformTestCase` fixtures and plain `@Test` classes) | `./gradlew test` |
| Platform tests | `src/platformTest/` (source set declared, currently empty) | JUnit Vintage | `./gradlew platformTest` |
| Integration tests | `src/integrationTest/` | JUnit Jupiter + IDE Starter/Driver; `Karate2UITest` boots one IDE for its seven tests, `UppercutUITest` is `@Disabled` | `./gradlew integrationTest` |
| UI tests | Via IDE Starter/Driver | Manual workflow | `.github/workflows/run-ui-tests.yml` |

### Test Conventions

- Tests that need PSI or a project extend `BasePlatformTestCase` (or `ParsingTestCase`, `FormatterTestCase`); those use `test*` method names because the IntelliJ base classes are JUnit 3-style. Pure logic (`KarateJsModernSyntaxTest`, `KarateV2EventProcessorTest`, `KarateVersionDetectionTest`) is plain JUnit 4 with `@Test` and descriptive names
- Checkstyle runs on test sources too: `MethodName` pattern applies (suppress with `// CHECKSTYLE.SUPPRESS: MethodName for +1 lines` where a fixture base class forces a name), and `FormatterTestCase` derives the fixture file from the method name (`testJs_modern` → `js_modern.feature`)
- Mocking with Mockito (`@Mock`, `MockitoAnnotations.openMocks(this)`)
- Test data files (`.feature`) go in `src/test/testData/`
- Class names: `*Test` suffix
- Run-configuration producer tests build their context with `ConfigurationContext.createEmptyContextForLocation(new PsiLocation<>(project, module, element))` - `getFromContext(DataContext)` reads `PSI_ELEMENT_ARRAY`, not `PSI_ELEMENT`, and yields no location

### Running Tests Locally

Tests require a display environment. On headless Linux, use xvfb:
```bash
xvfb-run --auto-servernum ./gradlew check
```

On macOS/Windows, tests run natively.

## Plugin Extension Points

The plugin registers two custom extension points in `plugin.xml`:

1. **`cucumberJvmExtensionPoint`** - Interface: `CucumberJvmExtensionPoint`. For registering step definition providers.
2. **`uppercut.karateJavascriptParsingExtensionPoint`** - Interface: `KarateJavascriptParsingExtensionPoint`. For JavaScript parsing strategy.

JavaScript support is conditionally loaded via `plugin-withJs.xml` when the IntelliJ JavaScript plugin is available.

## CI/CD

### Build Pipeline (`.github/workflows/build.yml`)

Triggers on push to `main`/`ij-2024.2` and pull requests. Jobs:
1. **Build** - Compile and create plugin artifact
2. **Test** - Run `check` (excluding integration tests) with Kover code coverage
3. **Integration tests** - `Karate2UITest` under xvfb; only on push, not pull requests
4. **Verify** - IntelliJ Plugin Verifier against the newest release and newest EAP of the platform major (`pluginVerification` in `build.gradle.kts`; `-PpluginVerifierScope=all` for the full since-build range)
5. **Release Draft** - Auto-create GitHub release draft (push to main only); gated on all four jobs above, integration tests included

Caching: the IntelliJ platform and verifier IDEs are Gradle dependencies, cached by `setup-gradle`. The extracted copies (`caches/*/transforms`) are excluded everywhere and the verify job is read-only - the repository has a 10 GB cache budget, and caching the extracted IDEs per job blew past it and evicted everything.

### Docs site (`.github/workflows/docs.yml`)

Publishes `site/` to https://rankweis.github.io/uppercut/ on push to `main` when `site/**` changes, or by hand (`workflow_dispatch`). Requires Settings → Pages → Source: GitHub Actions on the repository.

### Release Pipeline (`.github/workflows/release.yml`)

Triggers on GitHub release publication. Signs and publishes to JetBrains Marketplace.

## Dependencies

Key dependencies (see `gradle/libs.versions.toml` and `gradle.properties`):

| Dependency | Version | Purpose |
|-----------|---------|---------|
| IntelliJ Platform | `platformVersion` in `gradle.properties` (Ultimate) | IDE platform APIs |
| Karate Core / JUnit 5 | `karateVersion` (1.5.1) | Bundled v1 fallback runner |
| Logback | `logbackVersion` | Logging |
| Kodein DI | `libs.versions.toml` | Dependency injection (integration tests) |
| JUnit Jupiter / Platform, JUnit 4, Mockito, Kotlin, GrammarKit | `gradle/libs.versions.toml` | Testing, integration-test language, build |

Versions are pinned in those two files and bumped by dependabot; the table names where to look rather than repeating numbers that go stale.

## User docs

`site/` is the user-facing documentation, published by `.github/workflows/docs.yml` and linked from the top of every version's Marketplace change notes (the link is prepended in `build.gradle.kts`, not written in CHANGELOG.md). Written for plugin users, not contributors: features in their terms, a support-status matrix per Karate version, a settings reference, and the messages the plugin emits. When a change alters what works, what a setting means, or a message a user sees, update `site/status.md`, `site/settings.md` or `site/troubleshooting.md` alongside the changelog entry. The IDE links to it too: `UppercutWebHelpProvider` maps help topic `com.rankweis.<page>` to `<site>/<page>` (the Settings page's **?** button uses it), and `KarateRunConfiguration.TROUBLESHOOTING` is appended to every message that refuses a run - so page names on the site are part of the plugin's contract.

## Changelog

Update `CHANGELOG.md` for every major change. Add entries under the `## [Unreleased]` section using [Keep a Changelog](https://keepachangelog.com) format (`### Added`, `### Fixed`, `### Modified`, etc.). The CI deploy action handles version bumping and release — do not manually create version entries.

## Common Tasks

### Adding a New Inspection

1. Create a class extending `GherkinInspection` (which extends `LocalInspectionTool`)
2. Implement `buildVisitor()` returning a `GherkinElementVisitor` with appropriate `visit*()` overrides
3. Register in `plugin.xml` under `<localInspection>` with language, bundle key, and group
4. Add bundle keys to `messages/MyBundle.properties`

### Adding a New PSI Element

1. Define the interface in `psi/`
2. Create `*Impl` class in `psi/impl/` extending appropriate base class
3. Register element type in the parser/element type definitions
4. Update the visitor if needed (`GherkinElementVisitor`)

### Modifying the JavaScript Lexer or Parser

1. Add the token to `Token.java` (mark it `keyword` if it is a reserved word) and teach `Lexer.java` to produce it - longest match first, and check `updateRegexAllowed`
2. Add the grammar rule to `Parser.java` (and a node type to `Type.java` if it needs one)
3. Map the token in `highlight/KarateJsHighlighter.java`
4. Cover it in `KarateJsModernSyntaxTest` and in a `.feature` fixture under `src/test/testData/` that `KarateJsHighlightTest` loads
