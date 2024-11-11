import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML

fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)

plugins {
    id("java") // Java support
    alias(libs.plugins.kotlin) // Kotlin support
    alias(libs.plugins.gradleIntelliJPlugin) // Gradle IntelliJ Plugin
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
    alias(libs.plugins.qodana) // Gradle Qodana Plugin
    alias(libs.plugins.kover) // Gradle Kover Plugin
}


configure<SourceSetContainer> {
    named("main") {
        java.srcDir("src/main/kotlin")
        java.srcDir("src/test/kotlin")
    }
}
group = properties("pluginGroup").get()
version = properties("pluginVersion").get()

// Configure project's dependencies
repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
        jetbrainsIdeInstallers()
        jetbrainsRuntime()
    }
}

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog
dependencies {
    compileOnly("io.karatelabs:karate-junit5:" + properties("karateVersion").get())
    implementation("ch.qos.logback:logback-classic:" + properties("logbackVersion").get())
}

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(17)
}

// Configure Gradle IntelliJ Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
dependencies {

    intellijPlatform {
//        pluginName = properties("pluginName")
        val version = properties("platformVersion")
//        val type = properties("platformType")

        intellijIdeaUltimate(version, useInstaller = false)
        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
        bundledPlugins(properties("platformPlugins").map {
            it.split(',')
                .map(String::trim).filter(String::isNotEmpty)
        })
        pluginVerifier()
        zipSigner()
        instrumentationTools()

    }
}

intellijPlatform {
    pluginConfiguration {
        version = properties("platformVersion").get()
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild").map { it.ifBlank { null } }
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
            .map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }) }
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
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
    jar {
        archiveBaseName.set(properties("pluginName"))
        archiveClassifier.set("")
        archiveVersion.set(properties("pluginVersion"))
    }
    wrapper {
        gradleVersion = properties("gradleVersion").get()
    }

    withType(JavaExec::class).configureEach {
        if (name.endsWith("main()")) {
            notCompatibleWithConfigurationCache("JavaExec created by IntelliJ")
        }
    }

    patchPluginXml {
        version = properties("pluginVersion")
        sinceBuild = properties("pluginSinceBuild")
        untilBuild = properties("pluginUntilBuild")


    }

    runIde {
        autoReload = true
    }
    // Configure UI tests plugin
    // Read more: https://github.com/JetBrains/intellij-ui-test-robot

    signPlugin {
        certificateChain = environment("CERTIFICATE_CHAIN")
        privateKey = environment("PRIVATE_KEY")
        password = environment("PRIVATE_KEY_PASSWORD")
    }

    publishPlugin {
        dependsOn("patchChangelog")
        token = environment("PUBLISH_TOKEN")
        // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels = properties("pluginVersion").map {
            listOf(
                it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" })
        }
    }
}
