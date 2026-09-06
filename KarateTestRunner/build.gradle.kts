fun properties(key: String) = providers.gradleProperty(key)
plugins {
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    // Pinned non-transitively, as the root project pins them: this module compiles against
    // FeatureRuntime and ScenarioCall and reflects on RuntimeHook, all of which come from
    // karate-core, and takes none of karate's own tree. Resolving it transitively pulled armeria,
    // netty, thymeleaf and the rest onto a compileOnly classpath that ships nothing, which is what
    // the submitted dependency graph was reporting to Dependabot.
    compileOnly("io.karatelabs:karate-junit5:${properties("karateVersion").get()}") {
        isTransitive = false
    }
    compileOnly("io.karatelabs:karate-core:${properties("karateVersion").get()}") {
        isTransitive = false
    }
    implementation("ch.qos.logback:logback-classic:${properties("logbackVersion").get()}")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.test {
    useJUnitPlatform()
}
