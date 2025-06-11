import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease

fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)

plugins {
    id("java") // Java support
    alias(libs.plugins.gradleIntelliJPlugin) // Gradle IntelliJ Plugin
    alias(libs.plugins.grammarkit)
    alias(libs.plugins.lombok)
    alias(libs.plugins.kotlin) // Kotlin support
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
    alias(libs.plugins.qodana) // Gradle Qodana Plugin
    alias(libs.plugins.kover) // Gradle Kover Plugin
    checkstyle
}

checkstyle {
    toolVersion = "10.23.0"
}

// Configure project's dependencies
repositories {
    mavenCentral()
    maven("https://www.jetbrains.com/intellij-repository/releases")
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    maven("https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public/")
    maven("https://download.jetbrains.com/teamcity-repository")
    intellijPlatform {
        defaultRepositories()
    }
}

configure<SourceSetContainer> {
    named("main") {
        java.srcDir("src/main/kotlin")
    }
    named("test") {
        java.srcDir("src/test/kotlin")
    }
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
    create("platformTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    }
}

val integrationTestImplementation by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}
val platformTestImplementation by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}

dependencies {
    intellijPlatform {
        val version = properties("platformVersion")

        intellijIdeaUltimate(version, useInstaller = false)
        // Plugin Dependencies. Uses `platformBundledPlugins` property from the gradle.properties file for bundled IntelliJ Platform plugins.
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })

        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file for plugin from JetBrains Marketplace.
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })

        jetbrainsRuntime()
//        testFramework(TestFrameworkType.Platform)
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
    testImplementation("com.jetbrains.intellij.platform:test-framework:251.26094.141")

    testImplementation(libs.junit) // JUnit 4 support
    testImplementation("org.junit.vintage:junit-vintage-engine") // JUnit 4 compatibility engine for JUnit 5

    // --- IntelliJ Testing Tools (IDE Starter + Driver) ---
    integrationTestImplementation(libs.starterSquashed)
    integrationTestImplementation(libs.starterJunit5)
    integrationTestImplementation(libs.starterDriver)
    integrationTestImplementation(libs.driverClient)
    integrationTestImplementation(libs.driverSdk)
    integrationTestImplementation(libs.driverModel)
    integrationTestImplementation(libs.metricsSquashed)
    integrationTestImplementation(libs.metricsCollector)
    integrationTestImplementation(libs.ijPerformance)
    integrationTestImplementation(libs.ijCommon)

    // --- Mocking and Coroutines Testing ---
    testImplementation(libs.mockito) // Mockito for mocking in tests
    integrationTestImplementation(libs.junitJupiter)
    integrationTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2") // Kotlin Coroutines testing library
    integrationTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2") // Kotlin Coroutines testing library
    integrationTestImplementation(libs.kodein)

//    integrationTestImplementation("com.jetbrains.intellij.tools:ide-performance-testing-commands:LATEST-EAP-SNAPSHOT")
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
    useJUnitPlatform {
        excludeEngines("junit-vintage")
        includeEngines("junit-jupiter")
    }
    dependsOn(tasks.prepareSandbox)
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

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(21)
}

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog
// Configure Gradle IntelliJ Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html

intellijPlatform {
    pluginConfiguration {
        version = properties("pluginVersion")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
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
        // Get the latest available change notes from the changelog file
        changeNotes = properties("pluginVersion").map { pluginVersion ->
            with(changelog) {
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
            ide(IntelliJPlatformType.IntellijIdeaUltimate, properties("platformVersion").get(), useInstaller = false)
        }
    }
}

grammarKit {
    tasks {
        generateLexer {
            sourceFile.set(file("src/main/java/io/karatelabs/js/js.jflex"))
            targetOutputDir.set(file("src/main/java/io/karatelabs/js"))
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

// Configure Gradle Kover Plugin - read more: https://github.com/Kotlin/kotlinx-kover#configuration
kover {
    reports {
        total {
            xml {
                onCheck = true
            }
        }
    }
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

tasks {
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
        types = listOf(IntelliJPlatformType.IntellijIdeaUltimate)
        untilBuild = "251.*"
    }
}