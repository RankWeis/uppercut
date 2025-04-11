fun properties(key: String) = providers.gradleProperty(key)
plugins {
    id("java")
    alias(libs.plugins.kotlin) // Kotlin support
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

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}