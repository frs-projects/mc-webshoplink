plugins {
    id("frs.forge")
}

dependencies {
    // Client-only at runtime (the dedicated server never classloads the browser code), but
    // the client screen classes compile against it. A mod jar, so Loom remaps it.
    // Rinku (formerly MCEF) provides the in-game Chromium browser; published on Modrinth's Maven.
    "modImplementation"("maven.modrinth:rinku:${property("deps.rinku")}-${property("deps.minecraft")}-forge")
}
