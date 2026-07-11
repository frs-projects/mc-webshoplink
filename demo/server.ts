/**
 * WebshopLink demo server.
 *
 * Runs two groups of HTTP routes on a single Bun server:
 *
 *  1. The **mod-facing API** (called by the Minecraft mod over HTTP). These
 *     four routes are the actual contract you must implement for any real shop:
 *
 *        POST /api/shop/initiate              -> open a session
 *        POST /api/shop/:uuid/checkout        -> return the resulting inventory
 *        POST /api/shop/:uuid/setApplied      -> confirm the mod applied it
 *        POST /api/shop/:uuid/cancel          -> abort the session
 *
 *  2. The **player-facing site** (opened in the browser via the `link` returned
 *     by /initiate). This is entirely up to you; here it's a small Tailwind page
 *     backed by a couple of JSON helper routes:
 *
 *        GET  /shop/:uuid                     -> the shop page (HTML)
 *        GET  /api/session/:uuid              -> session view model (JSON)
 *        POST /api/session/:uuid/cart         -> update the cart, get a preview
 *
 *     Plus the balance overlay page the mod's HUD browser loads directly
 *     (keyed by player UUID, no API key or session required):
 *
 *        GET  /balance/:playerUuid            -> tiny balance box (HTML)
 *        GET  /api/balance/:playerUuid        -> { balance, currency } (JSON)
 *
 * See ./README.md for the full flow and how to point the mod at this server.
 */

import { computeOrder, createSession, deleteSession, getSession, playerBalance } from "./store";
import { CATALOG, CURRENCY_ITEM, CURRENCY_NAME, sellPrice, catalogByItemId } from "./catalog";
import type { InitiateRequest, SessionRequest } from "./types";

const PORT = Number(process.env.PORT ?? 8080);
/** Public origin used to build the browser link. Override behind a proxy. */
const PUBLIC_URL = process.env.PUBLIC_URL ?? `http://localhost:${PORT}`;
/** If set, the mod must send a matching X-Webshop-Api-Key header. */
const API_KEY = process.env.WEBSHOP_API_KEY ?? "";

const SHOP_PAGE = await Bun.file(new URL("./public/index.html", import.meta.url)).text();
const BALANCE_PAGE = await Bun.file(new URL("./public/balance.html", import.meta.url)).text();

// ---------------------------------------------------------------------------
// Small response helpers
// ---------------------------------------------------------------------------

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** Error body shape the mod knows how to parse (it reads `error`/`message`). */
function error(message: string, status = 400): Response {
  return json({ error: message }, status);
}

/** Verify the shared secret header when one is configured. */
function checkApiKey(req: Request): boolean {
  if (!API_KEY) return true;
  return req.headers.get("X-Webshop-Api-Key") === API_KEY;
}

/** Load a session and verify the anti-tampering code from the request body. */
function authSession(uuidFromPath: string, body: SessionRequest) {
  const session = getSession(body.uuid ?? uuidFromPath);
  if (!session) return { error: error("Unknown shop session", 404) };
  if (session.tfaCode !== String(body.tfaCode)) {
    return { error: error("Invalid two-factor code", 403) };
  }
  return { session };
}

// ---------------------------------------------------------------------------
// Mod-facing API handlers
// ---------------------------------------------------------------------------

async function handleInitiate(req: Request): Promise<Response> {
  const body = (await req.json()) as InitiateRequest;
  if (!body?.playerId || !body?.inventories?.inventory) {
    return error("Missing playerId or inventories");
  }

  const session = createSession(body.playerId, body.shopSlug ?? "default", body.inventories);
  console.log(`[initiate] session ${session.uuid} for player ${body.playerId} (slug="${session.shopSlug}")`);

  // The mod requires `uuid` and `link`; `twoFactorCode` is echoed back later.
  return json({
    uuid: session.uuid,
    link: `${PUBLIC_URL}/shop/${session.uuid}`,
    twoFactorCode: session.tfaCode,
  });
}

async function handleCheckout(uuid: string, req: Request): Promise<Response> {
  const body = (await req.json()) as SessionRequest;
  const auth = authSession(uuid, body);
  if (auth.error) return auth.error;
  const session = auth.session;

  if (session.status === "applied" || session.status === "cancelled") {
    return error(`Session already ${session.status}`, 409);
  }

  const order = computeOrder(session);
  if (!order.valid) {
    // The mod surfaces this message to the player and aborts the trade.
    return error(order.errors.join(" "), 400);
  }

  session.status = "checked_out";
  console.log(`[checkout] session ${uuid}: -${order.cost} emeralds, +${order.added.length} item kind(s)`);

  // The mod expects the full new { inventory, echest } and applies it verbatim.
  return json(order.result);
}

async function handleSetApplied(uuid: string, req: Request): Promise<Response> {
  const body = (await req.json()) as SessionRequest;
  const auth = authSession(uuid, body);
  if (auth.error) return auth.error;
  const session = auth.session;

  session.status = "applied";
  console.log(`[setApplied] session ${uuid} completed`);

  // The mod checks for this EXACT message before declaring success.
  return json({ message: "Shop instance marked as applied" });
}

async function handleCancel(uuid: string, req: Request): Promise<Response> {
  const body = (await req.json()) as SessionRequest;
  const auth = authSession(uuid, body);
  if (auth.error) return auth.error;

  auth.session.status = "cancelled";
  console.log(`[cancel] session ${uuid} cancelled`);
  // Only the 200 status matters to the mod for cancellation.
  return json({ message: "Shop instance cancelled" });
}

// ---------------------------------------------------------------------------
// Player-facing data handlers
// ---------------------------------------------------------------------------

/** Build the JSON view model the browser page renders. */
function sessionView(uuid: string): Response {
  const session = getSession(uuid);
  if (!session) return error("Unknown shop session", 404);

  const preview = computeOrder(session);

  // Sellable = inventory slots whose item is in the catalog (and isn't money).
  const sellable = Object.entries(session.original.inventory.items)
    .filter(([, item]) => item.itemId !== CURRENCY_ITEM && catalogByItemId(item.itemId))
    .map(([slot, item]) => {
      const entry = catalogByItemId(item.itemId)!;
      return {
        slot,
        itemId: item.itemId,
        name: entry.name,
        emoji: entry.emoji,
        count: item.count,
        unitSell: sellPrice(entry),
      };
    });

  return json({
    uuid: session.uuid,
    shopSlug: session.shopSlug,
    playerId: session.playerId,
    status: session.status,
    currency: { itemId: CURRENCY_ITEM, name: CURRENCY_NAME },
    catalog: CATALOG.map((e) => ({
      id: e.id,
      name: e.name,
      itemId: e.itemId,
      count: e.count,
      price: e.price,
      emoji: e.emoji,
      hasNbt: !!e.nbt,
    })),
    sellable,
    cart: { buy: session.buy, sell: session.sell },
    preview,
  });
}

async function handleCartUpdate(uuid: string, req: Request): Promise<Response> {
  const session = getSession(uuid);
  if (!session) return error("Unknown shop session", 404);
  if (session.status !== "open") return error(`Session is ${session.status}; cart is locked.`, 409);

  const body = (await req.json()) as { buy?: Record<string, number>; sell?: Record<string, number> };
  // Replace the cart wholesale; the frontend always sends the full cart.
  session.buy = sanitizeCart(body.buy);
  session.sell = sanitizeCart(body.sell);

  return sessionView(uuid);
}

/** Keep only positive integer quantities. */
function sanitizeCart(cart?: Record<string, number>): Record<string, number> {
  const out: Record<string, number> = {};
  for (const [k, v] of Object.entries(cart ?? {})) {
    const n = Math.floor(Number(v));
    if (Number.isFinite(n) && n > 0) out[k] = n;
  }
  return out;
}

// ---------------------------------------------------------------------------
// Router
// ---------------------------------------------------------------------------

const server = Bun.serve({
  port: PORT,
  async fetch(req) {
    const url = new URL(req.url);
    const path = url.pathname;
    const method = req.method;

    try {
      // --- Mod-facing API (requires the API key when configured). ---
      if (path.startsWith("/api/shop")) {
        if (!checkApiKey(req)) return error("Invalid or missing API key", 401);

        if (method === "POST" && path === "/api/shop/initiate") {
          return await handleInitiate(req);
        }
        const m = path.match(/^\/api\/shop\/([^/]+)\/(checkout|setApplied|cancel)$/);
        if (method === "POST" && m) {
          const [, uuid, action] = m;
          if (action === "checkout") return await handleCheckout(uuid, req);
          if (action === "setApplied") return await handleSetApplied(uuid, req);
          if (action === "cancel") return await handleCancel(uuid, req);
        }
        return error("Not found", 404);
      }

      // --- Player-facing JSON helpers. ---
      const view = path.match(/^\/api\/session\/([^/]+)$/);
      if (method === "GET" && view) return sessionView(view[1]);

      const cart = path.match(/^\/api\/session\/([^/]+)\/cart$/);
      if (method === "POST" && cart) return await handleCartUpdate(cart[1], req);

      // --- The shop page itself. ---
      if (method === "GET" && /^\/shop\/[^/]+$/.test(path)) {
        return new Response(SHOP_PAGE, { headers: { "Content-Type": "text/html; charset=utf-8" } });
      }

      // --- Balance overlay (loaded directly by the player's client; no auth). ---
      const balance = path.match(/^\/api\/balance\/([^/]+)$/);
      if (method === "GET" && balance) {
        return json({ playerId: balance[1], balance: playerBalance(balance[1]), currency: CURRENCY_NAME });
      }
      if (method === "GET" && /^\/balance\/[^/]+$/.test(path)) {
        return new Response(BALANCE_PAGE, { headers: { "Content-Type": "text/html; charset=utf-8" } });
      }

      // --- Landing page. ---
      if (method === "GET" && path === "/") {
        return new Response(
          "WebshopLink demo server is running.\n\n" +
            "Open a shop session from Minecraft with `/shop default`,\n" +
            "or run `bun run simulate.ts` to get a browsable link without the game.\n",
          { headers: { "Content-Type": "text/plain; charset=utf-8" } },
        );
      }

      return error("Not found", 404);
    } catch (err) {
      console.error("[error]", err);
      return error("Internal server error: " + (err as Error).message, 500);
    }
  },
});

console.log(`WebshopLink demo listening on ${server.url}`);
console.log(`  mod API base : ${PUBLIC_URL}/api/shop`);
console.log(`  API key      : ${API_KEY ? "required (X-Webshop-Api-Key)" : "disabled (set WEBSHOP_API_KEY to enable)"}`);
