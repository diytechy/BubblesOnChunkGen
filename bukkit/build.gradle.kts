plugins {
    id("com.gradleup.shadow") version "9.4.1"
}

repositories {
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    implementation(project(":common"))
}

tasks.shadowJar {
    archiveBaseName.set("BubblesOnChunkGen-Bukkit")
    archiveClassifier.set("")
    relocate("com.bubbleschunkgen.common", "com.bubbleschunkgen.bukkit.internal.common")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
