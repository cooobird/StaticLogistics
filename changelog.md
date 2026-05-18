# 更新日志（1.1.2-SNAPSHOT）

## 扳手模式重写
- **对标行业标准**：扳手回归简洁——旋转 + 拆卸 Mek 塑料方块，其他模组（Mekanism/Create 等）的拆卸由对应模组自身的 `CONFIGURATORS`/`CHAIN_RIDEABLE` 标签机制处理，不再越俎代庖。
- **ItemAbility 门控**：`canPerformAction` 只在 WRENCH 模式放行 `wrench_` 类能力，非扳手模式拒绝，防止其他模组在非预期模式下接管拆卸。
- **RightClickBlock 双层事件防护**：
  - `EventPriority.HIGHEST`：检测 Create 的 `IWrenchable` 方块，非扳手模式抢先取消事件，阻止 `WrenchEventHandler`（`EventPriority.HIGH`）的直接拆卸调用。
  - `EventPriority.LOW`：非 IWrenchable 方块设置 `UseBlock=FALSE`，阻止 Mekanism `CommonPlayerTracker` 的 `UseBlock=TRUE` 覆盖，恢复正常物品→方块处理流程。
- **Create IWrenchable 直接引用**：`instanceof IWrenchable` 替代反射，通过 `ModCompat.isCreateLoaded()` 守卫，干净精确。
- **移除冗余逻辑**：删除 Container NBT 默认状态对比、刷怪笼开关、扳手命名空间黑名单、多方块检测（反射/属性/标签）——这些不再属于扳手的职责。
- **移除 SLItemAbilities**：删除自定义 ItemAbility 常量类、ToolMode 能力集合、ModeHandler.abilities() 声明，能力归属由各模组自身的标签体系定义。
- **存点清理修复**：节点被拆除时永远清理存点引用，不再受 `auto_clean_stored_nodes` 配置开关限制——方块都没了，存点必然失效，这是基础正确性。
- **翻译键和配置清理**：删除 `sneak_required`、`not_container`、`no_block_entity`、`spawner_disabled`、`removed`、`failed`、`allow_spawner_wrench`、`wrench_blacklist` 等不再使用的翻译键和配置缓存字段。
- **扳手模式工具提示**：更新 `mode.staticlogistics.wrench.desc`，注明安装 Mekanism Additions 后可拆卸塑料方块。

## 性能优化
- **反向链接索引**：新增 `reverseLinks` 索引（`Map<Long, LongSet>`），将"谁连向我"的查询从 O(n) 全维度遍历降为 O(1) 直接查表，大幅降低 ticker 开销。
- **索引同步**：在所有链路增删路径（直接链接、批量链接、合并链接、面移除、方块移除、移除模式）同步维护反向链接索引，确保双向数据始终一致。
- **缓存快照防并发**：`CacheManager.getActiveProviderKeys()` 返回快照副本（`LongOpenHashSet`）替代 live view，防止遍历时其他线程写入导致崩溃。
- **传输上下文线程安全**：给对象池加 `ReentrantLock` + 硬上限 200，防止高并发下池膨胀。
- **热路径日志降级**：`ContainerConfig.updateCache()` 的 `LOGGER.info` 降为 `LOGGER.debug`，减少生产环境磁盘 IO。
- **过滤器配置缓存**：`StandardTransferHandlers` 中 `FaceConfig` 查找从每次传输改为每次 transfer 只查一次，避免重复哈希计算。
- **消除双重列表拷贝**：`StrategyBasedTargetSelector` 移除 `new ArrayList<>(cachedTargets)` 的冗余拷贝，直接排序原列表。
- **NBT 增量保存**：`LinkManagerStorage` 引入脏 key 追踪（`dirtyFaceKeys/dirtyContainerKeys`），仅序列化变化的条目，每 100 次增量为一个完整保存周期，大幅减少磁盘 IO。
- **Ticker 快速判空**：`hasActiveProviders()` 判空再取快照，空跑时零内存分配。
- **冷却批量清理**：方块拆除/批量移除时清理对应节点的冷却记录，防止已拆除节点的冷却残留。
- **能力缓存 TTL**：`CapabilityCache` 每 500 次访问清理已被 NeoForge 作废的缓存条目，防止长期运行后缓存膨胀。
- **NBT 过滤器缓存**：`NbtLogisticsFilter` 对同物品连续检查走 hash 缓存，避免反复 NBT 序列化比对。
- **冗余方法清理**：移除 `CacheManager.getMaxCacheSize()`、`getUsageRatio()` 和 `LinkManager.flush()`，全项目无调用者。

## 新功能
- **传输日志面板**：新增 `TransferLogManager`，环形缓冲记录最近 200 条传输，累计总量/按类型/按节点统计。
- **`/sl stats` 命令**：
  - `stats` — 总览（总次数、总量、失败数、按类型分布）
  - `stats recent` — 最近 20 条传输明细
  - `stats top` — Top10 发送 + 接收节点排行
  - `stats reset` — 重置统计

## 兼容性改进
- **放置状态同步**：放置已保存 NBT 的方块后，遍历容器槽位触发 `setItem`，自动更新方块状态（雕纹书架、唱片机等视觉生效）。
- **配置器防冒用**：新增 `STORED_NODES_OWNER` 数据组件，存节点时记 UUID，链接时校验，防止别人捡到配置器用你的存点创建链接。

## 数值平衡
- **升级倍率重调**：铁×2 → 金×4 → 钻石×8 → 下界合金×16 → 下界之星×64。每级翻倍，消除原来下界之星 10,000 倍的崩坏跳跃。
- **配方重做**：每级加主题材料（红石块/末影眼/木桶/潜影壳），标签过滤器不再使用命名牌（不可再生→纸+书），NBT 过滤器加红石。详见数据生成器。

## 权限审计
- 全部 C2S 网络包均已校验 `canPlayerModify` 或 `canPlayerAccess`，容器配置为物理升级槽位不设限。权限完整。

## 修复
- **组 ID 自增修复**：组 ID 改为会话绑定——库存第一个节点时自增，清空配置器时重置。同一会话内多次链接共用一组，不再每次链接都生成新组。

## 代码可读性
- 为 api、registry、core 包共 32 个核心文件添加直白中文注释，类/方法/字段各有说明。

# 更新日志（1.1.1-SNAPSHOT）

## 代码质量与架构优化
- **并发安全修复**：修复 `LinkManager.pendingRemovals` 的并发安全问题，改用原子操作和显式锁确保线程安全。
- **内存泄漏修复**：
  - `CacheManager` 实现 LRU 缓存机制，设置最大缓存大小限制（1000条），自动清理最旧条目。
  - `FaceConfigComposite` 添加缓存大小限制和全局缓存管理，防止目标缓存无限增长。
- **线程池资源管理改进**：
  - 为保存线程池添加异常处理器和优雅关闭机制。
  - 添加关闭状态检查，防止关闭后提交任务。
  - 改进 `markDirty()` 方法的异常处理和资源管理。
- **错误处理增强**：改进 `onBlocksRemovedBulk` 方法的错误处理，分别跟踪不同类型的失败操作，提供详细的失败日志汇总。
- **权限服务优化**：`PermissionService` 改为单例模式，避免重复创建实例，减少资源浪费，添加空值检查防止 NPE。
- **代码重构**：
  - `LinkConfig` 添加频道范围常量（`MIN_CHANNEL=1`、`MAX_CHANNEL=16`、`DISABLED_CHANNEL=0`）和 `clampChannel()` 方法。
  - 简化频道范围检查逻辑，消除重复代码。
  - 使用语义化常量替换硬编码的魔法数字，提高代码可读性和可维护性。
- **字符串比较安全化**：将所有不安全的字符串比较替换为 `Objects.equals()`，防止空指针异常，涉及 `BaseFilterScreen`、`FaceConfiguratorScreen` 和 `LinkConfiguratorScreen`。
- **性能优化**：
  - `LogisticsTicker` 添加分批处理机制（每批50个节点），避免单tick处理所有节点导致的性能尖峰，平滑负载。
  - `CacheManager` 使用读写锁（`ReadWriteLock`）替代 `synchronized`，读操作可并发执行，显著提高读多写少场景下的性能。
  - `FaceConfigComposite` 全局缓存移除显式锁，利用 `ConcurrentHashMap` 的原子操作，减少锁竞争，提高并发性。
- **代码可维护性提升**：
  - 提取魔法数字为常量：`DEFAULT_COOLDOWN_TICKS`、`DOUBLE_CLICK_THRESHOLD_MS`、`SHUTDOWN_TIMEOUT_SECONDS`、`FORCE_SHUTDOWN_TIMEOUT_SECONDS`、`FACE_BITS`、`FACE_MASK`。
  - `LogisticsNode` 添加便捷方法 `isInSameDimension()`，统一维度比较逻辑，减少代码重复。
  - 使用 Java 16+ 的 `toList()` 简化 Stream API，代码更简洁高效。
  - **配置值集中管理**：创建 `LogisticsConstants` 统一管理所有配置常量，按功能分类（Cache、Network、Performance、Storage、Thread、UI），消除重复定义，便于统一调整和维护，涉及 10 个文件的常量替换。
- **性能参数配置化**：将性能、缓存、网络相关参数迁移到配置文件，玩家可在服务器配置中调整：
  - 缓存配置：提供者缓存大小、加载因子、目标缓存大小、全局目标缓存大小
  - 网络配置：批量同步最大条目数
  - 性能配置：批处理大小、清理间隔、冷却时间、批量清理阈值、对象池大小
  - 支持热重载，修改配置后无需重启服务器
  - 添加详细的中英文tooltip描述，包含默认值、范围、影响说明，提升配置界面友好度

# 更新日志（1.1-SNAPSHOT）

## 传输系统重构
- **底层传输协议升级**：引入 `ExtractionResult` 包装提取结果，可携带槽位等上下文信息，从根源上解决物品传输时模拟提取与实际提交不一致导致的复制问题。
- **物品轮询策略优化**：为 `SLOT_ROUND_ROBIN` 策略提供精确槽位绑定，确保提取和提交始终针对同一容器槽位，彻底修复该策略下物品异常增减或丢失的漏洞。
- **频道机制简化与强化**：
  - 频道改为整个面共享（不再按传输类型独立），范围固定为 1~16，不再允许 0。
  - 新建链接默认分配频道 1；GUI 频道循环仅限 1~16；任何途径传入的 0 均自动纠正为 1。
  - 确保传输匹配条件（双方频道非零且相等）始终满足。

## 性能与稳定性
- 优化配置保存机制：修改后延迟写入，减少磁盘频繁操作。
- 批量处理方块破坏时的网络同步，降低爆炸等场景下的网络负载。
- 传输上下文采用对象池复用，减少内存分配，提升高负载流畅度。
- 目标节点列表排序结果加入缓存，避免重复计算，提高传输效率。
- 修复能力缓存和冷却缓存的潜在内存泄漏。
- 移除冗余链路索引结构，减少内存占用。

## 渲染改进
- 粒子线颜色映射优化：频道值直接映射为 `RenderConstants.DYE_COLORS` 索引（频道 n 对应颜色 n-1），16 种颜色与频道一一对应，修复频道为 0 时渲染被跳过的 bug。
- 修复配置界面频道为 0 时颜色按钮数组越界（`Index -1 out of bounds`）的潜在崩溃。
- 为客户端面状态渲染添加防御检查：收到无效频道值（如 0）时改用默认白色，不再崩溃。

## 安全性
- 加强网络包权限校验，防止玩家越权修改他人配置。
- 修复多人同时操作节点可能导致的配置冲突。

## 界面与易用性
- **过滤器标签选择支持流体**：在标签过滤模式下，流体槽位现在也可弹出标签下拉菜单，支持左键/右键添加允许/排除标签，与物品槽位行为完全一致。
- **支持从 JEI 拖放物品/流体到过滤器格子**：实现 `GhostIngredientHandler`，为过滤器槽位生成整体目标区域，拖放时精准填入对应格子。
- 优化过滤器标签选择下拉菜单，代码更简洁，交互更稳定。
- 标签栏显示优化：优先显示物品标签，若无则显示该槽位的流体标签，避免空白。
- 新增配置文件选项"自动清理存放点"，默认关闭。
- 配置界面频道颜色按钮支持左键增加、右键减少，数值在 1~16 循环。

## 修复
- **修复扳手模式拆除物流容器后链接残留**：移除方块前正确调用 `LinkManager.onBlockRemoved`，完整清理该方块所有面的配置、双向链接及关联升级物品。
- **修复 Tag Filter 模式下流体槽位无法选取标签的问题**：此前通过流体桶右键设置的流体无法弹出标签菜单，现已支持。
- 修复因频道值为 0 导致的传输完全失效及粒子线不渲染。
- 修复清除槽位时仅清除物品标签而遗留流体标签的清理不彻底问题，现在清除槽位会同时移除物品和流体标签。
- 修复孤槽位（既无物品也无流体）仍残留标签数据导致的显示异常。
- 修正部分命令执行错误。
- 修复队伍权限变更后配置同步异常。
- 修复节点删除时的一个编译错误。

## 开发者注意
- **`FilterData` 流体标签字段类型变更**：`fluidFilterTags` 和 `excludedFluidTags` 由 `Set<TagKey<Fluid>>` 改为 `Map<String, Set<TagKey<Fluid>>>`，支持按槽位独立存储流体标签，编解码同步更新。自定义过滤器读取数据时需适配。
- 传输协议接口 `TransferProtocol` 已更改：`simulateExtract` 返回 `ExtractionResult<T>`，`commitExtract` 接收 `ExtractionResult<T>`。自定义处理器需同步适配。
- `TransferContext` 不再是不可变记录，请使用 `obtain()` 获取实例，用后调用 `recycle()` 归还。
- 频道值统一使用 1~16，不再允许 0；颜色映射请使用 `(channel - 1) % DYE_COLORS.length`。
- `LinkManager.markDirty()` 现为公开方法，替代原先的 `setDirty()`。
- 新增 `removeFaceConfigDataOnly()` 方法，用于静默移除面配置。
- `GhostIngredientHandler` 改为通配符实现 `IGhostIngredientHandler<BaseFilterScreen<?>>`，并增加了 `acceptGhostIngredient` 的槽位索引重载，便于 JEI 集成。
