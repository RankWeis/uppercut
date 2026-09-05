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

dependencies {
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
