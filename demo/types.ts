/**
 * TypeScript mirror of the JSON contract used by the WebshopLink mod.
 *
 * These types intentionally match the Java classes in
 * `src/main/java/info/rusty/webshoplink/DataTypes.java` so the wire format is
 * easy to reason about. The mod (de)serializes everything with GSON.
 */

/**
 * A single item in one inventory slot.
 * Mirrors `DataTypes.ItemData`.
 */
export interface ItemData {
  /** Namespaced item id, e.g. "minecraft:diamond_sword". */
  itemId: string;
  /** Stack size. */
  count: number;
  /**
   * Optional serialized NBT, exactly as produced by the mod's `NbtSerializer`:
   * a plain JSON object where numbers map to NBT numeric tags, strings to
   * StringTag, arrays to ListTag, nested objects to CompoundTag.
   */
  nbt?: Record<string, unknown>;
}

/**
 * One container (the main player inventory OR the ender chest).
 * Mirrors `DataTypes.InventoryData` / `DataTypes.ContainerData`.
 *
 * `items` is keyed by the slot index. Note that in JSON the keys are strings
 * ("0", "1", ...) even though they represent integers on the Java side.
 * Empty slots are simply omitted from the map.
 */
export interface ContainerData {
  /** Total number of slots in the container (player inventory is usually 41). */
  size: number;
  items: Record<string, ItemData>;
}

/**
 * The `{ inventory, echest }` envelope. Sent by the mod on /initiate and
 * returned by the shop on /checkout. Mirrors `DataTypes.InventoryList`.
 */
export interface InventoryList {
  inventory: ContainerData;
  echest: ContainerData;
}

/** Body of `POST {shopEndpoint}` (default `/initiate`). */
export interface InitiateRequest {
  /** Player UUID (string form). */
  playerId: string;
  /** The `<type>` argument from `/shop <type>` in game. */
  shopSlug: string;
  inventories: InventoryList;
}

/**
 * Response to /initiate. Mirrors `DataTypes.ShopResponse`.
 * The mod requires both `uuid` and `link` to be present and non-empty.
 */
export interface InitiateResponse {
  /** Identifier for this session, echoed back on every later request. */
  uuid: string;
  /** Full URL the player opens in the browser to do their shopping. */
  link: string;
  /**
   * Anti-tampering code. The mod stores it and sends it back as `tfaCode` on
   * every subsequent request; the shop should reject mismatches.
   */
  twoFactorCode: string;
}

/** Body of /checkout, /setApplied and /cancel. */
export interface SessionRequest {
  uuid: string;
  tfaCode: string;
}
