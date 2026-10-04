# WebshopLink

A Minecraft Forge / NeoForge mod that lets players browse and use an external web shop **inside an in-game browser window**, then have the purchased items applied straight to their inventory.

> Throughout the docs this project is referred to as a "mod", even where a plugin version is discussed — the functionality is the same regardless.

> **WebshopLink only handles the in-game side.** The actual shop (catalog, pricing, what items a player gets) lives in an external web service that you run and that speaks WebshopLink's HTTP protocol. See the [Wiki](WIKI.md) for the contract, and [`shop-demo`](https://github.com/frs-projects/shop-demo) for a working reference implementation.

## How it works

1. A player runs `/shop <type>` in game.
2. WebshopLink (server side) sends the player's current inventory to your shop API and gets back a session URL.
3. The URL opens in a **full-screen in-game browser** (rendered with [MCEF](https://github.com/CinemaMod/mcef)) floating over the game world, with a small button bar at the bottom.
4. The player builds their cart on the web page, then clicks **Finish Trade**.
5. WebshopLink fetches the resulting inventory from your API, verifies the player's inventory hasn't changed since the session started, and applies it. Closing the window (or **Cancel** / ESC) cancels the session.

```
Player ──/shop──▶ WebshopLink (server) ──HTTP──▶ Your shop API
                        │                              │
                        └──── opens in-game browser ───┘
                                     │
                        Finish Trade ▼
                  inventory applied to player
```

## Features

- **In-game shopping** — the web shop renders in a Chromium browser overlaid on the game; no alt-tabbing to an external browser.
- **Flash-free opening** — one browser is kept alive for the whole session and reused, and the shop is only drawn once the page has painted, so opening a shop never flashes a blank window over the world. Pages can signal readiness explicitly; see [Writing the shop page](WIKI.md#writing-the-shop-page).
- **Inventory verification** — purchases are rejected if the player's inventory changed between starting the session and confirming, preventing duping/race exploits.
- **Two-factor session code** — an anti-tampering code is exchanged with your API on every call so sessions can't be forged from outside.
- **API key support** — every request to your shop API carries a configurable `X-Webshop-Api-Key` header.
- **Main inventory + Ender Chest** support, with full NBT serialization for complex items.
- **Balance overlay (optional)** — a small HUD box in a configurable screen corner renders your shop's balance page, so players always see their balance. On by default; the server pushes its `balanceUrl` to clients, with position, size, and a URL fallback in client-side config. See [Balance overlay](WIKI.md#balance-overlay).
- **Optional networking** — the mod registers its network channel as optional, so vanilla clients (or clients without the mod) can still connect to the server; they just can't open the browser.

## Requirements

| Side | Required |
|---|---|
| **Server** | WebshopLink mod |
| **Client** | WebshopLink mod **+** [MCEF](https://www.curseforge.com/minecraft/mc-mods/mcef) |
| **Elsewhere** | An external web shop implementing the [WebshopLink API](WIKI.md) |

- Minecraft **1.20.1** with Forge **47.x**, or Minecraft **1.21.1** with NeoForge **21.1.x**
- MCEF **2.2.0+** for the same loader (client only — the dedicated server never loads it)

Each loader has its own jar: `webshoplink-<version>+1.20.1-forge.jar` or
`webshoplink-<version>+1.21.1-neoforge.jar`. On 1.21.1 the `nbt` field of an item carries the
item's data components instead of legacy NBT (see [NBT notes](WIKI.md#nbt-notes)).

Players whose client is missing the mod/MCEF will be told to install them when they run `/shop`.

## Commands

| Command | Description |
|---|---|
| `/shop <type> [label]` | Cancels any unfinished session, then starts a new shop session of the given `type` (the `shopSlug` sent to your API). Optional `label` is shown in the confirmation UI. Opens the in-game browser. |
| `/shopFinish <uuid>` | Manual backstop for checkout (the browser's **Finish Trade** button does this automatically). Fetches the new inventory and offers a clickable confirm link in chat. |
| `/confirmFinish <uuid>` | Applies the checked-out inventory changes. |
| `/shopCancel <uuid>` | Cancels the session both locally and with your API. |

Normal play only needs `/shop` — the browser's buttons drive the rest. The other commands exist as fallbacks and for clients without the in-game browser.

By default anyone can run `/shop`. To restrict it to operators/command blocks (so shops only open at specific locations) set `shopCommandPermissionLevel` in the config — see [Locking down `/shop`](WIKI.md#locking-down-shop).

## Installation

**Server**
1. Download the latest WebshopLink release JAR.
2. Drop it into the server's `mods/` folder.
3. Start the server once to generate `config/webshoplink-common.toml`, then point it at your shop API (see the [Wiki](WIKI.md#configuration)).
4. Restart.

**Client (each player)**
1. Install [MCEF](https://www.curseforge.com/minecraft/mc-mods/mcef).
2. Install the WebshopLink mod.

## Building

The build is the same framework as the other FRS-Projects mods:
[Stonecutter](https://stonecutter.kikugie.dev/) with Architectury Loom, one source tree, and one
node per Minecraft version and loader. Gradle 9.7 runs its daemon on **Java 25**, so a JDK 25
must be installed where Gradle can find it; each node still compiles to its own Java level.

| Task | What it does |
|---|---|
| `./gradlew buildAll` | Builds every node |
| `./gradlew checkAll` | Runs every node's checks, including `verifyModMetadata` |
| `./gradlew collectJars` | Copies every node's jar into `build/libs` |
| `./gradlew :1.20.1-forge:runClient` | Dev client for one node (`versions/<node>/run/client`) |
| `./gradlew "Set active project to 1.20.1-forge"` | Switches the working tree to another node |

Shared code lives in `src/main/java/info/rusty/webshoplink` (`client/` is client-only); the
loader entry points, config registration, network transport and client event wiring live in
`forge/` and `neoforge/` (gated with `//? if forge` / `//? if neoforge`). Minecraft API
differences are `//? if` branches in the shared files, the main one being `StackNbt` (item NBT
versus data components). The config classes are written against NeoForge's `ModConfigSpec`;
Stonecutter renames it to `ForgeConfigSpec` for the Forge node (`stonecutter.gradle.kts`). The
working tree is the `1.21.1-neoforge` node, so the Forge files are committed commented out.
Pushing a `v*` tag that matches `mod.version` publishes a GitHub Release with every node's jar.

## Documentation

- **[Wiki](WIKI.md)** — server-operator setup: full config reference, the HTTP API your shop must implement, how to write the shop page, inventory data format, and the security model.
- **[`shop-demo`](https://github.com/frs-projects/shop-demo)** — a small, fully-commented reference shop (Bun + Tailwind) you can run locally to try the mod or learn the contract.

## License

See [LICENSE](LICENSE).
