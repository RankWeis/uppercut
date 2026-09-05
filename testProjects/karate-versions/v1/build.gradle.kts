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

dependencies {
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
