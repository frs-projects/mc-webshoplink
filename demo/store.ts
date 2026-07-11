/**
 * In-memory session store + the inventory math.
 *
 * A "session" is one shopping trip: it starts when the mod calls /initiate and
 * ends on /setApplied or /cancel. Everything lives in a Map; restart the server
 * and all sessions are gone — which is exactly what you want for debugging.
 */

import {
  CATALOG,
  CURRENCY_ITEM,
  CURRENCY_STACK,
  catalogById,
  catalogByItemId,
  sellPrice,
  type CatalogEntry,
} from "./catalog";
import type { ContainerData, InventoryList, ItemData } from "./types";

/** Slots 0..35 are the regular storage + hotbar; 36-39 armor, 40 offhand. */
const STORAGE_SLOTS = 36;

export type SessionStatus = "open" | "checked_out" | "applied" | "cancelled";

export interface Session {
  uuid: string;
  playerId: string;
  shopSlug: string;
  /** Anti-tampering code returned to the mod and required on later calls. */
  tfaCode: string;
  status: SessionStatus;
  createdAt: number;
  /** The inventory snapshot the mod sent at /initiate — the source of truth. */
  original: InventoryList;
  /** Cart: catalog entry id -> quantity to buy. */
  buy: Record<string, number>;
  /** Cart: inventory slot index (string) -> quantity to sell from that slot. */
  sell: Record<string, number>;
}

const sessions = new Map<string, Session>();

export function createSession(
  playerId: string,
  shopSlug: string,
  inventories: InventoryList,
): Session {
  const uuid = crypto.randomUUID();
  const session: Session = {
    uuid,
    playerId,
    shopSlug,
    tfaCode: String(Math.floor(100000 + Math.random() * 900000)), // 6 digits
    status: "open",
    createdAt: Date.now(),
    original: inventories,
    buy: {},
    sell: {},
  };
  sessions.set(uuid, session);
  return session;
}

export function getSession(uuid: string): Session | undefined {
  return sessions.get(uuid);
}

export function deleteSession(uuid: string): void {
  sessions.delete(uuid);
}

/**
 * Best-known currency balance for a player, derived from their most recent
 * session (the demo has no player database — currency lives in the inventory).
 * Returns null when the player has never opened a session on this server.
 */
export function playerBalance(playerId: string): number | null {
  let latest: Session | undefined;
  for (const s of sessions.values()) {
    if (s.playerId === playerId && (!latest || s.createdAt > latest.createdAt)) {
      latest = s;
    }
  }
  if (!latest) return null;
  // After a completed trade the applied result is fresher than the snapshot.
  if (latest.status === "applied") return computeOrder(latest).balanceAfter;
  return countCurrency(latest.original.inventory);
}

// ---------------------------------------------------------------------------
// Inventory helpers
// ---------------------------------------------------------------------------

function cloneContainer(c: ContainerData): ContainerData {
  return structuredClone(c);
}

function nbtKey(nbt?: Record<string, unknown>): string {
  return nbt ? JSON.stringify(nbt) : "";
}

/** Total amount of currency held across all (NBT-free) currency stacks. */
function countCurrency(c: ContainerData): number {
  let total = 0;
  for (const item of Object.values(c.items)) {
    if (item.itemId === CURRENCY_ITEM && !item.nbt) total += item.count;
  }
  return total;
}

/** Remove every plain currency stack so we can re-distribute a new balance. */
function removeCurrency(c: ContainerData): void {
  for (const [slot, item] of Object.entries(c.items)) {
    if (item.itemId === CURRENCY_ITEM && !item.nbt) delete c.items[slot];
  }
}

function firstEmptySlot(c: ContainerData): number | null {
  for (let i = 0; i < STORAGE_SLOTS; i++) {
    if (!c.items[String(i)]) return i;
  }
  return null;
}

/**
 * Add `count` of an item into the container, first topping up matching partial
 * stacks, then filling empty storage slots. Returns false if it ran out of room.
 */
function addItem(
  c: ContainerData,
  itemId: string,
  count: number,
  stack: number,
  nbt?: Record<string, unknown>,
): boolean {
  let remaining = count;
  const key = nbtKey(nbt);

  // Top up existing matching stacks.
  if (stack > 1) {
    for (const item of Object.values(c.items)) {
      if (remaining <= 0) break;
      if (item.itemId === itemId && nbtKey(item.nbt) === key && item.count < stack) {
        const space = stack - item.count;
        const add = Math.min(space, remaining);
        item.count += add;
        remaining -= add;
      }
    }
  }

  // Spill the rest into empty slots.
  while (remaining > 0) {
    const slot = firstEmptySlot(c);
    if (slot === null) return false;
    const add = Math.min(stack, remaining);
    const item: ItemData = { itemId, count: add };
    if (nbt) item.nbt = nbt;
    c.items[String(slot)] = item;
    remaining -= add;
  }
  return true;
}

export interface ItemDelta {
  itemId: string;
  name: string;
  count: number;
}

export interface OrderPreview {
  balanceBefore: number;
  balanceAfter: number;
  cost: number; // net currency spent (buys minus sells)
  valid: boolean;
  errors: string[];
  added: ItemDelta[];
  removed: ItemDelta[];
  /** The resulting full inventory list to hand back to the mod at /checkout. */
  result: InventoryList;
}

function displayName(itemId: string, nbt?: Record<string, unknown>): string {
  const entry = catalogByItemId(itemId);
  if (entry) return entry.name;
  // Fall back to a Title-Cased version of the path, like the mod's UI does.
  const path = itemId.includes(":") ? itemId.split(":")[1] : itemId;
  return path
    .split("_")
    .map((p) => p.charAt(0).toUpperCase() + p.slice(1))
    .join(" ");
}

/** Aggregate a container into key -> {itemId, nbt, count} for diffing. */
function aggregate(c: ContainerData): Map<string, { itemId: string; nbt?: Record<string, unknown>; count: number }> {
  const m = new Map<string, { itemId: string; nbt?: Record<string, unknown>; count: number }>();
  for (const item of Object.values(c.items)) {
    const k = item.itemId + "|" + nbtKey(item.nbt);
    const cur = m.get(k);
    if (cur) cur.count += item.count;
    else m.set(k, { itemId: item.itemId, nbt: item.nbt, count: item.count });
  }
  return m;
}

/**
 * Compute the new inventory for a session's current cart. Pure: it never
 * mutates the stored session, so the frontend can call it on every cart change
 * to preview the outcome, and /checkout calls it to get the final result.
 */
export function computeOrder(session: Session): OrderPreview {
  const errors: string[] = [];
  const inv = cloneContainer(session.original.inventory);

  const balanceBefore = countCurrency(inv);
  let balance = balanceBefore;
  removeCurrency(inv);

  // --- Sells: remove items from their slots, gain currency. ---
  for (const [slot, qtyRaw] of Object.entries(session.sell)) {
    const qty = Math.floor(qtyRaw);
    if (qty <= 0) continue;
    const item = inv.items[slot];
    if (!item) {
      errors.push(`Nothing to sell in slot ${slot}.`);
      continue;
    }
    const entry = catalogByItemId(item.itemId);
    if (!entry) {
      errors.push(`${displayName(item.itemId)} cannot be sold here.`);
      continue;
    }
    if (qty > item.count) {
      errors.push(`Only ${item.count}x ${entry.name} available in slot ${slot}.`);
      continue;
    }
    item.count -= qty;
    if (item.count <= 0) delete inv.items[slot];
    balance += sellPrice(entry) * qty;
  }

  // --- Buys: total their cost. ---
  let buyCost = 0;
  const buys: { entry: CatalogEntry; qty: number }[] = [];
  for (const [id, qtyRaw] of Object.entries(session.buy)) {
    const qty = Math.floor(qtyRaw);
    if (qty <= 0) continue;
    const entry = catalogById(id);
    if (!entry) {
      errors.push(`Unknown catalog item "${id}".`);
      continue;
    }
    buyCost += entry.price * qty;
    buys.push({ entry, qty });
  }

  if (buyCost > balance) {
    errors.push(`Not enough ${CURRENCY_ITEM.split(":")[1]} (need ${buyCost}, have ${balance}).`);
  }
  balance = Math.max(0, balance - buyCost);

  // --- Add bought items. ---
  for (const { entry, qty } of buys) {
    const ok = addItem(inv, entry.itemId, entry.count * qty, entry.stack, entry.nbt);
    if (!ok) errors.push(`Not enough inventory space for ${entry.name}.`);
  }

  // --- Put the new balance back as currency stacks. ---
  if (balance > 0) {
    const ok = addItem(inv, CURRENCY_ITEM, balance, CURRENCY_STACK);
    if (!ok) errors.push(`Not enough inventory space to return ${balance} emeralds.`);
  }

  // --- Diff for display. ---
  const before = aggregate(session.original.inventory);
  const after = aggregate(inv);
  const added: ItemDelta[] = [];
  const removed: ItemDelta[] = [];
  const keys = new Set([...before.keys(), ...after.keys()]);
  for (const k of keys) {
    const b = before.get(k)?.count ?? 0;
    const a = after.get(k)?.count ?? 0;
    const meta = after.get(k) ?? before.get(k)!;
    const delta = a - b;
    if (delta > 0) added.push({ itemId: meta.itemId, name: displayName(meta.itemId, meta.nbt), count: delta });
    else if (delta < 0) removed.push({ itemId: meta.itemId, name: displayName(meta.itemId, meta.nbt), count: -delta });
  }

  return {
    balanceBefore,
    balanceAfter: balance,
    cost: buyCost,
    valid: errors.length === 0,
    errors,
    added,
    removed,
    result: { inventory: inv, echest: session.original.echest },
  };
}
