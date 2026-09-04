# StaticLogistics

**[English](README.md)** | **[中文](README_ZH.md)**

StaticLogistics is a pipe-free logistics mod for Forge 1.20.1. It treats an exact block face as a node, transfers items,
fluids, energy, and optional integration resources across configurable distances and dimensions, and uses one Link
Configurator for linking, grouping, previewing, and configuration.

## Current Architecture

```text
Owner
└─ Group (stable internal ID + editable display name)
   └─ Connection (individually nameable)
      ├─ Endpoint A: dimension + block position + face
      └─ Endpoint B: dimension + block position + face
```

- Nodes represent exact block faces. Different sides of the same container are distinct nodes.
- Connections persist topology between two endpoints. Runtime direction follows the enabled input/output roles and may
  be one-way, bidirectional, or inactive.
- Groups have explicit owners and stable internal identities. Renaming preserves links; renaming to an existing
  same-owner group merges them.
- Output types are stored on output faces. Accepted types on input faces are derived from connected outputs.
- Speed, range, stack, and dimension upgrades are shared by every linked face on one container.
- Input/output filters, priority, stock keeping, distribution, extraction, and output selection are face-specific.
- The server validates permissions, links, configuration, inventory settlement, and persistence.

The old standalone node configuration mode has been removed. The Link Configurator is the only entry point for node
settings, group management, and network preview.

## Main Features

- Unified configurator for tool modes, resource types, topology, groups, connections, node settings, and inventory
- Face-addressed links across distance and dimensions
- Interactive preview with pan, zoom, drag, box selection, multi-selection, group dragging, and batch configuration
- Owner-scoped groups and individually named connections with create, rename, merge, and confirmed deletion
- World preview scoped to a whole group or a single connection
- Container-shared speed, range, stack, and dimension upgrades
- Independent input and output filters
- Runtime-extensible transfer type registry
- Blueprint capture, preview, move, rotate, paste, and undo
- Player ownership, FTB Teams access, and administrative commands
- Public resource-adapter API and transfer events

## Getting Started

1. Craft and hold a **Link Configurator**.
2. Right-click normally, then create and select a group.
3. Choose the resource types enabled by default on new output faces. Empty-type links may be configured later.
4. Select Insert or Extract mode and sneak-right-click one or more block faces to record them.
5. Switch to the opposite mode and sneak-right-click the other endpoint to link every recorded face to it.
6. Select nodes in the preview and edit their roles, filters, upgrades, and transfer rules below.

Example: record three machine faces in Insert mode, switch to Extract mode, then sneak-right-click the supplying chest.

### Basic Controls

| Action                                   | Result                                            |
|------------------------------------------|---------------------------------------------------|
| Normal right-click                       | Open the unified configurator                     |
| Sneak + right-click a node               | Perform the current tool mode                     |
| Configured mode key + wheel              | Change tool mode                                  |
| Configured bulk-select key + right-click | Select connected matching blocks and usable faces |
| Sneak-right-click air                    | Clear recorded nodes                              |

| Tool mode               | Purpose                                           |
|-------------------------|---------------------------------------------------|
| Wrench                  | Perform supported wrench or dismantle actions     |
| Select point as Insert  | Record receiving endpoints                        |
| Select point as Extract | Record providing endpoints                        |
| Remove Links            | Remove links on this face from the selected group |

Released filters and upgrades return to the acting player's inventory first and only drop when the inventory is full.

## Configurator and Network Preview

The screen is divided into the top toolbar, network preview, group/connection directory, and node configuration. Tool
mode, resource types, group selection, and single-connection focus are independent states.

- Click nodes to configure them and lines to focus individual connections.
- Hold the configurable multi-select key while clicking to add or remove nodes; hold it while dragging empty space to
  box-select.
- Drag a selected node to move the whole selection; drag normal empty space to pan.
- Scroll to zoom around the cursor and right-click empty space to clear selection.
- Node positions are stored locally on the client and restored by stable group identity; reopening recenters the current
  topology.
- Different faces at the same block position remain separate nodes.
- Blocked or out-of-range connections use a warning color and explain the reason on hover.
- Batch apply copies the source node's types, filters, container upgrades, and side settings to all selected nodes.

### Groups and Connections

- Groups and connections can be selected, renamed, and deleted; destructive actions require confirmation.
- Renaming to an existing same-owner group merges both groups and their links.
- Selecting a group previews all links; selecting one connection restricts UI and world preview to it.
- Connection deletion removes reciprocal endpoint references. Disconnected faces do not retain meaningless roles.

## Node Configuration

### Input Side

- Input role toggle
- Input filter
- Priority
- Stock keeping
- Read-only accepted types derived from connected outputs

### Output Side

- Output role toggle
- Output filter
- Output resource types
- Container-shared upgrades
- Target distribution strategy
- Item-slot extraction strategy

Disabling a role pauses that direction without deleting the topology.

## Transfer Types

Items, fluids, and energy are built in. Optional integrations register extra types when loaded:

| Mod          | Resource types                       |
|--------------|--------------------------------------|
| Mekanism     | Gas, Infusion, Pigment, Slurry, Heat |
| Ars Nouveau  | Source                               |
| Botania      | Mana                                 |
| GregTech CEu | EU                                   |

An output with no selected types keeps its links but transfers nothing. Jade reads output types from the actual
selection and derives accepted input types from connected outputs instead of guessing from capabilities.

Third parties can register stable types through the public `ResourceAdapter<C, V>` SPI.
See [the integration guide](docs/INTEGRATION.md).

## Upgrades

Speed, range, and stack upgrades have Iron, Gold, Diamond, Netherite, and Nether Star tiers. Dimension is a separate
single-tier upgrade.

| Container capability | Effect                                                                                                      |
|----------------------|-------------------------------------------------------------------------------------------------------------|
| Speed                | Reduces transfer activation interval                                                                        |
| Range / Dimension    | Increases maximum distance; Dimension permits cross-dimensional transfer and removes normal distance limits |
| Stack                | Increases transferable amount per activation                                                                |

Upgrades belong to the container. They remain while another link uses the same container and return after the last
relevant link is removed or the container is dismantled. Hot-reloaded base amounts and multipliers apply to existing
networks.

## Filters

| Filter       | Matches                                                             |
|--------------|---------------------------------------------------------------------|
| Basic Filter | Specific item or fluid identities                                   |
| Tag Filter   | Included and explicitly excluded item/fluid tags                    |
| NBT Filter   | Item data with partial/full comparison and optional damage ignoring |

Filters are separate plugin items, not performance-upgrade tiers. All support whitelist/blacklist behavior, and
input/output sides persist independent rules.

## Transfer Rules and Scheduling

Target distribution supports Sequential, Round-robin, Nearest, Furthest, and Random. Item extraction supports Sequential
and Slot round-robin.

Extraction scans usable slots and skips filtered or non-insertable candidates. Large networks use fair cursors, failure
cooldowns, candidate batches, and a soft per-tick time budget so one network cannot monopolize the server. Transfers
only access loaded endpoints with valid capabilities and never force-load remote chunks.

Breaking or correctly dismantling an endpoint removes its reciprocal links.

## Blueprints

The Logistics Blueprint copies existing logistics configuration:

1. Expand a group and choose the whole group or one connection.
2. Confirm the intended scope through the world preview.
3. Select two region corners.
4. Move or rotate the destination preview, then paste.
5. Use the configurable undo key to revert the last paste.

Paste validates permissions, bounds, and required upgrades.

## Ownership and Permissions

- The first write claims the node for an owner.
- Group identities remain stable across rename.
- FTB Teams, when installed, supplies team-member access policy.
- Administrative commands require permission level 2.

## Commands

| Command                               | Description                           |
|---------------------------------------|---------------------------------------|
| `/sl info [pos]`                      | Inspect a container and all six faces |
| `/sl list`                            | Show the first group page             |
| `/sl list page <page>`                | List groups by page                   |
| `/sl list group <group> [page]`       | List one group by page                |
| `/sl stats`                           | Show transfer statistics              |
| `/sl stats recent`                    | Show recent transfers                 |
| `/sl stats top`                       | Show top senders and receivers        |
| `/sl stats reset`                     | Reset statistics                      |
| `/sl transfer <from> <to>`            | Transfer all logistics ownership      |
| `/sl transfer <from> group <id> <to>` | Transfer one group                    |
| `/sl rename <owner> <old> <new>`      | Rename or merge a group               |
| `/sl cleanup <owner>`                 | Remove one player's logistics data    |

## Server Configuration

```toml
[general]
gameplay_mode = "ADVANCED"
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

`gameplay_mode` accepts `SIMPLE` (a 1-tick transfer interval, `999999999` per resource transfer, no distance or
dimension limits, and hidden upgrade slots) or `ADVANCED` (default; configured values and installed upgrades apply).

`ticker_batch_size` is a candidate polling base, not a node limit. `ticker_time_budget_us` is a soft budget shared by
all dimensions. `provider_size` only pre-sizes the scheduler index.

## Save Compatibility

Forge 1.20.1 resource IDs and stable bit offsets remain unchanged:

`item=0`, `fluid=1`, `energy=2`, `mek_gas=3`, `mek_infusion=4`, `mek_pigment=5`, `mek_slurry=6`, `mek_heat=7`,
`ars_source=8`, `botania_mana=9`, `gtceu_energy=10`.

Legacy type masks, group names, links, and face data migrate on load. Preview layout is stored locally on the client and
never changes server transfer topology.

## Developer Extension

- `StaticLogisticsApi.resourceAdapters()` adapter SPI
- Cancellable `PreTransferEvent`
- `PostTransferEvent`
- Registrable distribution strategies

Third parties should only depend on public API packages. See [docs/INTEGRATION.md](docs/INTEGRATION.md).

## License

GNU LGPL 3.0 — cooobird, WangXiaoJin, slime_dragon
