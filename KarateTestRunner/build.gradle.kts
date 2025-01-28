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

val instrumentedJars by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, JavaVersion.current().majorVersion.toInt())
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("instrumented-jar"))
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