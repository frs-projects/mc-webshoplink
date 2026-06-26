/**
 * The demo shop's catalog and "currency".
 *
 * This is deliberately hard-coded so the example stays self-contained. A real
 * shop would load this from a database, a config file, per-`shopSlug` listings,
 * etc. Nothing here is dictated by the mod — the mod only cares about the
 * inventory JSON the shop returns at /checkout.
 */

/** The item used as money. Buying spends it, selling earns it. */
export const CURRENCY_ITEM = "minecraft:emerald";
export const CURRENCY_NAME = "Emerald";
/** How many currency items fit in one stack (emeralds stack to 64). */
export const CURRENCY_STACK = 64;

export interface CatalogEntry {
  /** Internal id used by the cart/frontend (not a Minecraft id). */
  id: string;
  /** Human-friendly name shown in the UI. */
  name: string;
  /** Namespaced Minecraft item id added to the inventory when bought. */
  itemId: string;
  /** How many items one "unit" gives. */
  count: number;
  /** Price in currency items per unit. */
  price: number;
  /** Max stack size of this item in Minecraft (tools/armor = 1). */
  stack: number;
  /** Price the shop pays when buying this item back (sell). Defaults to floor(price/2). */
  sell?: number;
  /**
   * Optional NBT applied to the bought item. Uses the same plain-JSON shape the
   * mod's NbtSerializer emits/consumes. Great for exercising the NBT round-trip.
   */
  nbt?: Record<string, unknown>;
  /** Emoji shown in the UI purely for flavour. */
  emoji?: string;
}

export const CATALOG: CatalogEntry[] = [
  { id: "bread", name: "Bread", itemId: "minecraft:bread", count: 16, price: 1, stack: 64, emoji: "🍞" },
  { id: "diamond", name: "Diamond", itemId: "minecraft:diamond", count: 1, price: 8, stack: 64, emoji: "💎" },
  { id: "golden_apple", name: "Golden Apple", itemId: "minecraft:golden_apple", count: 1, price: 6, stack: 64, emoji: "🍎" },
  { id: "iron_ingot", name: "Iron Ingot", itemId: "minecraft:iron_ingot", count: 8, price: 4, stack: 64, emoji: "⛓️" },
  { id: "oak_log", name: "Oak Log", itemId: "minecraft:oak_log", count: 32, price: 2, stack: 64, emoji: "🪵" },
  {
    // A non-stackable item carrying NBT — use this to debug the NBT round-trip
    // (custom name + enchantment). See the README note about short vs int tags.
    id: "debug_blade",
    name: "Debug Blade (enchanted)",
    itemId: "minecraft:diamond_sword",
    count: 1,
    price: 24,
    stack: 1,
    emoji: "🗡️",
    nbt: {
      // A display name is the clearest NBT to verify in-game.
      display: { Name: '{"text":"Debug Blade","color":"aqua","italic":false}' },
      // Enchantments list — each entry is a CompoundTag.
      Enchantments: [{ id: "minecraft:sharpness", lvl: 5 }],
      Damage: 0,
    },
  },
];

export function catalogById(id: string): CatalogEntry | undefined {
  return CATALOG.find((e) => e.id === id);
}

export function catalogByItemId(itemId: string): CatalogEntry | undefined {
  return CATALOG.find((e) => e.itemId === itemId);
}

/** Sell price for a catalog entry (defaults to half the buy price, min 1). */
export function sellPrice(entry: CatalogEntry): number {
  return entry.sell ?? Math.max(1, Math.floor(entry.price / 2));
}
