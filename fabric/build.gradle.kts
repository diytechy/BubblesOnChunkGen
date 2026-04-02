plugins {
    id("net.fabricmc.fabric-loom") version "1.15.5"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    maven {
        name = "FabricMC"
        url = uri("https://maven.fabricmc.net/")
    }
}

dependencies {
    minecraft("com.mojang:minecraft:26.1")
    // MC 26.1 is unobfuscated - no mappings needed
    implementation("net.fabricmc:fabric-loader:0.18.4")
    implementation("net.fabricmc.fabric-api:fabric-api:0.145.0+26.1")

    implementation(project(":common"))
    include(project(":common"))
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("BubblesOnChunkGen-Fabric")
}
