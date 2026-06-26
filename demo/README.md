# WebshopLink — Demo Shop

A tiny, self-contained example web shop that speaks the [WebshopLink](../README.md)
mod's HTTP protocol. Use it to:

- **debug the mod** locally without standing up a real shop, and
- **learn the contract** — every file is commented and maps 1:1 to the mod's
  expectations.

It's built with [Bun](https://bun.sh) (server + tooling) and
[Tailwind CSS](https://tailwindcss.com) (the player-facing page, via the Play
CDN so there's no build step). Sessions live in memory only — restart and
everything is gone, which is exactly what you want while debugging.

---

## Quick start

```bash
cd demo
bun run server.ts          # starts on http://localhost:8080
# or: bun run dev          # same, with --watch auto-reload
```

You now have two ways to drive it:

### A) Without Minecraft (fastest)

```bash
bun run simulate.ts        # acts as the mod: opens a session, prints a link
bun run simulate.ts full   # runs the whole flow (cart → checkout → setApplied)
```

`simulate.ts` builds a sample player inventory and calls the same endpoints the
mod calls. The plain run prints a shop URL you can open in a browser; the `full`
run also fills a cart and verifies the round-trip end to end.

### B) With Minecraft

1. Build/install the mod on a server and copy [`webshoplink-common.toml`](./webshoplink-common.toml)
   into the server's `config/` folder (the defaults already point at this demo).
2. Start this demo server, then in game run `/shop default`.
3. The shop opens in an in-game browser window (requires the WebshopLink client
   mod + MCEF). Build your cart, then click **Finish Trade** in the button bar
   below the browser to apply it. Watch both the in-game inventory and this
   server's console.

---

## The flow

```
Minecraft                         Demo server (this project)            Browser
   |  /shop default                     |                                  |
   |---- POST /api/shop/initiate ------->|  create session, store           |
   |     {playerId, shopSlug,            |  the inventory snapshot          |
   |      inventories}                   |                                  |
   |<--- {uuid, link, twoFactorCode} ----|                                  |
   |                                     |                                  |
   |  (in-game browser opens the link) - - - - - - - - - - - - - - - - - - ->| GET /shop/:uuid
   |                                     |<--- GET  /api/session/:uuid ------| load catalog + inventory
   |                                     |<--- POST /api/session/:uuid/cart -| buy/sell, live preview
   |                                     |                                  |
   |  Finish Trade                       |                                  |
   |---- POST /…/:uuid/checkout -------->|  compute new inventory from cart |
   |     {uuid, tfaCode}                 |                                  |
   |<--- {inventory, echest} ------------|                                  |
   |                                     |                                  |
   |  Confirm (inventory unchanged?)     |                                  |
   |---- POST /…/:uuid/setApplied ------>|  mark applied                    |
   |<--- {message:"Shop instance         |                                  |
   |        marked as applied"} ---------|                                  |
   |  (mod applies the new inventory)    |                                  |
```

Cancelling at any point sends `POST /…/:uuid/cancel`.

---

## The API contract (what a real shop must implement)

All four endpoints live under `apiBaseUrl` (`http://localhost:8080/api/shop` by
default) and are `POST` with `Content-Type: application/json`. If you set an API
key, the mod sends it in the `X-Webshop-Api-Key` header on every call.

| Endpoint | Request body | Success response |
|---|---|---|
| `/initiate` | `{ playerId, shopSlug, inventories }` | `{ uuid, link, twoFactorCode }` |
| `/{uuid}/checkout` | `{ uuid, tfaCode }` | `{ inventory, echest }` (the **new** inventory) |
| `/{uuid}/setApplied` | `{ uuid, tfaCode }` | `{ message: "Shop instance marked as applied" }` |
| `/{uuid}/cancel` | `{ uuid, tfaCode }` | any 200 response |

Things the mod is strict about (all handled in [`server.ts`](./server.ts)):

- **`uuid` and `link` are required** in the `/initiate` response, or the mod
  aborts with an error.
- **`twoFactorCode`** is anti-tampering only: the mod stores it and sends it
  back as `tfaCode` on every later call. The demo rejects mismatches with `403`.
- **`setApplied` must return exactly** `{"message":"Shop instance marked as
  applied"}` — the mod string-compares this before declaring success.
- **`cancel` only cares about the HTTP 200** status.
- **Errors**: return a non-200 with `{"error": "..."}` or `{"message": "..."}`.
  The mod extracts that text and shows it to the player.

### Inventory format

Both directions use the same shape (see [`types.ts`](./types.ts), mirrored from
the mod's `DataTypes.java`):

```jsonc
{
  "inventory": {
    "size": 41,                  // player inventory: 36 main + 4 armor + 1 offhand
    "items": {                   // keyed by slot index; empty slots are omitted
      "0": { "itemId": "minecraft:emerald", "count": 64 },
      "4": {
        "itemId": "minecraft:diamond_sword",
        "count": 1,
        "nbt": { "display": { "Name": "{\"text\":\"Debug Blade\"}" } }
      }
    }
  },
  "echest": { "size": 27, "items": { /* same shape */ } }
}
```

At `/checkout` you return the **complete desired inventory**, not a diff — the
mod replaces the player's inventory with what you send (after verifying it
hasn't changed since `/initiate`).

> **NBT note:** the mod's `NbtSerializer` emits/consumes plain JSON: numbers
> become numeric NBT tags, strings become `StringTag`, arrays become `ListTag`,
> objects become `CompoundTag`. Whole numbers come back as `IntTag`/`LongTag`,
> so a value that vanilla expects as a `short` (e.g. an enchantment `lvl`) may
> not behave identically. A custom `display.Name` is the most reliable thing to
> verify visually. The demo's "Debug Blade" exercises both.

---

## How this demo is organised

| File | Purpose |
|---|---|
| [`server.ts`](./server.ts) | HTTP routing: the 4 mod endpoints + the browser helpers. The contract lives here. |
| [`store.ts`](./store.ts) | In-memory sessions and the inventory math (`computeOrder`). |
| [`catalog.ts`](./catalog.ts) | The shop's items, prices, and currency. Edit this to change the stock. |
| [`types.ts`](./types.ts) | TypeScript mirror of the mod's JSON types. |
| [`public/index.html`](./public/index.html) | The Tailwind shop page the player opens. |
| [`simulate.ts`](./simulate.ts) | Stand-in for the mod, to test without Minecraft. |
| [`webshoplink-common.toml`](./webshoplink-common.toml) | Drop-in mod config pointing at this server. |
| [`.env.example`](./.env.example) | Configuration (port, public URL, API key). |

The shop itself is deliberately minimal: emeralds are the currency, you can buy
from a fixed [catalog](./catalog.ts) and sell catalog items back. None of that is
dictated by the mod — only the inventory JSON returned at `/checkout` is. Swap in
your own pricing, currency, persistence, or per-`shopSlug` catalogs as needed.

---

## Configuration

Copy [`.env.example`](./.env.example) to `.env` (Bun loads it automatically):

| Variable | Default | Meaning |
|---|---|---|
| `PORT` | `8080` | Port to listen on. |
| `PUBLIC_URL` | `http://localhost:8080` | Origin used to build the browser `link`. Change it behind a proxy/tunnel. |
| `WEBSHOP_API_KEY` | _(empty)_ | If set, callers must send a matching `X-Webshop-Api-Key`. Leave empty for this mod build, which does not send an API key. |

---

## Using a compiled Tailwind build instead of the CDN

The Play CDN is great for a debug tool but isn't meant for production. To compile
a static stylesheet instead:

```bash
bunx tailwindcss -i ./input.css -o ./public/style.css --minify
```

Then drop the `<script src="https://cdn.tailwindcss.com">` from
[`public/index.html`](./public/index.html), add
`<link rel="stylesheet" href="/style.css">`, and serve `/style.css` from
`server.ts`.
