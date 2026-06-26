/**
 * Pretends to be the WebshopLink mod so you can debug the shop without running
 * Minecraft. It builds a sample player inventory and drives the HTTP flow.
 *
 *   bun run simulate.ts          # only /initiate, then prints a browsable link
 *   bun run simulate.ts full     # initiate -> (auto cart) -> checkout -> setApplied
 *
 * Env: BASE_URL (default http://localhost:8080/api/shop), WEBSHOP_API_KEY.
 */

import type { InventoryList } from "./types";

const BASE_URL = process.env.BASE_URL ?? "http://localhost:8080/api/shop";
const API_KEY = process.env.WEBSHOP_API_KEY ?? "";
const FULL = process.argv[2] === "full";

const headers: Record<string, string> = { "Content-Type": "application/json" };
if (API_KEY) headers["X-Webshop-Api-Key"] = API_KEY;

/** A small but representative player inventory (size 41 like a real player). */
const inventories: InventoryList = {
  inventory: {
    size: 41,
    items: {
      "0": { itemId: "minecraft:emerald", count: 64 },
      "1": { itemId: "minecraft:emerald", count: 32 }, // 96 emeralds total
      "2": { itemId: "minecraft:diamond", count: 5 },
      "3": { itemId: "minecraft:bread", count: 12 },
      "4": {
        itemId: "minecraft:diamond_sword",
        count: 1,
        nbt: { display: { Name: '{"text":"Old Blade"}' }, Damage: 120 },
      },
    },
  },
  echest: { size: 27, items: {} },
};

async function post(path: string, body: unknown) {
  const res = await fetch(BASE_URL + path, { method: "POST", headers, body: JSON.stringify(body) });
  const text = await res.text();
  let parsed: any;
  try {
    parsed = JSON.parse(text);
  } catch {
    parsed = text;
  }
  return { status: res.status, body: parsed };
}

function summarize(inv: InventoryList) {
  let emeralds = 0;
  const items: string[] = [];
  for (const it of Object.values(inv.inventory.items)) {
    if (it.itemId === "minecraft:emerald" && !it.nbt) emeralds += it.count;
    else items.push(`${it.count}× ${it.itemId}${it.nbt ? " (+nbt)" : ""}`);
  }
  return `${emeralds} emeralds | ${items.join(", ") || "no other items"}`;
}

console.log(`→ POST /initiate  (player inventory: ${summarize(inventories)})`);
const init = await post("/initiate", {
  playerId: "00000000-0000-0000-0000-000000000001",
  shopSlug: "default",
  inventories,
});
console.log(`  ${init.status}`, init.body);

if (init.status !== 200) process.exit(1);
const { uuid, link, twoFactorCode } = init.body;
console.log(`\n  Open the shop:  ${link}`);

if (!FULL) {
  console.log("\nNext steps (or run with `full` to do them automatically):");
  console.log(`  checkout:   curl -XPOST ${BASE_URL}/${uuid}/checkout   -H 'Content-Type: application/json' -d '{"uuid":"${uuid}","tfaCode":"${twoFactorCode}"}'`);
  console.log(`  setApplied: curl -XPOST ${BASE_URL}/${uuid}/setApplied -H 'Content-Type: application/json' -d '{"uuid":"${uuid}","tfaCode":"${twoFactorCode}"}'`);
  console.log(`  cancel:     curl -XPOST ${BASE_URL}/${uuid}/cancel     -H 'Content-Type: application/json' -d '{"uuid":"${uuid}","tfaCode":"${twoFactorCode}"}'`);
  process.exit(0);
}

// --- full flow: act as the browser to fill a cart, then as the mod. ---
const sessionBase = BASE_URL.replace(/\/api\/shop$/, "/api/session");
console.log(`\n→ (browser) fill cart: buy 1 Debug Blade + 3 diamonds, sell 6 bread`);
await fetch(`${sessionBase}/${uuid}/cart`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ buy: { debug_blade: 1, diamond: 3 }, sell: { "3": 6 } }),
});

console.log(`→ POST /${uuid}/checkout`);
const checkout = await post(`/${uuid}/checkout`, { uuid, tfaCode: twoFactorCode });
console.log(`  ${checkout.status}`);
if (checkout.status === 200) {
  console.log(`  new inventory: ${summarize(checkout.body)}`);
} else {
  console.log(`  error:`, checkout.body);
  process.exit(1);
}

console.log(`→ POST /${uuid}/setApplied`);
const applied = await post(`/${uuid}/setApplied`, { uuid, tfaCode: twoFactorCode });
console.log(`  ${applied.status}`, applied.body);
console.log(applied.body?.message === "Shop instance marked as applied" ? "\n✅ Full flow OK" : "\n❌ Unexpected setApplied message");
