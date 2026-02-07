# CLAUDE.md - Uppercut Project Guide

## Project Overview

Uppercut is an IntelliJ IDEA plugin providing comprehensive IDE support for the **Karate testing framework**. It adds syntax highlighting, code completion, debugging, navigation, inspections, and run configurations for `.feature` (Karate/Gherkin) and `.featurejs` (KarateJs) files.

- **Plugin ID:** `com.rankweis`
- **Group:** `com.rankweis.uppercut`
- **License:** Apache 2.0
- **Repository:** https://github.com/rankweis/uppercut

## Build System

**Gradle 8.13** with Kotlin DSL (`build.gradle.kts`). Uses the Gradle wrapper (`./gradlew`).

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

# Run integration tests (requires prepareSandbox)
./gradlew integrationTest

# Launch IntelliJ with the plugin loaded for manual testing
./gradlew runIde

# Verify plugin compatibility
./gradlew verifyPlugin

# Generate JavaScript lexer from JFlex grammar
./gradlew generateLexer
```

Tests in CI run under `xvfb` for headless display:
```bash
xvfb-run --auto-servernum --server-args="-screen 0 1920x1080x24" ./gradlew check -x integrationTest
```

### JDK Requirements

- **Main project:** Java 21 (jvmToolchain)
- **KarateTestRunner subproject:** Java 17 (jvmToolchain)

### Gradle Properties

Key version properties are in `gradle.properties`:
- `pluginVersion` - Current plugin version (SemVer)
- `platformVersion` - Target IntelliJ platform version
- `pluginSinceBuild` / `pluginUntilBuild` - Compatibility range
- `karateVersion` - Karate framework version (1.5.1)

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
│   │   ├── kotlin/               # Main source (mostly Java despite the path)
│   │   │   ├── com/rankweis/uppercut/
│   │   │   │   ├── karate/       # Core plugin logic
│   │   │   │   ├── parser/       # AST parsing utilities
│   │   │   │   ├── settings/     # Plugin settings UI & persistence
│   │   │   │   └── util/         # General utilities
│   │   │   └── io/karatelabs/js/ # JavaScript language support + JFlex grammar
│   │   ├── java/io/karatelabs/js/# Generated lexer output (js.jflex source also here)
│   │   └── resources/
│   │       ├── META-INF/
│   │       │   ├── plugin.xml        # Main plugin descriptor
│   │       │   └── plugin-withJs.xml # JavaScript support (optional dependency)
│   │       ├── colorSchemes/     # Darcula and Default color schemes
│   │       ├── icons/            # Plugin icons (SVG)
│   │       ├── messages/         # Localization bundles
│   │       └── i18n.json         # Internationalization keywords
│   ├── test/                     # Unit tests
│   │   ├── kotlin/               # Test classes
│   │   └── testData/             # Test fixture files (.feature)
│   ├── integrationTest/          # IDE integration tests (IDE Starter + Driver)
│   └── platformTest/             # Platform compatibility tests
└── .github/workflows/
    ├── build.yml                 # CI: build, test, verify, draft release
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
| `psi/structure/` | File structure view |
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
| `run/` | Run configurations, execution, console |
| `spellchecker/` | Spell checker integration |
| `steps/` | Step definition reference and completion |
| `steps/reference/` | Reference contributors for step definitions |
| `steps/search/` | Step search utilities |

## Code Conventions

### Language Mix

The project is **~98% Java, ~2% Kotlin** despite source living under `src/main/kotlin/`. Kotlin is used only for `MyBundle.kt` (resource bundle) and `CucumberStepDefinitionCreationContext.kt` (data class). New code should follow the existing language of the file being modified.

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

### Architecture Patterns

1. **PSI (Program Structure Interface):** Core IntelliJ pattern. Interface + `Impl` class pairs for AST elements (e.g., `GherkinFile` / `GherkinFileImpl`)
2. **Visitor Pattern:** `GherkinElementVisitor` for inspections and annotations
3. **Extension Points:** Plugin defines `cucumberJvmExtensionPoint` and `karateJavascriptParsingExtensionPoint`
4. **Services:** `@Service` + `@State` for persistent application settings, accessed via `getInstance()` factory
5. **Cached Values:** `CachedValuesManager` for expensive computations in PSI
6. **Smart Pointers:** `SmartPsiElementPointer` for safe PSI element references

### Generated Code

The JavaScript lexer is generated from `src/main/java/io/karatelabs/js/js.jflex` via the GrammarKit plugin. Run `./gradlew generateLexer` to regenerate. Do not manually edit the generated `Lexer.java` in `src/main/java/io/karatelabs/js/`.

## Testing

### Test Suites

| Suite | Source Set | Engine | Command |
|-------|-----------|--------|---------|
| Unit tests | `src/test/` | JUnit 5 + Vintage | `./gradlew test` |
| Platform tests | `src/platformTest/` | JUnit Vintage | `./gradlew platformTest` |
| Integration tests | `src/integrationTest/` | JUnit Jupiter | `./gradlew integrationTest` |
| UI tests | Via IDE Starter/Driver | Manual workflow | `.github/workflows/run-ui-tests.yml` |

### Test Conventions

- Test classes extend `LightPlatformTestCase` or `ParsingTestCase` (IntelliJ test framework)
- Mocking with Mockito (`@Mock`, `MockitoAnnotations.openMocks(this)`)
- Test data files (`.feature`) go in `src/test/testData/`
- Test method names: `test*` prefix (JUnit 3/4 convention used by IntelliJ test base classes)
- Class names: `*Test` suffix

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
3. **Verify** - IntelliJ Plugin Verifier for API compatibility
4. **Release Draft** - Auto-create GitHub release draft (push to main only)

### Release Pipeline (`.github/workflows/release.yml`)

Triggers on GitHub release publication. Signs and publishes to JetBrains Marketplace.

## Dependencies

Key dependencies (see `gradle/libs.versions.toml` and `gradle.properties`):

| Dependency | Version | Purpose |
|-----------|---------|---------|
| IntelliJ Platform | 253.x (Ultimate) | IDE platform APIs |
| Karate Core | 1.5.1 | Karate framework support |
| Karate JUnit 5 | 1.5.1 | Karate test runner |
| Logback | 1.5.6 | Logging |
| Kodein DI | 7.26.1 | Dependency injection (integration tests) |
| JUnit 5 | 5.13.4 | Testing |
| Mockito | 5.19.0 | Mocking |
| GrammarKit | 2022.3.2.2 | Lexer/parser generation |
| Kotlin | 2.2.10 | Kotlin support |

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

### Modifying the JavaScript Lexer

1. Edit `src/main/java/io/karatelabs/js/js.jflex`
2. Run `./gradlew generateLexer`
3. The generated `Lexer.java` will be placed in `src/main/java/io/karatelabs/js/`
