plugins {
    id("net.neoforged.moddev") version "2.0.141"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

neoForge {
    version = "26.1.1.0-beta"

    runs {
        create("server") {
            server()
        }
    }

    mods {
        create("bubbleschunkgen") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    implementation(project(":common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("BubblesOnChunkGen-Forge")
    from(project(":common").sourceSets.main.get().output)
}
