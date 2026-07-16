# StaticLogistics 模组集成指南

## 运行语义与兼容边界

- 当前调度器只主动执行 push；`isPullMode` 仅为旧接口与未来扩展保留，不代表存在独立 pull 调度循环。
- 服务端保存的节点、分组、菜单目标与权限是最终权威。客户端坐标、分组名和配置数据只能作为请求，集成代码不得据此绕过服务端校验。
- 适配器必须分别实现模拟、提交和缩量，并如实声明事务能力；不能反向写入源端时，必须保证同 tick 的提交结果严格兑现模拟结果。

## 概述

第三方模组通过公开的 `ResourceAdapter<C, V>` SPI 接入统一物流管线；
`LogisticsResource<C>` 是本模组内部桥接接口，不属于外部集成契约。

所有传输类型（物品、流体、能量、化学品、魔源、热量、魔力）统一走 `TransferUtils.doTransferNodes` 管线，自动获得：
- NeoForge `BlockCapabilityCache`（失效后自动释放条目）
- 维度/距离/区块加载检查
- 脏链接清理
- 传输日志
- Pre/Post 传输事件

---

## 快速开始

```java
// 1. 实现公开的类型安全 ResourceAdapter<句柄, 资源值>
public class MyResource implements ResourceAdapter<MyHandle, MyValue> {
    // 定义类型 ID 常量
    private static final ResourceLocation TYPE_ID = ResourceLocation.fromNamespaceAndPath("mymod", "my_type");

    // ── 类型元数据 ──
    @Override public ResourceLocation typeId() { return TYPE_ID; }
    @Override public int color() { return 0xFF55FFFF; }
    @Override public String translationKey() { return "transfer_type.mymod.my_type"; }
    @Override public Supplier<ItemStack> iconSupplier() { return () -> new ItemStack(MyItems.ICON); }
    @Override public IntSupplier baseStackSizeSupplier() { return MyConfig::getStackSize; }
    @Override public Class<MyValue> valueType() { return MyValue.class; }
    @Override public TransactionCapabilities transactionCapabilities() {
        return TransactionCapabilities.exactCompensating();
    }

    // ── 传输逻辑 ──
    @Override
    public @Nullable MyHandle resolve(ServerLevel level, BlockPos pos, Direction face) {
        return level.getCapability(MyCapabilities.MY_CAP, pos, face);
    }

    @Override
    public BlockCapability<MyHandle, Direction> blockCapability() {
        return MyCapabilities.MY_CAP; // 可选；声明后自动使用失效缓存
    }

    @Override
    public SimulationResult<MyValue> simulateExtract(MyHandle source, TransferRequest request) {
        return source.simulateExtract(request.limit());
    }

    @Override
    public CommitResult<MyValue> commitExtract(MyHandle source,
                                                SimulationResult<MyValue> simulation,
                                                TransferRequest request) {
        return source.commitExtract(simulation, request.limit());
    }

    @Override
    public long simulateInsert(MyHandle target, ResourceValue<MyValue> value,
                               TransferRequest request) {
        return target.insert(value.value(), value.amount(), true);
    }

    @Override
    public long commitInsert(MyHandle target, ResourceValue<MyValue> value,
                             TransferRequest request) {
        return target.insert(value.value(), value.amount(), false);
    }

    @Override
    public ResourceValue<MyValue> resize(ResourceValue<MyValue> value, long amount) {
        return new ResourceValue<>(value.value(), amount);
    }

    @Override
    public long rollback(MyHandle source, ResourceValue<MyValue> value,
                         TransferRequest request) {
        return source.restore(value.value(), value.amount());
    }
}

// 2. 在双方约定的注册阶段分配稳定 bitOffset
StaticLogisticsApi.resourceAdapters().register(new MyResource(), 10);
```

---

## 事务适配契约

`ResourceAdapter<C, V>` 使用明确的两阶段协议：

1. `simulateExtract` 生成带类型的候选资源；
2. `simulateInsert` 计算目标可接收量；
3. `commitExtract` 按候选结果执行真实提取；
4. `commitInsert` 执行真实插入；
5. `resize` 构造指定数量的不可变资源值；
6. 支持反向写入的源端在后续提交失败时，由 `rollback` 把余量补偿回源端。

`TransactionCapabilities` 必须如实声明句柄是否提供精确模拟和补偿能力。不能可靠补偿、但能严格兑现模拟结果的单向能力应声明 `exactSimulationOnly()`；不能可靠补偿的适配器不得声明 `exactCompensating()`。

底层能力使用 `int` 参数时，适配器应自行做非负饱和转换；公共 SPI 不暴露内部节点配置或传输上下文对象，过滤、权限和存量维持由统一管线处理。

---

## 注册参数

资源类只负责实现能力解析和事务传输逻辑；第三方通过公开 API 显式分配稳定类型位偏移：

```java
public static final int BIT_MY_TYPE = 10;
StaticLogisticsApi.resourceAdapters().register(new MyResource(), BIT_MY_TYPE);
```

`ResourceAdapter` 的主要元数据方法：

| 方法                        | 说明                       | 约束                         |
|---------------------------|--------------------------|----------------------------|
| `typeId()`                | ResourceLocation 格式的唯一标识 | `"modid:type_name"`        |
| `color()`                 | ARGB 颜色值                 | `0xAARRGGBB`               |
| 注册参数 `stableBitOffset` | 稳定类型序号                    | 非负且唯一；0-31 会额外写入旧 mask 兼容值 |
| `translationKey()`        | GUI 显示文本                 | 需提供 lang 文件                |
| `iconSupplier()`          | 类型图标的 ItemStack          | —                          |
| `baseStackSizeSupplier()` | 单次基础传输量                  | 读取 config 配置               |
| `requiresCooldown()`      | 传输失败后是否冷却                | 默认 `true`                  |
| `requiresValidLinks()`    | 是否需要有效链接                 | 默认 `true`                  |

### 内置类型偏移分配

| bitOffset | 类型                  |
|-----------|---------------------|
| 0         | 物品 (item)           |
| 1         | 流体 (fluid)          |
| 2         | 能量 (energy)         |
| 3         | 化学品 (mek_chemicals) |
| 4         | 热量 (mek_heat)       |
| 5         | 魔源 (ars_source)     |
| 6         | 魔力 (botania_mana)   |
| 7+        | 自定义第三方类型            |

---

## 传输管线

### 统一管线（所有类型）

```
LogisticsTicker.tick()
  └─ TransferExecutor.executeTransfer(context)
       └─ ResourceAdapterHandler.performTransfer(context, targets)
            └─ ResourceAdapterProtocol（每次调用独立创建，携带不可覆盖的上下文）
                 └─ TransferUtils.doTransferNodes(...)
                      ├─ 原生能力缓存（适配器声明 blockCapability 时）
                      ├─ 维度/距离/区块检查
                      ├─ dirty target 清理 + 反向索引增量更新
                      ├─ Fire PreTransferEvent（可取消）
                      ├─ adapter.simulateExtract
                      ├─ adapter.simulateInsert
                      ├─ adapter.commitExtract
                      ├─ adapter.commitInsert
                      ├─ adapter.resize
                      ├─ 失败补偿 → adapter.rollback
                      ├─ Fire PostTransferEvent
                      └─ TransferLogManager.logTransfer
```

### ResourceAdapterHandler 自动处理的能力

- **能力缓存**：声明 `blockCapability()` 的原生能力使用 NeoForge 自动失效缓存；自定义句柄直接调用 `resolve`
- **重入保护**：ThreadLocal 防止同一处理器递归调用
- **递归深度限制**：防止传输链路循环
- **维度/距离/区块加载检查**：按容器升级配置
- **脏链接清理**：目标方块已移除时自动断开
- **传输日志**：成功/失败均记录到 TransferLogManager
- **Pre/Post 事件**：第三方可 hook 传输行为

---

## 事件系统

### PreTransferEvent（可取消）

```java
@EventBusSubscriber
public class MyEventHandler {
    @SubscribeEvent
    public static void onPreTransfer(PreTransferEvent event) {
        // 检查传输量
        if (event.getRequestedAmount() > 10000) {
            event.setCanceled(true); // 取消传输
        }
    }
}
```

### PostTransferEvent

```java
@SubscribeEvent
public static void onPostTransfer(PostTransferEvent event) {
    if (event.isSuccess()) {
        // 记录传输日志、触发成就等
    }
}
```

---

## TransferFailureReason 注册表

内置失败原因，第三方可注册自定义原因：

```java
TransferFailureReason.register(
    ResourceLocation.fromNamespaceAndPath("mymod", "incompatible_chemical"),
    "failure.mymod.incompatible_chemical"
);
```

---

## 传输上限说明

传输上限由底层 API 决定：

| 类型  | 上限                | 原因                                               |
|-----|-------------------|--------------------------------------------------|
| 物品  | Integer.MAX_VALUE | `IItemHandler.extractItem(int, int, boolean)`    |
| 流体  | Integer.MAX_VALUE | `IFluidHandler.drain(int, Action)`               |
| 能量  | Integer.MAX_VALUE | `IEnergyStorage.extractEnergy(int, boolean)`     |
| 化学品 | Long.MAX_VALUE    | `IChemicalHandler.extractChemical(long, Action)` |
| 热量  | Long.MAX_VALUE    | `IHeatHandler.handleHeat(double)`                |
| 魔源  | Integer.MAX_VALUE | `ISourceCap.extractSource(int, boolean)`         |
| 魔力  | Integer.MAX_VALUE | Botania API 参数是 int                              |

**实际传输量** = `baseStackSize × stackMultiplier`，由配置和升级决定，通常远小于 API 上限。

---

## 完整示例

本页“快速开始”代码就是完整的公共 SPI 骨架。内建 Mekanism、能量等实现属于模组内部桥接，不是第三方可依赖的 API；集成方只应导入 `com.coobird.staticlogistics.api.transfer` 与 `StaticLogisticsApi`。
