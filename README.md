# StaticLogistics

**[English](README.md)** | **[中文](README_ZH.md)**

A modern logistics mod for Minecraft featuring flexible item/fluid/energy transfer, cross-dimensional support, upgrade system, smart filtering, group management, and configurable performance settings.

## Features

### Core Functionality
- **Flexible Transfer**: Support for items, fluids, and energy transfer
- **Cross-Dimensional**: Transfer resources across different dimensions
- **Upgrade System**: Enhance your logistics with various upgrades (speed, range, stack, dimension)
- **Smart Filtering**: Advanced filtering with tag-based and NBT-based filters
- **Group Management**: Organize your logistics nodes into groups for easier management
- **Configurable Performance**: Fine-tune performance settings to match your server's capabilities

### Upgrades
- **Speed Upgrades**: Increase transfer speed (Iron, Gold, Diamond, Netherite, Nether Star)
- **Range Upgrades**: Extend transfer range (Iron, Gold, Diamond, Netherite, Nether Star)
- **Stack Upgrades**: Increase stack size for transfers (Iron, Gold, Diamond, Netherite, Nether Star)
- **Dimension Upgrade**: Enable cross-dimensional transfer
- **Filter Upgrades**: Add filtering capabilities (Basic, Tag, NBT)

### Filtering
- **Item Tags**: Filter by item tags
- **Block Tags**: Filter by block tags
- **Fluid Tags**: Filter by fluid tags
- **NBT Data**: Filter by NBT data
- **Match Strategies**: EXACT, CONTAINS, SMART_CONTAINS, IGNORE

## Installation

### Requirements
- Minecraft 1.21.1
- NeoForge 21.1.219 or higher

### Optional Dependencies
- JEI (Just Enough Items) - For recipe viewing
- Mekanism - For chemical and heat transfer
- Ars Nouveau - For source transfer
- PneumaticCraft: Repressurized - For pressure and heat transfer

### Installation Steps
1. Download the latest version of StaticLogistics
2. Install NeoForge for Minecraft 1.21.1
3. Place the StaticLogistics jar file in your `mods` folder
4. Launch the game

## Usage

### Getting Started
1. **Craft a Link Configurator**: The main tool for setting up logistics
2. **Configure a Node**: Shift + Right-click on a block to configure it
3. **Set Transfer Mode**: Choose between Insert (存入) or Extract (提取)
4. **Set Transfer Type**: Select items, fluids, or energy
5. **Create Links**: Link nodes together to start transferring resources

### Link Configurator Modes
- **Wrench**: Remove logistics blocks while preserving their NBT data (requires Shift + Right-click)
- **Link as Insert**: Mark a node as a destination (resources will be inserted here)
- **Link as Extract**: Mark a node as a source (resources will be extracted from here)
- **Remove Links**: Remove all links connected to a node face
- **Configure Node Face**: Open detailed configuration for a specific face
- **Configure Node Container**: Open detailed configuration for the container

### Configuration
- **Face Configuration**: Set input/output modes, channels, priorities, and strategies
- **Container Configuration**: Configure upgrades and their multipliers
- **Filter Configuration**: Set up advanced filters with tags and NBT data

### Channels
- Channels 1-16 for organizing your logistics
- Nodes only connect if they have the same channel
- Set channels in the face configuration GUI

## Configuration

### Server Configuration (`staticlogistics-server.toml`)

#### General Settings
```toml
[general]
default_radius = 16              # Default transfer radius
default_tick_interval = 20       # Base tick interval
max_transfer_limit = 10000000    # Max transfer per tick
auto_clean_stored_nodes = false  # Auto clean stored nodes
```

#### Cache Settings
```toml
[cache]
provider_size = 1000           # Provider cache size
load_factor = 0.75              # Cache load factor
target_size = 50                 # Target cache size
global_target_size = 500         # Global target cache size
```

#### Network Settings
```toml
[network]
max_bulk_entries = 100          # Max bulk entries per packet
```

#### Performance Settings
```toml
[performance]
ticker_batch_size = 50          # Nodes processed per tick
clean_interval = 200             # Cleanup interval (ticks)
default_cooldown = 10           # Default cooldown (ticks)
batch_clean_threshold = 500      # Batch cleanup threshold
batch_clean_size = 200           # Batch cleanup size
context_pool_size = 100          # Context pool size
```

## Mod Integrations

### Supported Mods
- **Mekanism**: Chemical and heat transfer
- **Ars Nouveau**: Source transfer
- **PneumaticCraft: Repressurized**: Pressure and heat transfer
- **Create**: Additional compatibility
- **FTB Teams**: Team-based permissions

### Integration Settings
Integration-specific stack sizes and multipliers can be configured in the server config file.

## Commands

### `/sl info <pos>`
Display logistics information at a specific position.

### `/sl list [page]`
List all active logistics groups.

### `/sl strategies [page]`
List data component match strategies.

### `/sl transfer <player> <group_id>`
Transfer links from one player to another.

### `/sl rename <group_id> <new_name>`
Rename a logistics group.

### `/sl cleanup <player>`
Clean up all links owned by a player.

## Performance Tips

1. **Adjust Batch Size**: If you experience lag, reduce `ticker_batch_size`
2. **Optimize Cache**: Increase cache sizes for better performance on large servers
3. **Network Settings**: Lower `max_bulk_entries` if you have network issues
4. **Clean Up**: Use the cleanup command to remove unused links

## Troubleshooting

### Links not working?
- Check that both nodes have the same channel
- Verify that both nodes are in the same dimension (unless using dimension upgrade)
- Ensure that the transfer type matches (item/fluid/energy)

### High memory usage?
- Reduce cache sizes in the configuration
- Use the cleanup command to remove unused links

### Network issues?
- Reduce `max_bulk_entries` in the network settings
- Check your server's network configuration

## License

This mod is licensed under GNU LGPL 3.0.

## Credits

- **Authors**: cooobird, WangXiaoJin
- **Contributors**: Thank you to everyone who has contributed to this project

## Support

For issues, suggestions, or questions, please visit the project's issue tracker.

## Changelog

See [changelog.md](changelog.md) for detailed version history.