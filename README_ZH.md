# StaticLogistics

**[English](README.md)** | **[中文](README_ZH.md)**

NeoForge 1.21.1 的 Minecraft 物流模组。物品、流体、能量传输。跨维度支持。7 种升级类型 × 5 个等级。4 种匹配策略的智能过滤。分组管理。FTB Teams 权限。蓝图系统。

## 特性

- **物品 / 流体 / 能量传输** — 内置 3 种传输类型，支持其他模组注册自定义类型
- **跨维度传输** — 维度升级插件启用跨维度物流
- **5 种工具模式** — 扳手、链接为输入、链接为输出、移除链接、节点配置
- **每面独立配置** — 6 个方块面各有独立设置：频道（1–16）、优先级、分发策略、提取模式、输入/输出开关、类型掩码
- **7 种升级 × 5 等级** — 速度、范围、堆叠（铁 → 金 → 钻石 → 下界合金 → 下界之星），加维度升级、基础过滤、标签过滤、NBT 过滤
- **智能过滤** — 基础过滤（物品白名单/黑名单）、标签过滤（物品+流体标签）、NBT 过滤（精确/部分 NBT 匹配），4 种匹配策略：EXACT、CONTAINS、SMART_CONTAINS、IGNORE
- **2 种提取模式** — 顺序提取、槽位轮询
- **5 种分发策略** — 顺序、轮询、最近、最远、随机
- **分组管理** — 命名分组，按组同步、转移所有权、重命名、清理
- **FTB Teams 集成** — 基于队伍的所有权和权限
- **蓝图系统** — 保存物流配置、旋转预览、粘贴到方块
- **完整 /sl 命令树** — info、list、stats、transfer、rename、cleanup、strategies
- **可调优性能** — ticker 批处理大小、冷却间隔、缓存大小、对象池大小

## 快速上手

1. 合成**链接配置器**
2. 空手右键打开 GUI → 创建或选择分组
3. 左侧边栏切换工具模式 → 右键方块面进行链接
4. 在**节点配置**界面（模式 4）添加升级和配置过滤器

| 模式    | 右键方块面操作          |
|-------|------------------|
| 扳手    | 移除物流配置（保留方块 NBT） |
| 链接为输入 | 标记为接收面           |
| 链接为输出 | 标记为发送面           |
| 移除链接  | 移除该面所有链接         |
| 节点配置  | 打开面设置 + 容器升级     |

## 升级系统

| 类型     | 等级               | 效果                  |
|--------|------------------|---------------------|
| 速度     | 铁、金、钻石、下界合金、下界之星 | 传输速度倍率              |
| 范围     | 铁、金、钻石、下界合金、下界之星 | 搜索半径倍率              |
| 堆叠     | 铁、金、钻石、下界合金、下界之星 | 每次传输数量倍率            |
| 维度     | 单级               | 启用跨维度传输             |
| 基础过滤   | 单级               | 物品白名单/黑名单           |
| 标签过滤   | 单级               | 按物品/流体标签过滤          |
| NBT 过滤 | 单级               | 按 NBT 数据过滤（部分/完全匹配） |

升级倍率可在 staticlogistics.toml 中按等级独立配置。

## 过滤系统

| 过滤类型   | 匹配方式               |
|--------|--------------------|
| 基础过滤   | 指定物品和/或流体          |
| 标签过滤   | 属于特定标签的物品或流体       |
| NBT 过滤 | NBT 数据匹配的物品（部分或完全） |

全部支持黑名单模式。全部匹配策略支持物品和流体。

**匹配策略**：EXACT — CONTAINS — SMART_CONTAINS — IGNORE

## 命令 (/sl)

需要权限等级 2。

| 命令                                  | 说明              |
|-------------------------------------|-----------------|
| /sl info [pos]                      | 查看容器 + 6 面配置详情  |
| /sl list                            | 列出所有活跃分组及节点     |
| /sl stats                           | 传输统计概览          |
| /sl stats recent                    | 最近 20 条传输（含时间戳） |
| /sl stats top                       | Top 发送/接收节点排行   |
| /sl stats reset                     | 重置传输统计          |
| /sl transfer <from> <to>            | 转移全部节点所有权       |
| /sl transfer <from> group <id> <to> | 转移指定分组          |
| /sl rename <owner> <old> <new>      | 重命名分组           |
| /sl cleanup <owner>                 | 删除某玩家全部节点       |
| /sl strategies [page]               | 列出注册的组件匹配策略     |

## 服务端配置

config/staticlogistics.toml
```toml
[general]
default_radius = 16            # 默认搜索半径（格）
default_tick_interval = 20     # 传输基础间隔（tick）
auto_clean_stored_nodes = true # 批量链接后自动清理存储节点引用
item_stack_size = 8            # 每次传输物品数
fluid_stack_size = 250         # 每次传输流体（mB）
energy_stack_size = 1024       # 每次传输能量（FE）
mek_chemical_stack_size = 250
mek_heat_stack_size = 1000
ars_source_stack_size = 100
botania_mana_stack_size = 1000

[performance]
provider_size = 1000           # 提供者缓存条目数
load_factor = 0.75             # 缓存加载因子
target_size = 50               # 每面目标缓存数
max_bulk_entries = 100         # 同步包最大条目
ticker_batch_size = 50         # 每 tick 处理节点数
clean_interval = 200           # 冷却清理间隔（tick）
default_cooldown = 10          # 传输失败后冷却（tick）
batch_clean_threshold = 500    # 触发批量清理的冷却条目阈值
batch_clean_size = 200         # 每批清理条目数
context_pool_size = 100        # TransferContext 对象池大小

[upgrades]
iron_multiplier = 2            # 铁升级倍率
gold_multiplier = 4            # 金升级倍率
diamond_multiplier = 8         # 钻石升级倍率
netherite_multiplier = 16      # 下界合金升级倍率
nether_star_multiplier = 64    # 下界之星升级倍率

[filter]
component_strategy_overrides = []  # 格式："命名空间:组件ID=策略"
```

## 模组集成

| 模组          | 传输类型     |
|-------------|----------|
| Mekanism    | 化学品、热量   |
| Ars Nouveau | 魔源       |
| Botania     | 魔力       |
| FTB Teams   | 队伍权限和所有权 |

## 许可证

GNU LGPL 3.0 — cooobird, WangXiaoJin, slime_dragon