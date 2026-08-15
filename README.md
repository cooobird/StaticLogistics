# StaticLogistics

**[English](README.md)** | **[中文](README_ZH.md)**

A Minecraft logistics mod for NeoForge 1.21.1 with item, fluid, energy, and integration-resource transfer, visual network management, cross-dimension links, upgrades, filters, group permissions, and blueprints.

## Features

- **Unified Link Configurator** — Manage groups, connections, the network preview, endpoint settings, upgrades, filters, resource types, and player inventory from one screen
- **Interactive network preview** — Select and drag nodes, pan, zoom, highlight links, inspect direction and range warnings, and preserve custom layouts
- **Cross-dimension transfer** — The Dimension Upgrade removes both dimension and normal distance restrictions
- **4 tool modes** — Wrench, Link as Input, Link as Output, Remove Links
- **Per-face configuration** — Each endpoint controls input and output independently; input settings cover filters, priority, and stock keeping, while output settings cover filters, upgrades, distribution, extraction, and resource types
- **7 upgrade types, 5 tiers each** — Speed, Range, Stack (Iron → Gold → Diamond → Netherite → Nether Star), plus Dimension, Basic Filter, Tag Filter, NBT Filter
- **Smart filtering** — Basic (item whitelist/blacklist), Tag (item + fluid tags), NBT (exact/partial NBT matching) with 4 match strategies: EXACT, CONTAINS, SMART_CONTAINS, IGNORE
- **2 extraction modes** — Sequential, Slot Round-Robin
- **5 distribution strategies** — Sequential, Round-Robin, Nearest, Furthest, Random
- **Transfer events** — `PreTransferEvent` (cancellable) and `PostTransferEvent` for third-party hooking
- **Group and connection management** — Expand grouped links, rename or remove individual entries, and merge same-owner groups
- **FTB Teams integration** — team-based ownership and permissions
- **Blueprints** — Preview a whole group or one connection, move and rotate it, undo changes, and paste it to blocks
- **Full /sl command tree** — info, list, stats, transfer, rename, cleanup
- **Configurable performance** — ticker batch size, cooldown intervals, cache sizes, object pool size
## Getting Started

1. Craft the **Link Configurator**
2. Hold the Link Configurator and right-click in air to open the unified configurator, then create or select a group
3. Select Link as Output or Link as Input and right-click the block faces to connect
4. Select a node from the group list or network preview, then configure its input, output, upgrades, and filters in the lower panel

| Mode           | Right-click on block face                                  |
|----------------|------------------------------------------------------------|
| Wrench         | Remove logistics configuration while preserving block data |
| Link as Input  | Use the selected face as the receiving endpoint            |
| Link as Output | Use the selected face as the sending endpoint              |
| Remove Links   | Remove links associated with that face                     |

## Configurator and Network Preview

- Click a group to expand its links. Double-click groups or links to rename them, and right-click to remove them.
- Renaming a group to an existing name merges both groups when they have the same owner.
- Select nodes in the network preview to configure their faces, or select a connection to highlight and manage only that link.
- Hold the configurable multi-select key while clicking nodes to add or remove them from the selection. Hold it and drag
  empty preview space to box-select nodes; right-click empty preview space to clear the selection.
- Drag any selected node to move the whole selection together. Reopening the configurator automatically centers the
  visible network.
- Drag nodes to arrange the layout, drag empty preview space to pan, and use the mouse wheel for proportional zoom.
- Node positions, zoom, and pan are saved with the world. Selecting a whole group or one link also controls the world preview scope.
- Valid links use the normal line color; out-of-range links use a warning color and explain their direction and distance in a tooltip.

## Upgrades

| Type         | Tiers                                       | Effect                                  |
|--------------|---------------------------------------------|-----------------------------------------|
| Speed        | Iron, Gold, Diamond, Netherite, Nether Star | Transfer speed multiplier               |
| Range        | Iron, Gold, Diamond, Netherite, Nether Star | Search radius multiplier                |
| Stack        | Iron, Gold, Diamond, Netherite, Nether Star | Transfer amount multiplier              |
| Dimension    | Single                                      | Enable cross-dimension transfer         |
| Basic Filter | Single                                      | Item whitelist/blacklist                |
| Tag Filter   | Single                                      | Filter by item/fluid tags               |
| NBT Filter   | Single                                      | Filter by NBT data (partial/full match) |

Upgrade multipliers are configurable per tier in staticlogistics.toml.

## Filtering

| Filter Type | Matches                                        |
|-------------|------------------------------------------------|
| Basic       | Specific items and/or fluids                   |
| Tag         | Items or fluids belonging to specific tags     |
| NBT         | Items with matching NBT data (partial or full) |

All filters support blacklist mode (invert match). All match strategies support items and fluids.

**Match strategies:** EXACT — CONTAINS — SMART_CONTAINS — IGNORE

## Commands (/sl)

Requires permission level 2.

| Command                             | Description                                        |
|-------------------------------------|----------------------------------------------------|
| /sl info [pos]                      | Show container + 6-face config details at position |
| /sl list                            | Show the first page of active logistics groups     |
| /sl list page <page>                | List active logistics groups by page               |
| /sl list group <group> [page]       | List nodes in one group by page                    |
| /sl stats                           | Transfer statistics overview                       |
| /sl stats recent                    | Last 20 transfers with timestamps                  |
| /sl stats top                       | Top sender and receiver nodes by count             |
| /sl stats reset                     | Reset all transfer statistics                      |
| /sl transfer <from> <to>            | Transfer all node ownership to another player      |
| /sl transfer <from> group <id> <to> | Transfer a specific group                          |
| /sl rename <owner> <old> <new>      | Rename a group                                     |
| /sl cleanup <owner>                 | Delete all nodes owned by a player                 |

## Server Config

config/staticlogistics.toml
```toml
[general]
default_radius = 16            # Default search radius (blocks)
default_tick_interval = 20     # Base interval between transfers (ticks)
auto_clean_stored_nodes = true # Auto-clean stored node refs after batch linking
item_stack_size = 64           # Items per transfer
fluid_stack_size = 250         # mB per transfer
energy_stack_size = 1024       # FE per transfer
mek_chemical_stack_size = 250
mek_heat_stack_size = 1000
ars_source_stack_size = 100
botania_mana_stack_size = 1000

[performance]
provider_size = 1000           # Expected provider index capacity (not a node limit)
load_factor = 0.75             # Cache load factor
max_bulk_entries = 100         # Max entries per sync packet
ticker_batch_size = 50         # Node/type candidates shared per server tick
ticker_time_budget_us = 1500   # Shared logistics scheduler budget per server tick (microseconds)
clean_interval = 200           # Cooldown cleanup interval (ticks)
default_cooldown = 10          # Cooldown after failed transfer (ticks)
batch_clean_threshold = 500    # Cooldown entries before batch clean
batch_clean_size = 200         # Entries cleaned per batch
context_pool_size = 100        # TransferContext pool size

[upgrades]
iron_multiplier = 2
gold_multiplier = 4
diamond_multiplier = 8
netherite_multiplier = 16
nether_star_multiplier = 64

[filter]
component_strategy_overrides = []  # Format: "namespace:id=STRATEGY"
```

## Mod Integrations

| Mod         | Transfer types               | Implementation                     |
|-------------|------------------------------|------------------------------------|
| Mekanism    | Chemical, Heat               | Internal `ResourceAdapter` bridges |
| Ars Nouveau | Source                       | Internal `ResourceAdapter` bridge  |
| Botania     | Mana                         | Internal `ResourceAdapter` bridge  |
| FTB Teams   | Team permissions & ownership | `FTBEventHandlers`                 |

Third-party integrations use the public `ResourceAdapter<C, V>` SPI. Built-in optional integrations use the internal bridge directly. See [docs/INTEGRATION.md](docs/INTEGRATION.md).

## License

GNU LGPL 3.0 — cooobird, WangXiaoJin, slime_dragon
