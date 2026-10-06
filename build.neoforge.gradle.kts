plugins {
    id("dev.architectury.loom")
    id("webshoplink-platform")
}

loom {
    silentMojangMappingsLicense()
}

val rinkuWrapper = "maven.modrinth:rinku:${property("deps.rinku")}-${property("deps.minecraft")}-neoforge"

// Extracted at configuration time: Loom resolves mod dependencies while the project is
// evaluated, before any task could produce the file.
val rinkuModJar: File = run {
    val wrapper = configurations.detachedConfiguration(dependencies.create(rinkuWrapper))
        .apply { isTransitive = false }
        .singleFile
    val target = layout.buildDirectory.file("rinku/${wrapper.nameWithoutExtension}-mod.jar").get().asFile
    if (!target.exists()) {
        val nested = zipTree(wrapper).matching { include("META-INF/jarjar/*-mod.jar") }.singleFile
        target.parentFile.mkdirs()
        nested.copyTo(target, overwrite = true)
    }
    target
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
    // the client screen classes compile against it. Rinku (formerly MCEF) provides the in-game
    // Chromium browser. The NeoForge jar on Modrinth is only a JarJar wrapper around the real
    // mod, so compile against the nested jar and run the dev client with the wrapper.
    "modCompileOnly"(files(rinkuModJar))
    "modLocalRuntime"(rinkuWrapper)
}

loom {
    runs {
        named("client") { runDir("run/client") }
        named("server") { runDir("run/server") }
    }
}
