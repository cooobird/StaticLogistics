D:\SoftWareSpace\GameProject\MinecraftModWorkSpace\StaticLogistics-neoforge-1.21.1\README_ZH.md
# StaticLogistics

**[English](README.md)** | **[中文](README_ZH.md)**

一个现代化的 Minecraft 物流模组，支持灵活的物品/流体/能量传输、跨维度支持、升级系统、智能过滤、分组管理和可配置的性能设置。

## 功能特性

### 核心功能
- **灵活传输**：支持物品、流体和能量传输
- **跨维度**：在不同维度之间传输资源
- **升级系统**：通过各种升级（速度、范围、堆叠、维度）增强物流系统
- **智能过滤**：支持基于标签和 NBT 的高级过滤
- **分组管理**：将物流节点组织成分组，便于管理
- **性能配置**：根据服务器能力微调性能设置

### 升级系统
- **速度升级**：提高传输速度（铁、金、钻石、下界合金、下界之星）
- **范围升级**：扩展传输范围（铁、金、钻石、下界合金、下界之星）
- **堆叠升级**：增加传输堆叠大小（铁、金、钻石、下界合金、下界之星）
- **维度升级**：启用跨维度传输
- **过滤升级**：添加过滤功能（基础、标签、NBT）

### 过滤系统
- **物品标签**：按物品标签过滤
- **方块标签**：按方块标签过滤
- **流体标签**：按流体标签过滤
- **NBT 数据**：按 NBT 数据过滤
- **匹配策略**：精确匹配、包含、智能包含、忽略

## 安装

### 系统要求
- Minecraft 1.21.1
- NeoForge 21.1.219 或更高版本

### 可选依赖
- JEI (Just Enough Items) - 用于查看配方
- Mekanism - 用于化学品和热量传输
- Ars Nouveau - 用于魔源传输
- PneumaticCraft: Repressurized - 用于气压和热量传输
- Create - 额外兼容性

### 安装步骤
1. 下载最新版本的 StaticLogistics
2. 安装 Minecraft 1.21.1 的 NeoForge
3. 将 StaticLogistics jar 文件放入 `mods` 文件夹
4. 启动游戏

## 使用指南

### 入门教程
1. **制作连接配置器**：设置物流系统的主要工具
2. **配置节点**：潜行 + 右键点击方块进行配置
3. **设置传输模式**：选择存入或提取模式
4. **设置传输类型**：选择物品、流体或能量
5. **创建链接**：将节点连接起来开始传输资源

### 连接配置器模式
- **设为存入端**：将节点标记为目标（资源将存入此处）
- **设为提取端**：将节点标记为源（资源将从此处提取）
- **移除链接**：移除连接到节点面的所有链接
- **配置节点面**：打开特定面的详细配置
- **配置节点容器**：打开容器的详细配置

### 配置说明
- **面配置**：设置输入/输出模式、频道、优先级和策略
- **容器配置**：配置升级及其倍率
- **过滤配置**：设置基于标签和 NBT 数据的高级过滤器

### 频道系统
- 使用频道 1-16 组织物流系统
- 节点只有在具有相同频道时才会连接
- 在面配置 GUI 中设置频道

## 配置文件

### 服务器配置 (`staticlogistics-server.toml`)

#### 通用设置
```toml
[general]
default_radius = 16              # 默认传输半径
default_tick_interval = 20       # 基础刻间隔
max_transfer_limit = 10000000    # 每刻最大传输量
auto_clean_stored_nodes = false  # 自动清理存储的节点
```

#### 缓存设置
```toml
[cache]
provider_size = 1000           # 提供者缓存大小
load_factor = 0.75              # 缓存加载因子
target_size = 50                 # 目标缓存大小
global_target_size = 500         # 全局目标缓存大小
```

#### 网络设置
```toml
[network]
max_bulk_entries = 100          # 每个网络包的最大批量条目数
```

#### 性能设置
```toml
[performance]
ticker_batch_size = 50          # 每刻处理的节点数
clean_interval = 200             # 清理间隔（游戏刻）
default_cooldown = 10           # 默认冷却时间（游戏刻）
batch_clean_threshold = 500      # 批量清理阈值
batch_clean_size = 200           # 批量清理大小
context_pool_size = 100          # 上下文池大小
```

## 模组集成

### 支持的模组
- **Mekanism**：化学品和热量传输
- **Ars Nouveau**：魔源传输
- **PneumaticCraft: Repressurized**：气压和热量传输
- **Create**：额外兼容性
- **FTB Teams**：基于团队的权限

### 集成设置
集成特定的堆叠大小和倍率可以在服务器配置文件中配置。

## 命令

### `/sl info <pos>`
显示特定位置的物流信息。

### `/sl list [page]`
列出所有活跃的物流分组。

### `/sl strategies [page]`
列出数据组件匹配策略。

### `/sl transfer <player> <group_id>`
将链接从一个玩家转移到另一个玩家。

### `/sl rename <group_id> <new_name>`
重命名物流分组。

### `/sl cleanup <player>`
清理玩家拥有的所有链接。

## 性能优化建议

1. **调整批处理大小**：如果遇到卡顿，减少 `ticker_batch_size`
2. **优化缓存**：在大服务器上增加缓存大小以获得更好的性能
3. **网络设置**：如果有网络问题，降低 `max_bulk_entries`
4. **清理链接**：使用清理命令删除未使用的链接

## 故障排除

### 链接不工作？
- 检查两个节点是否具有相同的频道
- 验证两个节点是否在同一维度（除非使用维度升级）
- 确保传输类型匹配（物品/流体/能量）

### 内存占用过高？
- 在配置中减少缓存大小
- 使用清理命令删除未使用的链接

### 网络问题？
- 在网络设置中降低 `max_bulk_entries`
- 检查服务器的网络配置

## 许可证

本模组采用 GNU LGPL 3.0 许可证。

## 致谢

- **作者**：cooobird, WangXiaoJin
- **贡献者**：感谢所有为该项目做出贡献的人

## 支持

如有问题、建议或疑问，请访问项目的问题跟踪器。

## 更新日志

详见 [changelog.md](changelog.md) 获取详细的版本历史。