# Minecraft WebshopLink

A Minecraft Forge mod for server-side shop functionality that allows players to interact with an external web shop system.

## Overview

WebshopLink is a server-side only mod that provides a seamless integration between your Minecraft server and an external web shop. Players can initiate shopping sessions in-game, complete purchases on a web interface, and have the items automatically added to their inventory.

## Features

- Server-side only implementation (no client-side mod required)
- Secure transaction handling with inventory verification
- Three in-game commands for shop interaction
- Two-factor authentication for enhanced security
- Support for main inventory, armor, offhand, and ender chest modifications
- Proper NBT data handling for complex items

## Commands

- `/shop <type>` - Initiates a shop session of the specified type and opens a web link
- `/shopFinish <uuid>` - Displays inventory changes to apply after completing a web purchase
- `/confirmFinish <uuid>` - Applies the inventory changes and completes the transaction

## Installation

1. Download the latest release JAR file
2. Place the JAR file in your server's `mods` folder
3. Configure the mod by editing the configuration file (generated after first run)
4. Restart your server

## Configuration

After the first server start, a configuration file will be created at:
`config/webshoplink-common.toml`

Configure the following settings:

```toml
# Base URL for the shop API
apiBaseUrl = "http://localhost:8080/api/shop"

# Endpoint for initiating shop processes
shopEndpoint = "/initiate"

# Endpoint for finishing shop processes
shopFinishEndpoint = "/finish"
```

Replace the `apiBaseUrl` with the URL of your shop API.

## API Requirements

The external shop API must implement the following endpoints:

### 1. Shop Initiation Endpoint

**Request:**
```json
{
  "playerId": "uuid-of-player",
  "type": "shop-type",
  "inventory": { /* serialized inventory data */ },
  "enderChest": [ /* serialized ender chest data */ ]
}
```

**Response:**
```json
{
  "processId": "uuid-for-process",
  "link": "https://your-shop-url.com/shop?id=process-id",
  "twoFactorCode": "verification-code"
}
```

### 2. Shop Finish Endpoint

**Request:**
```json
{
  "playerId": "uuid-of-player",
  "processId": "uuid-of-process"
}
```

**Response:**
```json
{
  "processId": "uuid-of-process",
  "inventory": { /* new inventory data */ },
  "enderChest": [ /* new ender chest data */ ]
}
```

## Inventory Data Format

The mod serializes and deserializes inventory data in the following format:

```json
{
  "mainInventory": [
    { "itemId": "minecraft:item_id", "count": 1, "nbt": { /* nbt data */ } },
    // More items...
  ],
  "armorInventory": [ /* armor items */ ],
  "offhandInventory": [ /* offhand items */ ],
  "enderChest": [ /* ender chest items */ ]
}
```

## Security Features

1. **Two-factor authentication**: Each shop session has a unique verification code that the player must enter on the website.
2. **Inventory verification**: The mod verifies that the player's inventory hasn't changed between starting and finishing a shop session.
3. **Process ID validation**: All transactions require a valid UUID linked to the player's specific shop session.

## License

All Rights Reserved

## Author

Rustypredator