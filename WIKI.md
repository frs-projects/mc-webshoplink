# WebshopLink — Server Operator & Integrator Guide

This page covers everything a server operator needs beyond dropping the JAR in `mods/`: configuring the mod and **implementing the external web shop** it talks to. For the player-facing overview, see the [README](README.md).

WebshopLink itself never decides prices, stock, or what a player receives. It only:

- snapshots the player's inventory and sends it to your shop API,
- opens your shop's URL in an in-game browser,
- fetches the resulting inventory from your API and applies it (after verifying nothing changed in the meantime).

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
| `debugEnabled` / `debugVerbosity` | Server-console logging. `ALL` prints full serialized inventories — useful when developing your API, noisy in production. |

> **Important:** the in-game browser loads `link` exactly as returned by your `/initiate` response. If your server runs behind a proxy, tunnel, or public hostname, make sure that `link` is an address the **player's client** can actually reach — not an internal `localhost` address.

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

---

## Security model

- **Two-factor code** — issued by your API at `/initiate`, echoed back as `tfaCode` on every later call. Validate it server-side; the mod treats it purely as a token to pass through.
- **Inventory verification** — before applying a checkout, the mod re-snapshots the player's inventory and compares it to the snapshot taken at `/initiate`. If anything changed, the purchase is **rejected** and the differences are reported to the player. This blocks duping and item-swap races during a session.
- **API key** — set `apiKey` and reject requests without a matching `X-Webshop-Api-Key` header to keep arbitrary clients from poking your shop endpoints.
- **Optional channel** — the mod's network channel accepts a missing protocol version, so non-mod clients can still join the server; they simply receive a message to install the client mod when they try to shop.

---

## Reference implementation

[`demo/`](demo/) is a self-contained shop implementing this contract, with its own [README](demo/README.md) covering setup, a flow diagram, and a `simulate.ts` that drives the protocol without Minecraft. Start there when building your own integration — copy [`demo/webshoplink-common.toml`](demo/webshoplink-common.toml) into your server's `config/` to point the mod at it.
