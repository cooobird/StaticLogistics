# StaticLogistics

**[English](README.md)** | **[中文](README_ZH.md)**

A Minecraft logistics mod for NeoForge 1.21.1. Item, fluid, energy transfer. Cross-dimension support. Upgrade system with 7 types across 5 tiers. Smart filtering with 4 match strategies. Group management. FTB Teams permissions. Blueprints.

## Features

- **Item / Fluid / Energy transfer** — 3 built-in types; other mods can register custom types
- **Cross-dimension transfer** — Dimension upgrade for inter-dimensional logistics
- **5 tool modes** — Wrench, Link as Input, Link as Output, Remove Links, Node Config
- **Per-face configuration** — each of 6 block faces has independent: channels (1–16), priority, distribution strategy, extraction mode, input/output toggle, type mask
- **7 upgrade types, 5 tiers each** — Speed, Range, Stack (Iron → Gold → Diamond → Netherite → Nether Star), plus Dimension, Basic Filter, Tag Filter, NBT Filter
- **Smart filtering** — Basic (item whitelist/blacklist), Tag (item + fluid tags), NBT (exact/partial NBT matching) with 4 match strategies: EXACT, CONTAINS, SMART_CONTAINS, IGNORE
- **2 extraction modes** — Sequential, Slot Round-Robin
- **5 distribution strategies** — Sequential, Round-Robin, Nearest, Furthest, Random
- **Group management** — named groups for per-group sync, ownership transfer, rename, cleanup
- **FTB Teams integration** — team-based ownership and permissions
- **Blueprints** — save logistics config, preview placement with rotation, paste to blocks
- **Full /sl command tree** — info, list, stats, transfer, rename, cleanup, strategies
- **Configurable performance** — ticker batch size, cooldown intervals, cache sizes, object pool size
## Getting Started

1. Craft the **Link Configurator**
2. Open GUI (right-click in air) → create or select a group
3. Switch tool mode via left sidebar → right-click block faces to link
4. Add upgrades and configure filters in the **Node Config** screen (mode 4)

| Mode           | Right-click on block face                     |
|----------------|-----------------------------------------------|
| Wrench         | Remove logistics config (preserves block NBT) |
| Link as Input  | Mark as receiver                              |
| Link as Output | Mark as sender                                |
| Remove Links   | Remove all links from this face               |
| Node Config    | Open face settings + container upgrades       |

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
| /sl list                            | List all active groups with member nodes           |
| /sl stats                           | Transfer statistics overview                       |
| /sl stats recent                    | Last 20 transfers with timestamps                  |
| /sl stats top                       | Top sender and receiver nodes by count             |
| /sl stats reset                     | Reset all transfer statistics                      |
| /sl transfer <from> <to>            | Transfer all node ownership to another player      |
| /sl transfer <from> group <id> <to> | Transfer a specific group                          |
| /sl rename <owner> <old> <new>      | Rename a group                                     |
| /sl cleanup <owner>                 | Delete all nodes owned by a player                 |
| /sl strategies [page]               | List registered component match strategies         |

## Server Config

config/staticlogistics.toml
```toml
[general]
default_radius = 16            # Default search radius (blocks)
default_tick_interval = 20     # Base interval between transfers (ticks)
auto_clean_stored_nodes = true # Auto-clean stored node refs after batch linking
item_stack_size = 8            # Items per transfer
fluid_stack_size = 250         # mB per transfer
energy_stack_size = 1024       # FE per transfer
mek_chemical_stack_size = 250
mek_heat_stack_size = 1000
ars_source_stack_size = 100
botania_mana_stack_size = 1000

[performance]
provider_size = 1000           # Provider cache entries
load_factor = 0.75             # Cache load factor
target_size = 50               # Targets cached per face
max_bulk_entries = 100         # Max entries per sync packet
ticker_batch_size = 50         # Nodes per tick
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

| Mod         | Transfer types               |
|-------------|------------------------------|
| Mekanism    | Chemical, Heat               |
| Ars Nouveau | Source                       |
| Botania     | Mana                         |
| FTB Teams   | Team permissions & ownership |

## License

GNU LGPL 3.0 — cooobird, WangXiaoJin, slime_dragon