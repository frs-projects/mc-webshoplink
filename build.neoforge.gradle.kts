plugins {
    id("dev.architectury.loom")
    id("webshoplink-platform")
}

loom {
    silentMojangMappingsLicense()
}

repositories {
    // MCEF (Minecraft Chromium Embedded Framework) - provides the in-game browser.
    maven("https://keksuccino.github.io/maven/") { name = "Keksuccino" }
}

dependencies {
    minecraft("com.mojang:minecraft:${property("deps.minecraft")}")
    // Minecraft ships unobfuscated from the 26.x line on, so Mojang no longer
    // publishes mapping files and there is nothing to remap against.
    if (property("deps.mappings") == "unobfuscated") {
        mappings(loom.layered { })
    } else {
        mappings(loom.officialMojangMappings())
    }
    "neoForge"("net.neoforged:neoforge:${property("deps.neoforge")}")
    // Client-only at runtime (the dedicated server never classloads the browser code), but
    // the client screen classes compile against it. A mod jar, so Loom remaps it.
    "modImplementation"("de.keksuccino:mcef-neoforge:${property("deps.mcef")}-${property("deps.minecraft")}")
}

loom {
    runs {
        named("client") { runDir("run/client") }
        named("server") { runDir("run/server") }
    }
}
