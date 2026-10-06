plugins {
    id("dev.architectury.loom")
    id("webshoplink-platform")
}

loom {
    silentMojangMappingsLicense()
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
    "forge"("net.minecraftforge:forge:${property("deps.forge")}")
    // Client-only at runtime (the dedicated server never classloads the browser code), but
    // the client screen classes compile against it. A mod jar, so Loom remaps it.
    // Rinku (formerly MCEF) provides the in-game Chromium browser; published on Modrinth's Maven.
    "modImplementation"("maven.modrinth:rinku:${property("deps.rinku")}-${property("deps.minecraft")}-forge")
}

loom {
    runs {
        named("client") { runDir("run/client") }
        named("server") { runDir("run/server") }
    }
}
