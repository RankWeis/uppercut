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
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
    }
}

// Dumps every RunEvent the way the Uppercut runner will consume them:
//   ../gradlew -p karate2-spike eventProbe
tasks.register<JavaExec>("eventProbe") {
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "spike.EventProbe"
    // e.g. -PprobePath=classpath:spike2 to probe the failing feature's event payloads
    if (project.hasProperty("probePath")) {
        args(project.property("probePath").toString())
    }
    isIgnoreExitValue = true
}
