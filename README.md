# WebshopLink

A mod for server-side shop functionality that allows players to interact with an external web shop system.  

> Im going to refer to this project as a mod throughout the description, even when talking about potential plugin versions of this project as the functionality will be the same regardless.

> Warning: This Mod is intended to work in tandem with a web-based shop system that does the actual modification of the players inventory.

## Overview

WebshopLink is a server-side only Mod that provides a seamless integration between your Minecraft server and an external web shop. Players can initiate shopping sessions in-game, complete purchases on a web interface, and have the items automatically added to their inventory.

## Features

- Server-side only implementation (no client-side mod required)
- Two-factor authentication for enhanced security
- Support for main Inventory and Ender-Chest modification
- Proper NBT data serialization and deserialization for complex items

## Commands

- `/shop <type>`
  - Automatically cancels any previously unfinished sessions.
  - Initiates a shop session of the specified type.
  - Displays a clickable Link in chat to open the shop instance.
  - Displays a clickable message in chat to execute the `/shopFinish` command automatically using the correct uuid.
- `/shopFinish <uuid>`
  - Starts checkout process.
  - Displays a clickable message in chat to execute the `/confirmFinish` Command automatically using the correct uuid.
  - TODO: Show inventory changes.
- `/confirmFinish <uuid>`
  - Applies the inventory changes and completes the transaction.

## Installation

1. Download the latest release JAR file
2. Place the JAR file in your server's `mods` or `plugins` folder (Depending on server type)
3. Configure the mod by editing the configuration file (generated after first run)
4. Restart your server

## Configuration

After the first server start, a configuration file will be created at:
`config/webshoplink-common.toml`

Configure the following settings:

```toml
# Base URL for the shop API
apiBaseUrl = "http://localhost:8080/api/shop"

#Endpoint for initiating shop processes
shopEndpoint = "/initiate"

#Endpoint for checking out shop processes
shopCheckoutEndpoint = "/{uuid}/checkout"

#Endpoint for marking shop processes as applied
shopAppliedEndpoint = "/{uuid}/setApplied"

#Endpoint for cancelling shop processes
shopCancelEndpoint = "/{uuid}/cancel"
```

Replace the `apiBaseUrl` with the URL of your shop API.  

When editing any of the "Endpoint" options, you can either put the uuid in the url like in the example or not, the uuid is additionally supplied in the request json body.

## API Requirements

The external shop API must implement the following endpoints:

### 1. Shop Initiation Endpoint

**Request:**
```json
{
  "playerId": "uuid-of-player",
  "shopSlug": "shop-type",
  "inventories": {
    "inventory": {
      // Serialized Inventory Data
      // See "Inventory Data Format" section in readme
    },
    "echest": { 
      // Serialized Inventory Data
      // See "Inventory Data Format" section in readme
    }
  }
}
```

**Response:**
```json
{
  // Full url to the shop instance frontend the player can visit
  "link": "https://test.com/shopidxy",
  // UUID identifying the instance for further commands.
  "uuid": "uuid",
  // Verification only used in the mod to ensure no tampering from external sources.
  "twoFactorCode": 000000
}
```

### 2. Shop Cancel Endpoint

**Request:**
```json
{
  // tfa code from init response
  "tfaCode": 000000,
  // uuid from init response
  "uuid": "uuid"
}
```

**Response:**  
HTTP Status Code is important here, only a "200" Code is considered a successful cancelation
```json
{
  "error": "Error message", // optional
  "message": "some other message" // optional
}
```

### 3. Shop Finish Endpoint

**Request:**
```json
{
  // tfa code from init response
  "tfaCode": 000000,
  // uuid from init response
  "uuid": "uuid"
}
```

**Response:**
```json
{
  "inventory": {
    // Serialized Inventory Data
    // See "Inventory Data Format" section in readme
  },
  "echest": {
    // Serialized Inventory Data
    // See "Inventory Data Format" section in readme
  }
}
```

### 4. Shop Finish Endpoint

**Request:**
```json
{
  // tfa code from init response
  "tfaCode": 000000,
  // uuid from init response
  "uuid": "uuid"
}
```

**Response:**
```json
{
  "inventory": {
    // Serialized Inventory Data
    // See "Inventory Data Format" section in readme
  },
  "echest": {
    // Serialized Inventory Data
    // See "Inventory Data Format" section in readme
  }
}
```

## Inventory Data Format

The mod serializes and deserializes inventory data in the following format:

```json
{
  "inventory": {
    "size": 41,
    "items": {
      // Each object is keyed by its slot ID and contains fully serialized data of the item in the slot.
      "0": {
        "itemId": "minecraft:cobblestone",
        "count": 1
      },
      "1": {
        "itemId": "minecraft:diamond_chestplate",
        "count": 1,
        "nbt": {
          "RepairCost": 1,
          "Enchantments": []
          // etc. etc.
        }
      }
    }
  },
  "echest": {
      // Same as in the inventory, just with the items from the echest...
  }
}
```