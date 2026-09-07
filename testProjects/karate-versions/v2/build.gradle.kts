plugins {
    id("java")
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        // Karate 2.x requires Java 21+ (virtual threads)
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// Standard Karate-Gradle setup: feature files and karate-config.js live next to the tests
// under src/test/java and must be copied to the test classpath.
sourceSets {
    test {
        resources {
            srcDir(file("src/test/java"))
            exclude("**/*.java")
        }
    }
}

// The plugin's runner classes, so the debug harness can drive the real agent and adapter. Compiled
// classes rather than a jar: this project is deliberately outside the root build (settings.gradle.kts
// does not include it), so there is no project() dependency to declare and nothing to keep a jar
// name in step with. Build them first with `./gradlew :KarateTestRunner:classes`.
val runnerClasses = file("../../../KarateTestRunner/build/classes/java/main")

dependencies {
    testCompileOnly(files(runnerClasses))
    testRuntimeOnly(files(runnerClasses))
    testImplementation("io.karatelabs:karate-junit6:2.1.1")
    // karate-junit6 declares junit-jupiter as provided; the migration guide asks for 5.10.1+
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Karate 2 depends on slf4j-api only (logback is bundled in its fat jar, not the library jar),
    // so a project brings its own provider or gets SLF4J's NOP logger and a silent console. This is
    // Karate 2.1.1's own logback pin; it also keeps the fixture representative of a real v2 project.
    testRuntimeOnly("ch.qos.logback:logback-classic:1.5.38")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
    }
}

// Dumps every RunEvent the way the Uppercut runner will consume them:
//   ../../gradlew -p testProjects/karate-versions :v2:eventProbe
tasks.register<JavaExec>("eventProbe") {
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "sample.EventProbe"
    // e.g. -PprobePath=classpath:broken to probe the failing feature's event payloads
    if (project.hasProperty("probePath")) {
        args(project.property("probePath").toString())
    }
    isIgnoreExitValue = true
}

// Phase 0 spike for docs/DEBUGGER.md: pauses a real run inside Karate 2's debug interceptor.
//   ../../gradlew -p testProjects/karate-versions :v2:debugProbe
//   ../../gradlew -p testProjects/karate-versions :v2:debugProbe -PpauseSeconds=45 -PpauseLine=4
tasks.register<JavaExec>("debugProbe") {
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "sample.DebugProbe"
    for (name in listOf("probePath", "pauseLine", "pauseSeconds", "parallelism")) {
        if (project.hasProperty(name)) {
            systemProperty(name, project.property(name).toString())
        }
    }
    isIgnoreExitValue = true
}

// End-to-end phase 1 check for docs/DEBUGGER.md: fake IDE + real agent + real Karate suite.
//   ./gradlew :KarateTestRunner:classes
//   ../../gradlew -p testProjects/karate-versions :v2:debugHarness
tasks.register<JavaExec>("debugHarness") {
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "sample.DebugHarness"
    for (name in listOf("pauseLine", "pauseSeconds", "moveTo")) {
        if (project.hasProperty(name)) {
            systemProperty(name, project.property(name).toString())
        }
    }
    doFirst {
        require(runnerClasses.isDirectory) {
            "Run ./gradlew :KarateTestRunner:classes in the root project first - $runnerClasses is missing"
        }
    }
    isIgnoreExitValue = true
}
