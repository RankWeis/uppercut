import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease

fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)

plugins {
    id("java") // Java support
    alias(libs.plugins.kotlinJvm) // Kotlin support (integration tests use the IDE Starter/Driver Kotlin DSL)
    alias(libs.plugins.gradleIntelliJPlugin) // Gradle IntelliJ Plugin
    alias(libs.plugins.grammarkit)
    alias(libs.plugins.lombok)
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
    alias(libs.plugins.qodana) // Gradle Qodana Plugin
    jacoco
    checkstyle
}

checkstyle {
    toolVersion = "10.23.0"
}

// Configure project's dependencies
repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

configure<SourceSetContainer> {
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
    create("platformTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    }
}

// `by getting { }` with a configuration block is deprecated as of Gradle 9.7 (removed in 10);
// the delegates stay because the dependencies block refers to these configurations by name
val integrationTestImplementation by configurations.getting
integrationTestImplementation.extendsFrom(configurations.testImplementation.get())
val platformTestImplementation by configurations.getting
platformTestImplementation.extendsFrom(configurations.testImplementation.get())

dependencies {
    intellijPlatform {
        intellijIdea(properties("platformVersion")) {
            type = IntelliJPlatformType.IntellijIdeaUltimate
        }
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })
        jetbrainsRuntime()
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Starter, configurationName = "integrationTestImplementation")
    }

    // Plugin Module
    implementation(project(":KarateTestRunner")) // Project-specific support for Karate tests

    // --- Core Dependencies ---
    implementation("ch.qos.logback:logback-classic:${properties("logbackVersion").get()}") // Logging framework

    // --- JUnit Testing Framework ---
    testImplementation(libs.junit5api) // JUnit 5 API
    testImplementation(libs.junit5Params) // JUnit 5 API
    testImplementation(libs.junitPlatformLauncher) // JUnit Platform launcher
    integrationTestImplementation(libs.junit5engine) // JUnit 5 runtime engine

    testImplementation(libs.junit) // JUnit 4 support
    testImplementation("org.junit.vintage:junit-vintage-engine") // JUnit 4 compatibility engine for JUnit 5

    // --- Mocking ---
    testImplementation(libs.mockito) // Mockito for mocking in tests
    integrationTestImplementation(libs.junitJupiter)
    // IDE Starter/Driver integration tests are written in Kotlin (see docs: integration-tests-intro)
    integrationTestImplementation(kotlin("stdlib"))
    integrationTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.1")
    integrationTestImplementation("org.kodein.di:kodein-di-jvm:7.26.1")
    // Starter's TeamCityReporter needs it at runtime; not pulled in transitively (intellij-dependencies repo)
    "integrationTestRuntimeOnly"("org.jetbrains.teamcity:serviceMessages:2024.07")

    implementation("io.karatelabs:karate-junit5:${properties("karateVersion").get()}") {
        isTransitive = false
    }
    implementation("io.karatelabs:karate-core:${properties("karateVersion").get()}") {
        isTransitive = false
    }
}

val integrationTests = tasks.register<Test>("integrationTest") {
    val integrationTestSourceSet = sourceSets.getByName("integrationTest")
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    systemProperty("path.to.build.plugin", tasks.prepareSandbox.get().pluginDirectory.get().asFile)
    // the IDE the Starter boots: the same major the plugin is built against, not whatever is newest
    systemProperty("platform.version", providers.gradleProperty("platformVersion").get())
    // IntelliJ's MultiRoutingFileSystem (pulled in by the Starter's JDK/IDE extraction) implements
    // sun.nio.fs internals, which are not exported to the unnamed module on modern JDKs.
    jvmArgs(
        "--add-exports=java.base/sun.nio.fs=ALL-UNNAMED",
        "--add-opens=java.base/sun.nio.fs=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED",
    )
    useJUnitPlatform {
        excludeEngines("junit-vintage")
        includeEngines("junit-jupiter")
    }
    dependsOn(tasks.prepareSandbox)
}

tasks.test {
    systemProperty("idea.home.path", intellijPlatform.platformPath.toString())
}

tasks.register<Test>("platformTest") {
    val integrationTestSourceSet = sourceSets.getByName("platformTest")
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    useJUnitPlatform {
        includeEngines("junit-vintage")
    }
}

group = properties("pluginGroup").get()
version = properties("pluginVersion").get()

abstract class InstrumentedJarsRule : AttributeCompatibilityRule<LibraryElements> {
    override fun execute(details: CompatibilityCheckDetails<LibraryElements>) = details.run {
        if (consumerValue?.name == "instrumented-jar" && producerValue?.name == "jar") {
            compatible()
        }
    }
}

kotlin {
    jvmToolchain(25)
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog
// Configure Gradle IntelliJ Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html

intellijPlatform {
    pluginConfiguration {
        version = properties("pluginVersion")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            // No upper bound on purpose: the plugin stays installable on newer IDE releases instead of
            // waiting for a compatibility release. verifyPlugin (recommended IDEs) and the Marketplace
            // verifier are what catch a breaking platform change. The Gradle plugin would otherwise
            // default this to "<platform major>.*", so it has to be nulled explicitly.
            untilBuild = provider { null }
        }
        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        description =
            providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
                val start = "<!-- Plugin description -->"
                val end = "<!-- Plugin description end -->"

                with(it.lines()) {
                    if (!containsAll(listOf(start, end))) {
                        throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                    }
                    subList(indexOf(start) + 1, indexOf(end)).joinToString("\n")
                        .let(::markdownToHTML)
                }
            }

        val changelog = project.changelog // local variable for configuration cache compatibility
        // Get the latest available change notes from the changelog file. The docs link goes first in
        // every version's notes here, rather than as a line in CHANGELOG.md that someone has to
        // remember to keep.
        changeNotes = properties("pluginVersion").map { pluginVersion ->
            val docsLink = "<p>What works, what's in progress, and what every setting means: " +
                "<a href=\"https://rankweis.github.io/uppercut/\">rankweis.github.io/uppercut</a></p>\n"
            docsLink + with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }
    }
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels = providers.gradleProperty("pluginVersion")
            .map { listOf(it.substringAfter("-ch", "").substringBefore('.').ifEmpty { "default" }) }
    }
    pluginVerification {
        ides {
            // Every push verifies against the newest release and the newest EAP of the platform major
            // the plugin is built on - the two builds that can break it after it compiled - and
            // nothing older: with no until-build, `recommended()` would otherwise add one ~1.2 GB IDE
            // per major release from since-build up, forever. The full support range is one flag away
            // for a release check by hand:
            //
            //   ./gradlew verifyPlugin -PpluginVerifierScope=all
            //
            if (properties("pluginVerifierScope").orNull == "all") {
                recommended()
            } else {
                select {
                    types = listOf(IntelliJPlatformType.IntellijIdeaUltimate)
                    channels = listOf(ProductRelease.Channel.RELEASE, ProductRelease.Channel.EAP)
                    // "2026.2" -> "262": the platform's own major build, so the list follows platformVersion
                    sinceBuild = properties("platformVersion").map { v ->
                        v.split('.').let { (year, minor) -> year.takeLast(2) + minor }
                    }
                }
            }
        }
    }
}

tasks.withType<Copy> {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    groups.empty()
    repositoryUrl = properties("pluginRepositoryUrl")
}

tasks.jacocoTestReport {
    reports {
        xml.required = true
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestReport)
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

tasks {
    // pluginName is the Marketplace display name and carries spaces; keep the distribution
    // archive on the short, URL-friendly name so release asset URLs stay stable.
    buildPlugin {
        archiveBaseName = "uppercut"
    }

    wrapper {
        gradleVersion = properties("gradleVersion").get()
    }

    withType(JavaExec::class).configureEach {
        if (name.endsWith("main()")) {
            notCompatibleWithConfigurationCache("JavaExec created by IntelliJ")
        }
    }

    publishPlugin {
        dependsOn("patchChangelog")
    }

    printProductsReleases {
        channels = listOf(ProductRelease.Channel.EAP)
        types = listOf(IntelliJPlatformType.IntellijIdea)
    }
}