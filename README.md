# StaticLogistics

**[English](README.md)** | **[中文](README_ZH.md)**

A Minecraft logistics mod for Forge 1.20.1. It supports item, fluid, energy and optional mod resources,
cross-dimensional transfer, upgrades, smart filters, stable groups, FTB Teams permissions and blueprints.

## Features

- **Unified transfer pipeline** — Built-in resources share one internal pipeline; third-party resources use the public
  type-safe `ResourceAdapter<C, V>` SPI.
- **Cross-dimension transfer** — A dimension upgrade enables links between dimensions.
- **Five tool modes** — Wrench, Link as Input, Link as Output, Remove Links and Node Config.
- **Per-face configuration** — Every block face has independent channels, priority, distribution strategy, extraction
  mode, input/output state and resource type ID selection.
- **Stable group identity** — Display names can change or merge without using names as persistent identities.
- **Seven upgrade types** — Speed, range, stack, dimension, basic filter, tag filter and NBT filter.
- **Smart filtering** — Basic item filters, item/fluid tag filters and exact or partial NBT matching.
- **Configurable key mappings** — GUI modifiers and world actions are configurable in Minecraft's Controls screen.
- **Transfer events** — `PreTransferEvent` is cancellable and `PostTransferEvent` reports committed transfers by stable
  resource type ID.
- **Blueprints** — Capture, preview, rotate, paste and undo logistics configurations.
- **Jade integration** — Shows role-specific transfer and receive types, channels, strategy, priority and statistics.

## Getting Started

1. Craft the **Link Configurator**.
2. Hold it and right-click in air to create or select a group.
3. Select an input or output linking mode and right-click the required block faces.
4. Use Node Config mode to edit channels, resource types, priority, filters and container upgrades.

| Mode           | Right-click on a block face                                                 |
|----------------|-----------------------------------------------------------------------------|
| Wrench         | Remove logistics configuration while preserving supported block entity data |
| Link as Input  | Add the face as a receiver in the selected group                            |
| Link as Output | Add the face as a sender in the selected group                              |
| Remove Links   | Remove the selected group's links from the face                             |
| Node Config    | Open face settings and shared container upgrades                            |

Removing one endpoint removes the reciprocal edge. If a sender has several receivers, removing one receiver removes only
that edge; once its final edge is removed, an otherwise empty endpoint is cleaned automatically.

## Upgrades

| Type         | Tiers                                       | Effect                             |
|--------------|---------------------------------------------|------------------------------------|
| Speed        | Iron, Gold, Diamond, Netherite, Nether Star | Transfer interval multiplier       |
| Range        | Iron, Gold, Diamond, Netherite, Nether Star | Link range multiplier              |
| Stack        | Iron, Gold, Diamond, Netherite, Nether Star | Transfer amount multiplier         |
| Dimension    | Single                                      | Enables cross-dimensional transfer |
| Basic Filter | Single                                      | Item whitelist or blacklist        |
| Tag Filter   | Single                                      | Item and fluid tag filtering       |
| NBT Filter   | Single                                      | Partial or full item NBT matching  |

Container-wide upgrades are shared by every configured face on the same block and are returned when the block is
removed.

## Filtering

Filter edits are server-authoritative and are saved when closing the filter screen or returning to the node screen.

| Filter | Matches                                              |
|--------|------------------------------------------------------|
| Basic  | Selected items                                       |
| Tag    | Selected or excluded item/fluid tags                 |
| NBT    | Exact or partial NBT, optionally ignoring durability |

All filter types support blacklist mode.

## Commands

The `/sl` administration tree requires permission level 2.

| Command                               | Description                                     |
|---------------------------------------|-------------------------------------------------|
| `/sl info [pos]`                      | Show container and face configuration           |
| `/sl list`                            | List active logistics groups                    |
| `/sl stats`                           | Show transfer statistics                        |
| `/sl stats recent`                    | Show recent transfers                           |
| `/sl stats top`                       | Show top senders and receivers                  |
| `/sl stats reset`                     | Reset transfer statistics                       |
| `/sl transfer <from> <to>`            | Transfer all owned logistics data               |
| `/sl transfer <from> group <id> <to>` | Transfer one group                              |
| `/sl rename <owner> <old> <new>`      | Rename or merge a same-owner group              |
| `/sl cleanup <owner>`                 | Remove an owner's logistics data                |
| `/sl debug`                           | Show registry and cache diagnostics             |
| `/sl debug cache`                     | Show Forge capability cache statistics          |
| `/sl debug types`                     | List resource IDs and stable legacy bit offsets |

## Server Configuration

The server configuration is stored in `config/staticlogistics.toml`.

```toml
[general]
default_radius = 16
default_tick_interval = 20
auto_clean_stored_nodes = true
item_stack_size = 8
fluid_stack_size = 250
energy_stack_size = 1024
mek_gas_stack_size = 250
mek_infusion_stack_size = 250
mek_pigment_stack_size = 250
mek_slurry_stack_size = 250
mek_heat_stack_size = 1000
ars_source_stack_size = 100
botania_mana_stack_size = 1000
gtceu_stack_size = 1024

[performance]
provider_size = 1000
load_factor = 0.75
max_bulk_entries = 100
ticker_batch_size = 50
ticker_time_budget_us = 1500
clean_interval = 200
default_cooldown = 10
batch_clean_threshold = 500
batch_clean_size = 200
context_pool_size = 100

[upgrades]
iron_multiplier = 2
gold_multiplier = 4
diamond_multiplier = 8
netherite_multiplier = 16
nether_star_multiplier = 64
```

## Mod Integrations

| Mod          | Transfer types or integration                   |
|--------------|-------------------------------------------------|
| Mekanism     | Gas, infusion, pigment, slurry and heat         |
| Ars Nouveau  | Source                                          |
| Botania      | Mana                                            |
| GregTech CEu | Energy                                          |
| FTB Teams    | Team permissions, ownership and alliance access |
| Jade         | Face configuration and transfer information     |
| JEI          | Ghost ingredients for filter configuration      |

Third-party resource types use the public `ResourceAdapter<C, V>` SPI. See [docs/INTEGRATION.md](docs/INTEGRATION.md).

## Compatibility

The Forge 1.20.1 resource IDs and legacy bit offsets remain stable:

`item=0`, `fluid=1`, `energy=2`, `mek_gas=3`, `mek_infusion=4`, `mek_pigment=5`, `mek_slurry=6`, `mek_heat=7`,
`ars_source=8`, `botania_mana=9`, `gtceu_energy=10`.

Old mask-based type selections, group display names and packed face storage keys are migrated when loading.

## License

GNU LGPL 3.0 — cooobird, WangXiaoJin, slime_dragon
