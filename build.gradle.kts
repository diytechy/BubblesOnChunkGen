plugins {
    java
}

defaultTasks("clean", "build", "publishToMavenLocal")

group = "com.bubbleschunkgen"

subprojects {
    apply(plugin = "java")

    group = rootProject.group
    version = rootProject.version

    java {
        toolchain {
            // Java 25: the whole 26.1 target stack (Paper 26.1, Fabric/NeoForge 26.1, Minestom, Allay)
            // runs on Java 25, and Paper 26.1's API is published as a Java-25-only library, so the
            // bukkit module cannot compile against it on Java 21. (terra-addon already overrides to 25.)
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    repositories {
        mavenCentral()
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}
