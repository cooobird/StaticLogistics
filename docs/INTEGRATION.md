# StaticLogistics 模组集成指南

## 概述

StaticLogistics 通过 `LogisticsResource<C>` 接口将任意模组的资源类型接入统一物流管线。  
所有资源均以**可传输**（extract/insert）为设计目标。

所有传输类型（物品、流体、能量、化学品、魔源、热量、魔力）统一走 `TransferUtils.doTransferNodes` 管线，自动获得：
- WeakRef 能力缓存
- 维度/距离/区块加载检查
- 脏链接清理
- 传输日志
- Pre/Post 传输事件

---

## 快速开始

```java
// 1. 实现 LogisticsResource<你的句柄类型>
public class MyResource implements LogisticsResource<MyHandle> {

    // ── 类型元数据 ──
    @Override public ResourceLocation typeId() { return ResourceLocation.fromNamespaceAndPath("mymod", "my_type"); }
    @Override public int color() { return 0xFF55FFFF; }
    @Override public int bitOffset() { return 10; }
    @Override public String translationKey() { return "transfer_type.mymod.my_type"; }
    @Override public Supplier<ItemStack> iconSupplier() { return () -> new ItemStack(MyItems.ICON); }
    @Override public IntSupplier baseStackSizeSupplier() { return MyConfig::getStackSize; }

    // ── 传输逻辑 ──
    @Override
    public @Nullable MyHandle resolve(ServerLevel level, BlockPos pos, Direction face) {
        // 返回该位置的操作句柄，不可用时返回 null
        return level.getCapability(MyCapabilities.MY_CAP, pos, face);
    }

    @Override
    public long extract(MyHandle handle, long amount, boolean simulate) {
        // 从句柄提取资源
    }

    @Override
    public long insert(MyHandle handle, long amount, boolean simulate) {
        // 向句柄注入资源
    }

    // 2. 注册（在 mod 初始化阶段调用）
    public static void register() {
        TransferRegistries.registerAdapter(new MyResource());
    }
}
```

---

## 实现层级

### 简单资源（能量/魔源/热量等 int/long 值资源）

覆写 `extract` / `insert`，返回 `long`。

```java
@Override
public long extract(MyHandle handle, long amount, boolean simulate) {
    return handle.extract(amount, simulate);
}

@Override
public long insert(MyHandle handle, long amount, boolean simulate) {
    return handle.receive(amount, simulate);
}
```

### 类型化资源（化学品等需要携带类型信息的资源）

覆写 `extractTyped` / `insertTyped` / `isEmptyResult`。

```java
@Override
public ExtractionResult<ChemicalStack> extractTyped(IChemicalHandler handle, long amount, boolean simulate) {
    ChemicalStack extracted = handle.extractChemical(amount, simulate ? Action.SIMULATE : Action.EXECUTE);
    return ExtractionResult.of(extracted);
}

@Override
public long insertTyped(IChemicalHandler handle, Object value, boolean simulate) {
    if (!(value instanceof ChemicalStack stack) || stack.isEmpty()) return 0;
    ChemicalStack remainder = handle.insertChemical(stack, simulate ? Action.SIMULATE : Action.EXECUTE);
    return stack.getAmount() - remainder.getAmount();
}

@Override
public boolean isEmptyResult(@Nullable Object value) {
    if (value == null) return true;
    if (value instanceof ChemicalStack chem) return chem.isEmpty();
    return false;
}
```

### 上下文感知资源（物品/流体等需要过滤器检查的资源）

覆写带 `FaceConfigComposite` / `TransferContext` 参数的重载。

```java
@Override
public ExtractionResult<?> extractTyped(MyHandle handle, long amount, boolean simulate,
                                         @Nullable FaceConfigComposite sourceCfg, boolean isPullMode,
                                         @Nullable TransferContext context) {
    // 输出过滤器检查
    if (sourceCfg != null && !isAllowed(sourceCfg, handle, isPullMode)) {
        return ExtractionResult.of(EMPTY);
    }
    return ExtractionResult.of(handle.extract(amount, simulate));
}

@Override
public long insertTyped(MyHandle handle, Object value, boolean simulate,
                         @Nullable FaceConfigComposite sourceCfg, boolean isPullMode,
                         @Nullable TransferContext context) {
    // 输入过滤器检查
    if (sourceCfg != null && !isAllowed(sourceCfg, value, isPullMode)) return 0;
    return handle.insert(value, simulate);
}

@Override
public boolean canInsertToTarget(MyHandle handle, Object value, FaceConfigComposite targetCfg) {
    // 目标端过滤器检查 + 存量维持
    return FilterEvaluator.isAllowed(value, targetCfg);
}

@Override
public void commitExtract(MyHandle handle, ExtractionResult<?> result, int actual,
                           @Nullable FaceConfigComposite sourceCfg, boolean isPullMode,
                           @Nullable TransferContext context) {
    // 从 ExtractionResult.context() 获取槽位索引进行精确提取
    if (result.context() instanceof Integer slotIdx) {
        handle.extractFromSlot(slotIdx, actual);
    }
}
```

---

## 注册参数

`TransferRegistries.registerAdapter(new MyResource())` 一步注册。

`LogisticsResource` 接口的元数据方法：

| 方法                        | 说明                       | 约束                  |
|---------------------------|--------------------------|---------------------|
| `typeId()`                | ResourceLocation 格式的唯一标识 | `"modid:type_name"` |
| `color()`                 | ARGB 颜色值                 | `0xAARRGGBB`        |
| `bitOffset()`             | 类型位掩码偏移                  | [0, 31]，各类型必须唯一     |
| `translationKey()`        | GUI 显示文本                 | 需提供 lang 文件         |
| `iconSupplier()`          | 类型图标的 ItemStack          | —                   |
| `baseStackSizeSupplier()` | 单次基础传输量                  | 读取 config 配置        |
| `requiresCooldown()`      | 传输失败后是否冷却                | 默认 `true`           |
| `requiresValidLinks()`    | 是否需要有效链接                 | 默认 `true`           |

### 内置类型偏移分配

| bitOffset | 类型                  |
|-----------|---------------------|
| 0         | 物品 (item)           |
| 1         | 流体 (fluid)          |
| 2         | 能量 (energy)         |
| 3         | 化学品 (mek_chemicals) |
| 4         | 魔源 (ars_source)     |
| 5         | 热量 (mek_heat)       |
| 6         | 魔力 (botania_mana)   |
| 7+        | 自定义第三方类型            |

---

## 传输管线

### 统一管线（所有类型）

```
LogisticsTicker.tick()
  └─ TransferExecutor.executeTransfer(context)
       └─ ResourceAdapterHandler.performTransfer(context, targets)
            └─ ResourceAdapterProtocol（携带 sourceCfg + isPullMode + TransferContext）
                 └─ TransferUtils.doTransferNodes(...)
                      ├─ getCachedCapability (WeakRef 缓存)
                      ├─ 维度/距离/区块检查
                      ├─ dirty target 清理 + 反向索引增量更新
                      ├─ Fire PreTransferEvent（可取消）
                      ├─ simulateExtract → adapter.extractTyped
                      ├─ canInsert → adapter.canInsertToTarget
                      ├─ executeInsert → adapter.insertTyped
                      ├─ commitExtract → adapter.commitExtract
                      ├─ Fire PostTransferEvent
                      └─ TransferLogManager.logTransfer
```

### ResourceAdapterHandler 自动处理的能力

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

内置 11 个失败原因，第三方可注册自定义原因：

```java
TransferFailureReason.register(
    ResourceLocation.fromNamespaceAndPath("mymod", "incompatible_chemical"),
    "failure.mymod.incompatible_chemical"
);
```

---

## 旧式集成（已废弃）

### TransferProvider

较早期的简化集成方式，**已废弃，不推荐使用**。

### TransferType

旧版类型定义 record，**已移除**。所有元数据已合并到 `LogisticsResource` 接口。

---

## 完整示例 — Mekanism 化学品

```java
public class MekanismChemicalResource implements LogisticsResource<IChemicalHandler> {

    @Override public ResourceLocation typeId() { return StaticLogistics.asResource("mek_chemicals"); }
    @Override public int color() { return 0xFF66FF66; }
    @Override public int bitOffset() { return 3; }
    @Override public String translationKey() { return "transfer_type.staticlogistics.mek_chemicals"; }
    @Override public Supplier<ItemStack> iconSupplier() { return () -> new ItemStack(MekanismBlocks.BASIC_CHEMICAL_TANK.get()); }
    @Override public IntSupplier baseStackSizeSupplier() { return SLConfig::getMekChemicalStack; }

    @Override
    public @Nullable IChemicalHandler resolve(ServerLevel level, BlockPos pos, Direction face) {
        return level.getCapability(mekanism.common.capabilities.Capabilities.CHEMICAL.block(), pos, face);
    }

    @Override
    public ExtractionResult<ChemicalStack> extractTyped(IChemicalHandler handle, long amount, boolean simulate) {
        ChemicalStack extracted = handle.extractChemical(amount, simulate ? Action.SIMULATE : Action.EXECUTE);
        return ExtractionResult.of(extracted);
    }

    @Override
    public long insertTyped(IChemicalHandler handle, Object value, boolean simulate) {
        if (!(value instanceof ChemicalStack stack) || stack.isEmpty()) return 0;
        ChemicalStack remainder = handle.insertChemical(stack, simulate ? Action.SIMULATE : Action.EXECUTE);
        return stack.getAmount() - remainder.getAmount();
    }

    @Override
    public boolean isEmptyResult(@Nullable Object value) {
        if (value == null) return true;
        if (value instanceof ChemicalStack chem) return chem.isEmpty();
        return false;
    }

    public static void register() {
        TransferRegistries.registerAdapter(new MekanismChemicalResource());
    }
}
```
