fun properties(key: String) = providers.gradleProperty(key)
plugins {
    id("java")
}

repositories {
    mavenCentral()
}

configure<SourceSetContainer> {
    named("main") {
        java.srcDirs("src/main/kotlin")
    }
}


dependencies {
    compileOnly("io.karatelabs:karate-junit5:${properties("karateVersion").get()}")
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
