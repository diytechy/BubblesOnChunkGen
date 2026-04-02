plugins {
    id("com.gradleup.shadow") version "9.4.1"
    `maven-publish`
}

repositories {
    mavenCentral()
    maven {
        name = "Solo Studios"
        url = uri("https://maven.solo-studios.ca/releases")
    }
    maven {
        name = "Repsy-Terra"
        url = uri("https://repo.repsy.io/mvn/diytechy/terra")
    }
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    // Terra APIs (provided at runtime)
    compileOnly("com.dfsek.terra:manifest-addon-loader:1.0.0-BETA-ec788bf")
    compileOnly("com.dfsek.terra:base:7.0.0-BETA-ec788bf")
    compileOnly("org.slf4j:slf4j-api:2.0.9")

    // Platform APIs for listener registration (provided at runtime)
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    implementation(project(":common"))
}

tasks.shadowJar {
    archiveBaseName.set("BubblesOnChunkGen-TerraAddon")
    archiveClassifier.set("")
    relocate("com.bubbleschunkgen.common", "com.bubbleschunkgen.terra.internal.common")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.processResources {
    filesMatching("terra.addon.yml") {
        expand(mapOf("version" to project.version))
    }
}

publishing {
    repositories {
        mavenLocal()
        maven {
            name = "Repsy"
            url = uri("https://repo.repsy.io/mvn/diytechy/bubbleschunkgen")
            credentials {
                username = project.findProperty("repsy.user") as String? ?: System.getenv("REPSY_USERNAME")
                password = project.findProperty("repsy.key") as String? ?: System.getenv("REPSY_PASSWORD")
            }
        }
    }
    publications {
        create<MavenPublication>("repsy") {
            artifact(tasks.shadowJar)
            groupId = rootProject.group.toString()
            artifactId = "bubbleschunkgen-terra-addon"
        }
    }
}

tasks.named("build") {
    finalizedBy(tasks.named("publishToMavenLocal"))
}
