pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.minecraftforge.net/") { name = "MinecraftForge" }
        maven("https://maven.architectury.dev/") { name = "Architectury" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
    includeBuild("build-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("dev.kikugie.stonecutter") version "0.9.8"
}

rootProject.name = "webshoplink"

stonecutter {
    create(rootProject) {
        fun match(mc: String, vararg loaders: String) = loaders.forEach {
            version("$mc-$it", mc).buildscript = "build.$it.gradle.kts"
        }

        // Client + server mod: a node is the loader's entry point, config registration, event
        // wiring and network transport (see src/main/java/info/rusty/webshoplink/<loader>).
        match("1.20.1", "forge")
        match("1.21.1", "neoforge")

        vcsVersion = "1.21.1-neoforge"
    }
}
