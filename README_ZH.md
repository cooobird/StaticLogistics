# StaticLogistics

**[English](README.md)** | **[中文](README_ZH.md)**

Forge 1.20.1 的 Minecraft 物流模组。支持物品、流体、能量与联动资源传输，并提供可视化网络管理、跨维度链接、升级、过滤器、分组权限和蓝图功能。

## 特性

- **统一链接配置器** — 在一个界面中管理分组、链接、网络预览、两端配置、升级、过滤器、资源类型和玩家物品栏
- **可交互网络预览** — 支持选择和拖动节点、平移、缩放、链接高亮、方向箭头、超距提示和布局持久化
- **跨维度传输** — 维度升级插件同时解除维度与常规距离限制
- **4 种工具模式** — 扳手、链接为输入、链接为输出、移除链接
- **每面独立配置** — 每个连接面可独立控制输入/输出；输入端配置过滤器、优先级和存量维持，输出端配置过滤器、升级、分发策略、提取策略和资源类型
- **7 种升级 × 5 等级** — 速度、范围、堆叠（铁 → 金 → 钻石 → 下界合金 → 下界之星），加维度升级、基础过滤、标签过滤、NBT 过滤
- **智能过滤** — 基础过滤（物品白名单/黑名单）、标签过滤（物品+流体标签）、NBT 过滤（精确/部分 NBT 匹配），4
  种匹配策略：EXACT、CONTAINS、SMART_CONTAINS、IGNORE
- **2 种提取模式** — 顺序提取、槽位轮询
- **5 种分发策略** — 顺序、轮询、最近、最远、随机
- **传输事件** — `PreTransferEvent`（可取消）和 `PostTransferEvent`，第三方可 hook 传输行为
- **分组与链接管理** — 折叠查看分组链接，直接重命名、删除或合并同一所有者下的分组，并可单独管理每条链接
- **FTB Teams 集成** — 基于队伍的所有权和权限
- **蓝图系统** — 按整个分组或单条链接预览物流网络，支持移动、旋转、撤销和粘贴
- **完整 /sl 命令树** — info、list、stats、transfer、rename、cleanup
- **可调优性能** — ticker 批处理大小、冷却间隔、缓存大小、对象池大小

## 快速上手

1. 合成**链接配置器**
2. 手持链接配置器右键空气，打开统一配置界面并创建或选择分组
3. 切换为“链接为输出”或“链接为输入”，依次右键需要连接的方块面
4. 在右侧分组列表或网络预览中选择节点，然后在下方直接配置输入端、输出端、升级和过滤器

| 模式    | 右键方块面操作          |
|-------|------------------|
| 扳手    | 移除物流配置（保留方块自身数据） |
| 链接为输入 | 将选中的方块面作为链接接收端   |
| 链接为输出 | 将选中的方块面作为链接发送端   |
| 移除链接  | 移除与该连接面关联的链接     |

## 配置器与网络预览

- 点击分组可展开其下链接；双击分组或链接可以重命名，右键可以删除。
- 同一所有者下，将分组重命名为已有名称会合并两个分组及其链接。
- 点击网络预览中的节点可配置对应连接面，点击链接可单独高亮和管理该链接。
- 鼠标拖动节点可整理布局，在预览空白处拖动可平移，滚轮可等比例缩放。
- 节点位置、缩放和平移会随存档保留；选择整个分组或单条链接也会影响世界中的预览范围。
- 绿色连线表示范围有效，超出范围时会改为警告颜色；悬停连线可查看方向和距离说明。

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
| /sl list                            | 显示活跃物流分组的第 1 页  |
| /sl list page <page>                | 按页查看活跃物流分组      |
| /sl list group <group> [page]       | 按页查看指定分组中的节点    |
| /sl stats                           | 传输统计概览          |
| /sl stats recent                    | 最近 20 条传输（含时间戳） |
| /sl stats top                       | Top 发送/接收节点排行   |
| /sl stats reset                     | 重置传输统计          |
| /sl transfer <from> <to>            | 转移全部节点所有权       |
| /sl transfer <from> group <id> <to> | 转移指定分组          |
| /sl rename <owner> <old> <new>      | 重命名分组           |
| /sl cleanup <owner>                 | 删除某玩家全部节点       |

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
mek_gas_stack_size = 250
mek_infusion_stack_size = 250
mek_pigment_stack_size = 250
mek_slurry_stack_size = 250
mek_heat_stack_size = 1000
ars_source_stack_size = 100
botania_mana_stack_size = 1000
gtceu_stack_size = 1024

[performance]
provider_size = 1000           # 活跃提供者索引预期容量（不限制节点数）
load_factor = 0.75             # 缓存加载因子
max_bulk_entries = 100         # 同步包最大条目
ticker_batch_size = 50         # 每 tick 处理节点数
ticker_time_budget_us = 1500   # 每个维度每 tick 的物流调度软时间预算（微秒）
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

| 模组           | 传输类型           | 实现方式                    |
|--------------|----------------|-------------------------|
| Mekanism     | 气体、灌注、颜料、浆液、热量 | 内部 `ResourceAdapter` 桥接 |
| Ars Nouveau  | 魔源             | 内部 `ResourceAdapter` 桥接 |
| Botania      | 魔力             | 内部 `ResourceAdapter` 桥接 |
| GregTech CEu | EU             | 内部 `ResourceAdapter` 桥接 |
| FTB Teams    | 队伍权限和所有权       | `FTBEventHandlers`      |

第三方集成统一使用公开的 `ResourceAdapter<C, V>`
SPI；内置可选联动直接使用内部桥接层。详见 [docs/INTEGRATION.md](docs/INTEGRATION.md)。

## 兼容性

Forge 1.20.1 已发布的资源 ID 和兼容位序保持不变：

`item=0`、`fluid=1`、`energy=2`、`mek_gas=3`、`mek_infusion=4`、`mek_pigment=5`、`mek_slurry=6`、`mek_heat=7`、
`ars_source=8`、`botania_mana=9`、`gtceu_energy=10`。

旧版类型 mask、分组名称、链接和 packed face 存档键会在加载时迁移。

## 许可证

GNU LGPL 3.0 — cooobird, WangXiaoJin, slime_dragon
