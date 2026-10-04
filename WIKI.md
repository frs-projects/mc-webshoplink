# WebshopLink — Server Operator & Integrator Guide

This page covers everything a server operator needs beyond dropping the JAR in `mods/`: configuring the mod and **implementing the external web shop** it talks to. For the player-facing overview, see the [README](README.md).

WebshopLink itself never decides prices, stock, or what a player receives. It only:

- snapshots the player's inventory and sends it to your shop API,
- opens your shop's URL in an in-game browser,
- fetches the resulting inventory from your API and applies it (after verifying nothing changed in the meantime),
- optionally renders your shop's balance page as a small always-on HUD overlay (see [Balance overlay](#balance-overlay)).

Everything shop-specific lives in **your** web service. A complete, commented reference implementation (Bun + Tailwind) is in [`demo/`](demo/) — run it locally to see the whole protocol end to end.

---

## Configuration

After the first server start, the config lives at `config/webshoplink-common.toml`:

```toml
# Base URL for the shop API
apiBaseUrl = "http://localhost:8080/api/shop"

# API key sent as the X-Webshop-Api-Key header on every request to the shop API
apiKey = ""

# Endpoint for initiating shop processes
shopEndpoint = "/initiate"

# Endpoint for checking out shop processes
shopCheckoutEndpoint = "/{uuid}/checkout"

# Endpoint for marking shop processes as applied
shopAppliedEndpoint = "/{uuid}/setApplied"

# Endpoint for cancelling shop processes
shopCancelEndpoint = "/{uuid}/cancel"

# URL of the balance page pushed to clients; empty = clients use their own client config
balanceUrl = ""

# Minecraft permission level required to run /shop (0-4). 0 = anyone, 2 = operators/command blocks only.
shopCommandPermissionLevel = 0

# Enable debug logging
debugEnabled = false

# Debug verbosity: MINIMAL | DEFAULT | ALL  (ALL includes full inventory dumps)
debugVerbosity = "DEFAULT"
```

| Key | Notes |
|---|---|
| `apiBaseUrl` | Root of your shop API. Every endpoint below is appended to it. |
| `apiKey` | Sent as the `X-Webshop-Api-Key` header on **every** request. Leave empty to send an empty key; set it (and validate it server-side) to lock the API down. |
| `shopEndpoint` / `shopCheckoutEndpoint` / `shopAppliedEndpoint` / `shopCancelEndpoint` | Paths for the four operations. The `{uuid}` placeholder is substituted with the session UUID; if you omit it from the path the UUID is still available in the JSON body. |
| `balanceUrl` | Balance page the server pushes to every client for the [balance overlay](#balance-overlay). Leave empty to let each client use its own `balanceUrl` from the client config. |
| `shopCommandPermissionLevel` | Minecraft permission level (0-4) required to run `/shop`. Default `0` (anyone). See [Locking down `/shop`](#locking-down-shop) below. |
| `debugEnabled` / `debugVerbosity` | Server-console logging. `ALL` prints full serialized inventories — useful when developing your API, noisy in production. |

> **Important:** the in-game browser loads `link` exactly as returned by your `/initiate` response. If your server runs behind a proxy, tunnel, or public hostname, make sure that `link` is an address the **player's client** can actually reach — not an internal `localhost` address.

### Client configuration

The client generates a second, purely client-side config at `config/webshoplink-client.toml`. It only controls the optional [balance overlay](#balance-overlay). Its `balanceUrl` is a **fallback**: if the server sets a `balanceUrl` of its own, that one wins (see [Balance overlay](#balance-overlay)).

```toml
# Show the shop balance as a small always-visible overlay (requires MCEF)
balanceDisplayEnabled = true

# URL of the balance page; the player's UUID is appended (or replaces {uuid})
balanceUrl = "http://localhost:8080/balance/"

# Screen corner: TOP_LEFT | TOP_RIGHT | BOTTOM_LEFT | BOTTOM_RIGHT
balancePosition = "TOP_RIGHT"

# Size and edge distance in scaled (GUI) pixels
balanceWidth = 120
balanceHeight = 40
balanceMargin = 4
```

The overlay is **on by default**, but points at `localhost`. The simplest way to give players the right URL is to set `balanceUrl` in the **server** config — it is pushed to every client on join and overrides this value, so nothing has to be shipped with the modpack. Otherwise ship this client config with your modpack (a copy preconfigured for the demo is at [`demo/webshoplink-client.toml`](demo/webshoplink-client.toml)). Players who don't want the overlay can turn it off locally.

---

## Balance overlay

When `balanceDisplayEnabled` is on, the mod keeps a **small transparent in-game browser** (the same MCEF browser used for the shop, just tiny and non-interactive) docked in the configured screen corner while the player is in a world. It simply loads:

```
GET {balanceUrl}{playerUuid}          e.g.  GET http://localhost:8080/balance/069a79f4-44e9-...
```

**Where the URL comes from:** when the player joins, the server sends its own `balanceUrl` (server config) to the client, and the overlay uses it. If the server leaves that value empty — or isn't running this mod — the client falls back to `balanceUrl` from its client config. Reloading the server config re-pushes the new URL to everyone online, and the pushed URL is dropped when the player disconnects, so each server only affects its own session.

If `balanceUrl` contains a `{uuid}` placeholder, it is substituted instead of appended. That is the entire contract — a plain `GET`, **no authentication, no API key, no session**: the page is fetched directly by the player's client, not by the Minecraft server. Treat the balance shown there as public information, and make sure the URL is reachable from players' machines (same caveat as the `link` returned by `/initiate`).

Guidelines for the page itself (see [`demo/public/balance.html`](demo/public/balance.html) for a working example):

- The browser window **is** the box — typically ~120×40 GUI pixels. Fill 100% of the viewport with a single panel and scale text with viewport units.
- A transparent page background (`background: transparent`) lets the game world show through around your panel's rounded corners.
- The overlay is passive (no mouse/keyboard input reaches it), so the page must **refresh itself** (e.g. poll a JSON endpoint every few seconds).
- Additionally, the mod reloads the page ~2 seconds after every shop session closes, so a purchase is reflected promptly.
- If the page **fails to load** (server unreachable, or a non-2xx response), the overlay swaps in a built-in "Failed to load balance" box instead of Chromium's error page, and retries the real URL every 30 seconds (and after each shop session).

---

## Writing the shop page

Beyond returning a reachable `link` from `/initiate`, a few properties of the page itself decide whether opening a shop looks seamless.

### How the mod shows your page

The mod keeps **one** Chromium browser alive for the whole session on the server, parked on a transparent blank page. `/shop` does not create a browser — it navigates that existing one. Chromium keeps painting the previous (transparent) frame until the new page has produced one, and the mod additionally refuses to draw the browser until your page is considered ready. So while your page loads the player sees the live game world, not a blank rectangle. (Before 1.9.0 a browser was created per session, which flashed its empty first frame over the world every time.)

"Ready" is decided by the first of these to happen:

1. your page sets `document.title = "webshoplink:ready"` — the explicit handshake, described below;
2. the document's `load` event fires, plus ~120 ms for the first paint;
3. 5 seconds elapse (a page that never loads is shown as-is, error page and all).

If the page is still not ready after ~400 ms, a small `Loading shop…` label appears over the world until it is.

### Signal readiness explicitly

Rule 2 only knows that the *document* loaded. If your shop fetches its session data and renders client-side (as most do), `load` fires while the page is still an empty shell, and the player briefly sees that shell. Setting the title once your real content is on screen removes it:

```js
function signalReady() {
  document.title = "webshoplink:ready";
}

// after the first render — two frames: one to lay out, one to paint
requestAnimationFrame(() => requestAnimationFrame(signalReady));
```

Set it on your error paths too, otherwise a "session expired" message waits for the timeout. Setting it more than once is harmless; the mod only reacts while a session is opening. The handshake is entirely optional — shops that never set the title keep working on rule 2.

### Declare transparency before anything else

The browser composites your page over the game world, so an opaque page background covers it. Put the transparent background in an **inline `<style>` at the very top of `<head>`**, ahead of every `<link rel="stylesheet">` and `<script>`:

```html
<head>
  <meta charset="utf-8" />
  <style>html, body { background: transparent; }</style>
  <!-- stylesheets, scripts, everything else after this -->
</head>
```

If transparency only arrives with an external stylesheet, the browser paints the default opaque page background for the frames before that stylesheet is applied — a white flash over the world that the mod cannot suppress, because as far as it can tell your page painted exactly what you asked for.

Two related habits worth keeping:

- **Avoid render-blocking third-party assets.** A CDN script or webfont in `<head>` delays your first paint by however long that host takes to answer, and that delay lands squarely in the window the player is waiting through. (The bundled demo uses the Tailwind Play CDN for convenience — a production shop should ship compiled CSS.)
- **Render something meaningful in the first paint.** With the handshake you control when the page appears, so use it to appear *finished* rather than to appear early.

---

## Locking down `/shop`

By default any player can run `/shop <type>` from anywhere. If you instead want shops to open only at specific locations — e.g. at a market stall block, an NPC, or a pressure plate — restrict who can *initiate* a session and drive `/shop` yourself from command blocks.

Set the permission level in the config:

```toml
# 0 = anyone, 1 = moderator, 2 = gamemaster/operator (also command blocks), 3 = admin, 4 = owner
shopCommandPermissionLevel = 2
```

At level `2`, only operators and command blocks can run `/shop`. Players can no longer open shops on their own, but you can open one *for* them from a command block at a chosen location:

```
/execute as @p[distance=..3] run shop weapons The Gunsmith
```

`/execute as` runs `/shop` as the targeted player (so their inventory is snapshotted) while keeping the command block's permission level, so the lock is satisfied.

The finishing commands — `/shopFinish`, `/confirmFinish`, `/shopCancel` — are intentionally **not** affected by this setting and remain available to everyone. A player can only use them against a session that already exists, and with `/shop` locked down they cannot create one themselves, so there is nothing to abuse. Lowering or raising `shopCommandPermissionLevel` only gates session initiation.

> Permission levels 2-4 map to vanilla op levels (`/op` grants level 4 by default, configurable via `server.properties` `op-permission-level`). Command blocks always run at level 2, which is why level `2` is the recommended setting for the command-block workflow.

---

## The API contract

Your shop API must implement four endpoints. All are `POST` with `Content-Type: application/json`, all receive the `X-Webshop-Api-Key` header, and the typical lifecycle is:

```
/initiate ──▶ (player shops in browser) ──▶ /checkout ──▶ /setApplied
                                                  └── /cancel (any time)
```

| Endpoint (default path) | Request body | Success response (HTTP 200) |
|---|---|---|
| `/initiate` | `{ playerId, shopSlug, inventories }` | `{ uuid, link, twoFactorCode }` |
| `/{uuid}/checkout` | `{ uuid, tfaCode }` | `{ inventory, echest }` — the **new** inventory |
| `/{uuid}/setApplied` | `{ uuid, tfaCode }` | `{ "message": "Shop instance marked as applied" }` |
| `/{uuid}/cancel` | `{ uuid, tfaCode }` | any 200 response |

### 1. Initiate — `POST {apiBaseUrl}{shopEndpoint}`

Called when a player runs `/shop <type>`. The request carries the player's current inventory snapshot.

**Request**
```json
{
  "playerId": "uuid-of-player",
  "shopSlug": "shop-type",
  "inventories": {
    "inventory": { /* Inventory Data — see below */ },
    "echest":    { /* Inventory Data — see below */ }
  }
}
```

**Response**
```json
{
  "link": "https://shop.example.com/session/abcd",
  "uuid": "session-uuid",
  "twoFactorCode": 123456
}
```

- `uuid` and `link` are **required** — the mod aborts the session with an error to the player if either is missing.
- `link` is the page opened in the in-game browser. Because the browser is created transparent, a page with a transparent CSS background will let the (dimmed) game world show through behind your shop panel.
- `twoFactorCode` is **anti-tampering only**: the mod stores it and echoes it back as `tfaCode` on every subsequent call. Reject mismatches (the demo answers `403`) so sessions can't be driven by an outside party.

### 2. Checkout — `POST {apiBaseUrl}{shopCheckoutEndpoint}`

Called when the player clicks **Finish Trade** (or runs `/shopFinish`). Return the **complete desired inventory**, not a diff — the mod replaces the player's inventory with exactly what you send.

**Request**
```json
{ "uuid": "session-uuid", "tfaCode": 123456 }
```

**Response**
```json
{
  "inventory": { /* Inventory Data */ },
  "echest":    { /* Inventory Data */ }
}
```

### 3. Set Applied — `POST {apiBaseUrl}{shopAppliedEndpoint}`

Called right before the mod writes the new inventory to the player, to tell your shop the trade is committing.

**Request**
```json
{ "uuid": "session-uuid", "tfaCode": 123456 }
```

**Response** — the mod **string-compares** this; it must match exactly:
```json
{ "message": "Shop instance marked as applied" }
```

### 4. Cancel — `POST {apiBaseUrl}{shopCancelEndpoint}`

Called when a session is abandoned: ESC / **Cancel** in the browser, `/shopCancel`, or automatically when a player starts a new `/shop` while one is still open.

**Request**
```json
{ "uuid": "session-uuid", "tfaCode": 123456 }
```

**Response** — only the **HTTP status matters**; any `200` is treated as success.

### Error responses

For any endpoint, return a **non-200** status with either field and the mod surfaces that text to the player:

```json
{ "error": "Error message" }
```
```json
{ "message": "Some other message" }
```

---

## Inventory Data Format

Both the inventory the mod sends (`/initiate`) and the inventory it expects back (`/checkout`) use the same shape:

```jsonc
{
  "inventory": {
    "size": 41,                  // player inventory: 36 main + 4 armor + 1 offhand
    "items": {                   // keyed by slot index; empty slots are omitted
      "0": { "itemId": "minecraft:emerald", "count": 64 },
      "4": {
        "itemId": "minecraft:diamond_sword",
        "count": 1,
        "nbt": {
          "display": { "Name": "{\"text\":\"Debug Blade\"}" }
          // any item NBT: Enchantments, RepairCost, etc.
        }
      }
    }
  },
  "echest": {
    "size": 27,                  // ender chest
    "items": { /* same shape */ }
  }
}
```

- Each entry under `items` is keyed by its **slot index** and holds the fully serialized item in that slot.
- `nbt` is optional and only present for items that carry NBT.

### NBT notes

The mod's `NbtSerializer` works in plain JSON: numbers become numeric NBT tags, strings become `StringTag`, arrays become `ListTag`, objects become `CompoundTag`. Whole numbers come back as `IntTag`/`LongTag`, so a value vanilla expects as a `short` (e.g. an enchantment `lvl`) may not round-trip identically. A custom `display.Name` is the most reliable thing to verify visually — the demo's "Debug Blade" exercises this.

On **Minecraft 1.21.1** (NeoForge) items no longer have NBT; they carry data components. There
the `nbt` object is the item's *component patch* (only what differs from the item's defaults),
encoded the way `/give` writes it: keys are component ids such as `minecraft:custom_data`,
`minecraft:enchantments` or `minecraft:custom_name`. An item sent by a 1.20.1 server therefore
looks different from the same item sent by a 1.21.1 server, and a shop serving both must store
and return the shape that matches the server it is talking to.

---

## Security model

- **Two-factor code** — issued by your API at `/initiate`, echoed back as `tfaCode` on every later call. Validate it server-side; the mod treats it purely as a token to pass through.
- **Inventory verification** — before applying a checkout, the mod re-snapshots the player's inventory and compares it to the snapshot taken at `/initiate`. If anything changed, the purchase is **rejected** and the differences are reported to the player. This blocks duping and item-swap races during a session.
- **API key** — set `apiKey` and reject requests without a matching `X-Webshop-Api-Key` header to keep arbitrary clients from poking your shop endpoints.
- **Optional channel** — the mod's network channel accepts a missing protocol version, so non-mod clients can still join the server; they simply receive a message to install the client mod when they try to shop.

---

## Reference implementation

[`demo/`](demo/) is a self-contained shop implementing this contract, with its own [README](demo/README.md) covering setup, a flow diagram, and a `simulate.ts` that drives the protocol without Minecraft. Start there when building your own integration — copy [`demo/webshoplink-common.toml`](demo/webshoplink-common.toml) into your server's `config/` to point the mod at it.
