# StaticLogistics

**[English](README.md)** | **[中文](README_ZH.md)**

适用于 Forge 1.20.1 的 Minecraft 物流模组，支持物品、流体、能量和可选模组资源传输，并提供跨维度链接、升级、智能过滤、稳定分组、FTB
Teams 权限与蓝图系统。

## 特性

- **统一传输管线**：内置资源共用同一底层管线；第三方资源通过公开的类型安全 `ResourceAdapter<C, V>` SPI 接入。
- **跨维度传输**：安装维度升级后可建立跨维度链接。
- **五种工具模式**：扳手、链接为输入、链接为输出、移除链接和节点配置。
- **每面独立配置**：每个方块面分别保存频道、优先级、分发策略、提取模式、输入/输出状态和资源类型 ID。
- **稳定分组身份**：持久化身份不依赖显示名称，同一所有者下的同名重命名会执行合并。
- **七种升级**：速度、范围、堆叠、维度、基础过滤、标签过滤和 NBT 过滤。
- **智能过滤**：支持物品过滤、物品/流体标签以及精确或部分 NBT 匹配。
- **可配置组合键**：界面修饰键和世界操作键可在 Minecraft 控制设置中修改。
- **传输事件**：`PreTransferEvent` 可取消，`PostTransferEvent` 使用稳定资源类型 ID 报告已提交传输。
- **蓝图系统**：捕获、预览、旋转、粘贴和撤销物流配置。
- **Jade 集成**：按面显示输入/输出角色、传输/接收类型、频道、策略、优先级和统计。

## 快速上手

1. 合成**链接配置器**。
2. 手持配置器右键空气，创建或选择分组。
3. 选择链接为输入或链接为输出模式，右键需要连接的方块面。
4. 使用节点配置模式调整频道、资源类型、优先级、过滤器和容器升级。

| 模式    | 右键方块面时的行为            |
|-------|----------------------|
| 扳手    | 移除物流配置，并保留受支持的方块实体数据 |
| 链接为输入 | 将该面加入选中分组并设为接收端      |
| 链接为输出 | 将该面加入选中分组并设为发送端      |
| 移除链接  | 从该面移除选中分组的链接         |
| 节点配置  | 打开面配置与共享容器升级         |

移除任意一端都会同步移除反向边。一个输出端连接多个输入端时，移除其中一个输入端只删除对应链接；最后一条链接消失后，没有其他配置意义的空端点会自动清理。

## 升级系统

| 类型     | 等级               | 效果             |
|--------|------------------|----------------|
| 速度     | 铁、金、钻石、下界合金、下界之星 | 调整传输间隔         |
| 范围     | 铁、金、钻石、下界合金、下界之星 | 调整链接范围         |
| 堆叠     | 铁、金、钻石、下界合金、下界之星 | 调整单次传输量        |
| 维度     | 单级               | 启用跨维度传输        |
| 基础过滤   | 单级               | 物品白名单或黑名单      |
| 标签过滤   | 单级               | 物品和流体标签过滤      |
| NBT 过滤 | 单级               | 部分或完整物品 NBT 匹配 |

同一个物理方块上的全部配置面共享容器升级；方块被移除时，升级会正常返还。

## 过滤系统

过滤器修改由服务端保存。关闭过滤器界面或返回节点配置界面时，当前设置都会提交。

| 过滤器    | 匹配内容              |
|--------|-------------------|
| 基础过滤   | 选中的物品             |
| 标签过滤   | 选中或排除的物品/流体标签     |
| NBT 过滤 | 精确或部分 NBT，可选择忽略耐久 |

全部过滤器均支持黑名单模式。

## 命令

`/sl` 管理命令树需要权限等级 2。

| 命令                                    | 说明              |
|---------------------------------------|-----------------|
| `/sl info [pos]`                      | 查看容器和各面配置       |
| `/sl list`                            | 列出活动物流分组        |
| `/sl stats`                           | 查看传输统计          |
| `/sl stats recent`                    | 查看最近传输          |
| `/sl stats top`                       | 查看主要发送端和接收端     |
| `/sl stats reset`                     | 重置传输统计          |
| `/sl transfer <from> <to>`            | 转移全部物流所有权       |
| `/sl transfer <from> group <id> <to>` | 转移指定分组          |
| `/sl rename <owner> <old> <new>`      | 重命名或合并同所有者分组    |
| `/sl cleanup <owner>`                 | 移除指定所有者的物流数据    |
| `/sl debug`                           | 查看注册表和缓存诊断      |
| `/sl debug cache`                     | 查看 Forge 能力缓存统计 |
| `/sl debug types`                     | 列出资源 ID 和稳定兼容位序 |

## 服务端配置

服务端配置位于 `config/staticlogistics.toml`。

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

## 模组集成

| 模组           | 传输类型或功能        |
|--------------|----------------|
| Mekanism     | 气体、灌注、颜料、浆液和热量 |
| Ars Nouveau  | 魔源             |
| Botania      | 魔力             |
| GregTech CEu | 能量             |
| FTB Teams    | 队伍权限、所有权和盟友访问  |
| Jade         | 面配置和传输信息       |
| JEI          | 过滤器幽灵配方材料      |

第三方资源类型使用公开的 `ResourceAdapter<C, V>` SPI，详见 [docs/INTEGRATION.md](docs/INTEGRATION.md)。

## 兼容性

Forge 1.20.1 已发布的资源 ID 和兼容位序保持不变：

`item=0`、`fluid=1`、`energy=2`、`mek_gas=3`、`mek_infusion=4`、`mek_pigment=5`、`mek_slurry=6`、`mek_heat=7`、`ars_source=8`、
`botania_mana=9`、`gtceu_energy=10`。

旧版类型 mask、分组显示名称和 packed face 存档键会在加载时迁移。

## 许可证

GNU LGPL 3.0 — cooobird、WangXiaoJin、slime_dragon
