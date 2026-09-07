plugins {
    id("java")
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        // Karate 1.x runs on 17; matched to the KarateTestRunner subproject's toolchain
        languageVersion = JavaLanguageVersion.of(17)
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

// The plugin's runner classes, so the debug harness can drive the real agent and adapter. See the
// v2 module's build for why this is a classes directory rather than a project dependency.
val runnerClasses = file("../../../KarateTestRunner/build/classes/java/main")

dependencies {
    testCompileOnly(files(runnerClasses))
    testRuntimeOnly(files(runnerClasses))
    // karate-junit5 is the v1 artifact; it was renamed to karate-junit6 in 2.x, which is what
    // Uppercut's classpath detection keys on.
    testImplementation("io.karatelabs:karate-junit5:1.5.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
    }
}

// The Karate 1 half of the phase 4 parity check in docs/DEBUGGER.md: same agent, same protocol,
// v1's RuntimeHook instead of v2's interceptor.
//   ./gradlew :KarateTestRunner:classes
//   ../../gradlew -p testProjects/karate-versions :v1:debugHarness
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
