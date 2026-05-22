plugins {
    id("net.neoforged.moddev") version "2.0.141"
    `maven-publish`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "FabricMC"
        url = uri("https://maven.fabricmc.net/")
    }
    maven {
        name = "Solo Studios"
        url = uri("https://maven.solo-studios.ca/releases")
    }
    maven {
        name = "Repsy-Terra"
        url = uri("https://repo.repsy.io/mvn/diytechy/terra")
    }
    // Minestom and Allay both publish via jitpack.
    maven {
        name = "JitPack"
        url = uri("https://jitpack.io")
    }
    maven {
        name = "Sonatype-Snapshots"
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }
}

// gson is pulled in transitively at several different versions by paper, neoforge,
// minestom, allay - all compileOnly, but Gradle still needs a single resolution.
configurations.all {
    resolutionStrategy {
        force("com.google.code.gson:gson:2.13.2")
    }
}

// moddev pulls in vanilla Minecraft + NeoForge for compilation against the
// vanilla MC classes used by the Fabric/Forge handlers and the FlowableFluidMixin.
// No dev runs are configured here - the JAR is meant to be dropped into Terra,
// not launched standalone.
neoForge {
    version = "26.1.1.0-beta"
}

dependencies {
    // Terra APIs (provided at runtime by Terra)
    compileOnly("com.dfsek.terra:manifest-addon-loader:1.0.0-BETA-ec788bf")
    compileOnly("com.dfsek.terra:base:7.0.0-BETA-ec788bf")
    compileOnly("org.slf4j:slf4j-api:2.0.9")

    // Paper (Bukkit handler)
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    // Fabric (handler + entry-point shim)
    compileOnly("net.fabricmc:fabric-loader:0.18.4")
    compileOnly("net.fabricmc.fabric-api:fabric-api:0.145.0+26.1")

    // Mixin runtime (used by FlowableFluidMixin)
    compileOnly("org.spongepowered:mixin:0.8.7")

    // Minestom (handler)
    compileOnly("net.minestom:minestom:2026.05.17c-26.1.1")

    // Allay (handler)
    compileOnly("org.allaymc.allay:api:0.20.0")

    implementation(project(":common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("BubblesOnChunkGen-TerraAddon")
    from(project(":common").sourceSets.main.get().output)
}

tasks.processResources {
    filesMatching(listOf(
        "terra.addon.yml",
        "fabric.mod.json",
        "META-INF/neoforge.mods.toml"
    )) {
        expand(mapOf("version" to project.version))
    }
}

publishing {
    repositories {
        mavenLocal()
        maven {
            name = "Repsy"
            url = uri("https://repo.repsy.io/mvn/diytechy/bubblesonchunkgen")
            credentials {
                username = project.findProperty("repsy.user") as String? ?: System.getenv("REPSY_USERNAME")
                password = project.findProperty("repsy.key") as String? ?: System.getenv("REPSY_PASSWORD")
            }
        }
    }
    publications {
        create<MavenPublication>("repsy") {
            artifact(tasks.named<Jar>("jar"))
            groupId = rootProject.group.toString()
            artifactId = "bubbleschunkgen-terra-addon"
        }
    }
}

tasks.named("build") {
    finalizedBy(tasks.named("publishToMavenLocal"))
}
