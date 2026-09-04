# StaticLogistics

**[English](README.md)** | **[中文](README_ZH.md)**

StaticLogistics is a pipe-free logistics mod for NeoForge 1.21.1. It treats an exact block face as a node, transfers
items, fluids, energy, and optional integration resources across configurable distances and dimensions, and uses one
Link Configurator for linking, grouping, previewing, and configuration.

## Current Architecture

```text
Owner
└─ Group (stable internal ID + editable display name)
   └─ Connection (individually nameable)
      ├─ Endpoint A: dimension + block position + face
      └─ Endpoint B: dimension + block position + face
```

- **A node is a block face, not a whole block.** Different faces of one container are distinct nodes and can have
  different input/output roles.
- **A connection is only the topology between two endpoints.** Runtime direction follows the enabled roles on both ends,
  so one connection can be one-way, bidirectional, or temporarily inactive.
- **Groups have explicit owners.** Display names are not identities; renaming preserves links, and renaming to an
  existing same-owner group merges them.
- **Output types belong to output faces.** Accepted types shown on an input face are derived from connected outputs, not
  stored as another editable list.
- **Speed, range, stack, and dimension capabilities belong to the container.** Every linked face on the same block
  shares one container upgrade inventory.
- **Filters and transfer rules belong to a face.** Input/output filters, priority, stock keeping, distribution,
  extraction, and output types are persisted per face.
- **The server is authoritative.** It validates and commits group identity, ownership, permissions, links,
  configuration, and item returns.

The old standalone node-configuration mode has been removed. The Link Configurator is the only entry point for node
settings, group management, and network preview.

## Main Features

- One unified configurator for tool modes, resource types, topology, groups, connections, node settings, and inventory
- Face-addressed links that distinguish dimension, position, and side
- Interactive topology preview with pan, zoom, node dragging, box selection, multi-selection, group dragging, batch
  configuration, and link highlighting
- Owner-scoped groups and individually named connections with create, rename, merge, and confirmed deletion
- World rendering scoped to a whole group or a single connection
- Container-shared speed, range, stack, and cross-dimensional upgrades
- Independent input and output filters
- Runtime-extensible transfer type registry
- Blueprint capture, preview, paste, rotation, movement, and undo
- Player ownership, FTB Teams access, and administrative commands
- Public resource-adapter API and pre/post transfer events

## Getting Started

1. Craft and hold a **Link Configurator**.
2. Right-click normally to open it, then create and select a group.
3. Select the resource types that new output faces should enable by default. A connection may be created with no type
   and configured later.
4. Select either “Select point as Insert” or “Select point as Extract,” then sneak-right-click one or more block faces
   to record them.
5. Switch to the opposite selection mode and sneak-right-click the other endpoint. Every recorded face is linked to the
   current face.
6. Select a node in the network preview and configure its roles, filters, upgrades, and transfer rules in the lower
   panel.

Example: to send one chest into three machines, record the three machine faces in Insert mode, switch to Extract mode,
and sneak-right-click the chest face.

### Configurator Controls

| Action                                   | Result                                            |
|------------------------------------------|---------------------------------------------------|
| Normal right-click                       | Open the unified configurator                     |
| Sneak + right-click a node               | Perform the current tool mode                     |
| Configured mode-switch key + wheel       | Change tool mode                                  |
| Configured bulk-select key + right-click | Select connected matching blocks and usable faces |
| Sneak-right-click air                    | Clear recorded nodes                              |

### Tool Modes

| Mode                    | Purpose                                           |
|-------------------------|---------------------------------------------------|
| Wrench                  | Perform supported wrench or dismantle actions     |
| Select point as Insert  | Record endpoints that receive resources           |
| Select point as Extract | Record endpoints that provide resources           |
| Remove Links            | Remove links on this face from the selected group |

When a connection, group, or endpoint role releases filters or upgrades, items return to the acting player's inventory
first and only drop when the inventory is full.

## Configurator Screen

The screen has four clear responsibilities:

- **Top toolbar:** tool mode, resource-type pages, and default types for new links; when an output node is selected, it
  edits that face instead.
- **Network preview:** the real face-level topology of the selected group or connection.
- **Groups and connections:** preview scope, expansion, creation, rename, and deletion.
- **Node configuration:** settings for one or multiple selected preview nodes.

Tool mode and resource selection are independent states. Group selection and single-connection focus are also stored
independently.

### Network Preview

- Click a node to select and configure it; click a line to focus that connection.
- Hold the configurable multi-select key and click nodes to add or remove them.
- Hold the same key and drag empty space to box-select nodes.
- Drag a selected node to move the selection together; drag normal empty space to pan.
- Scroll to zoom proportionally around the cursor.
- Right-click empty space to clear the selection.
- Custom node positions are stored locally on the client and restored by stable group identity; reopening the screen
  recenters the current topology.
- Normal links use the regular color. Out-of-range or currently blocked links use a warning color and explain the reason
  on hover.
- Faces at the same block position remain separate nodes.

Batch apply copies the source node's resource types, filters, container upgrades, and side-specific settings to every
selected node. The server validates the operation and settles all inventory changes atomically.

### Groups and Connections

- Groups and individual connections can be selected, renamed, or deleted.
- Destructive actions use confirmation dialogs.
- Renaming a group to an existing same-owner name merges both groups and their links.
- Selecting a group previews all of it; selecting one connection limits both UI and world preview to that connection.
- Deleting a connection removes reciprocal references from both endpoints. Disconnected faces do not retain meaningless
  input/output roles.

## Node Configuration Model

### Input Side

- Enable or disable the input role
- Input filter
- Priority
- Stock keeping
- Read-only accepted types derived from connected outputs

### Output Side

- Enable or disable the output role
- Output filter
- Output resource types
- Container-shared upgrades
- Target distribution strategy
- Item-slot extraction strategy

Disabling one role pauses that direction without deleting the topology. Re-enabling it continues using the existing
connection.

## Transfer Types

Built-in types:

- Items
- Fluids
- Energy

Optional integrations register extra types only when their mods are loaded:

| Mod         | Resource types  |
|-------------|-----------------|
| Mekanism    | Chemicals, Heat |
| Ars Nouveau | Source          |
| Botania     | Mana            |

The type bar is backed by the runtime registry and supports pagination. Third-party mods can register stable types
through the public `ResourceAdapter<C, V>` SPI. See [the integration guide](docs/INTEGRATION.md).

An output face with no selected type keeps its links but starts no transfers. Jade's “Transfer Types” comes from the
actual output selection; “Accepted Types” is derived from connected outputs rather than guessed from available
capabilities.

## Upgrades

Speed, range, and stack upgrades have Iron, Gold, Diamond, Netherite, and Nether Star tiers. The Dimension Upgrade is a
separate single-tier item.

| Container capability | Effect                                                                                                                     |
|----------------------|----------------------------------------------------------------------------------------------------------------------------|
| Speed                | Reduces the interval between container activations                                                                         |
| Range / Dimension    | Range increases maximum distance; Dimension permits cross-dimensional transfer and removes the normal distance restriction |
| Stack                | Increases the resource amount transferable per activation                                                                  |

Upgrades belong to the container, not to one connection. They remain while the same container still has another valid
link and are returned when the last relevant link is removed or the container is dismantled.

Tier multipliers are configurable in `config/staticlogistics.toml`. Reloaded values apply to existing networks without
reconnecting them.

## Filters

Filters are separate plugin items, not tiers of the performance upgrades.

| Filter       | Matches                                                                                  |
|--------------|------------------------------------------------------------------------------------------|
| Basic Filter | Specific item or fluid identities                                                        |
| Tag Filter   | Included and explicitly excluded item/fluid tags                                         |
| NBT Filter   | An item and its data components, with partial/full matching and optional damage ignoring |

All three filters support whitelist and blacklist behavior. Input and output sides have independent slots. Basic and tag
filters retain only identity data needed for matching; NBT filters preserve the template components needed for
comparison.

## Transfer Rules

### Target Distribution

- Sequential
- Round-robin
- Nearest
- Furthest
- Random

### Item Extraction

- Sequential
- Slot round-robin

Item extraction scans usable container slots and skips filtered or non-insertable candidates instead of remaining stuck
on the first slots. Large networks are scheduled with fair cursors, failure cooldowns, candidate batches, and a soft
per-tick time budget so one network cannot monopolize a server tick.

Transfers only access loaded endpoints that still expose the required capability; remote links do not force-load chunks.
Breaking or correctly dismantling an endpoint removes its reciprocal links.

## Blueprints

The Logistics Blueprint copies existing logistics configuration; it does not replace the configurator:

1. Expand a group in the blueprint group screen.
2. Select a whole group or one connection to restrict the world preview.
3. Select two region corners to capture.
4. Preview at the destination, move or rotate, then confirm paste.
5. Use the configurable undo key to revert the last paste when needed.

Paste validates permissions, destination bounds, and required upgrade items. The blueprint tooltip reports the captured
region and upgrade requirements.

## Ownership and Permissions

- The first write claims a node for an owner; later mutations are checked against that owner.
- Groups use stable internal identities; display names are player-facing labels only.
- With FTB Teams installed, team-member access follows the shared team policy.
- Administrative commands require permission level 2.

## Commands

| Command                               | Description                                         |
|---------------------------------------|-----------------------------------------------------|
| `/sl info [pos]`                      | Inspect a container and all six face configurations |
| `/sl list`                            | Show the first group page                           |
| `/sl list page <page>`                | List groups by page                                 |
| `/sl list group <group> [page]`       | List one group by page                              |
| `/sl stats`                           | Show transfer statistics                            |
| `/sl stats recent`                    | Show recent transfers                               |
| `/sl stats top`                       | Show top sending and receiving nodes                |
| `/sl stats reset`                     | Reset statistics                                    |
| `/sl transfer <from> <to>`            | Transfer all logistics ownership                    |
| `/sl transfer <from> group <id> <to>` | Transfer one group                                  |
| `/sl rename <owner> <old> <new>`      | Rename or merge a group                             |
| `/sl cleanup <owner>`                 | Remove one player's logistics data                  |

## Server Configuration

Current `config/staticlogistics.toml` structure:

```toml
[general]
gameplay_mode = "ADVANCED"
default_radius = 16
default_tick_interval = 20
auto_clean_stored_nodes = true
item_stack_size = 64
fluid_stack_size = 250
energy_stack_size = 1024
mek_chemical_stack_size = 250
mek_heat_stack_size = 1000
ars_source_stack_size = 100
botania_mana_stack_size = 1000

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

- `gameplay_mode` accepts `SIMPLE` (a 1-tick transfer interval, `999999999` per resource transfer, and no distance or
  dimension limits) or `ADVANCED` (default; configured values and installed upgrades apply). Changes hot-reload without
  deleting installed upgrades.
- Resource `*_stack_size` values are base amounts per transfer.
- `ticker_batch_size` is the candidate polling base per tick, not a total-node limit.
- `ticker_time_budget_us` is a soft budget shared by all dimensions; unfinished work resumes on later ticks.
- `provider_size` only pre-sizes the scheduler index.
- Upgrade multipliers and base transfer amounts hot-reload into existing networks.

## Save Compatibility

- Resource types use stable IDs and bit offsets, so registry ordering does not disconnect existing links.
- Legacy type masks, group names, links, and face-storage data migrate on load.
- Group rename changes only the display name, not its stable identity.
- Network-preview layout is stored locally on the client and never changes the server transfer topology.

## Developer Extension

- Public adapter SPI: `StaticLogisticsApi.resourceAdapters()`
- Cancellable `PreTransferEvent`
- Post-commit `PostTransferEvent`
- Registrable distribution strategies

Third parties should depend only on public API packages; internal `LogisticsResource` and node implementations are not
stable integration contracts. See [docs/INTEGRATION.md](docs/INTEGRATION.md).

## License

GNU LGPL 3.0 — cooobird, WangXiaoJin, slime_dragon
