# StaticLogistics 模组集成指南（Forge 1.20.1）

## 兼容边界

- `com.coobird.staticlogistics.api` 与 `com.coobird.staticlogistics.api.transfer` 是第三方集成入口。
- `com.coobird.staticlogistics.transfer` 下的 `LogisticsResource`、传输上下文和管线实现属于内部结构，不应由第三方直接依赖。
- 服务端保存的节点、分组、权限和配置是最终权威。客户端提交的位置、面、分组名和类型列表都必须经过服务端验证。
- 当前调度器主动执行 push；`pullMode` 是稳定请求字段和未来扩展边界，不表示存在独立的 pull 调度循环。
- 适配器必须提供精确模拟与精确缩量。不能兑现模拟结果的适配器会在注册阶段被拒绝。

## 公共入口

第三方资源通过 `ResourceAdapter<C, V>` 描述：

- `C`：能力或资源句柄类型；
- `V`：资源值类型，例如某种化学品标识；
- `ResourceValue<V>`：不可为空并携带明确数量的资源；
- `SimulationResult<V>`：模拟提取结果及提交令牌；
- `CommitResult<V>`：真实提取结果；
- `TransferRequest`：稳定、只读的传输请求。

注册入口：

```java
StaticLogisticsApi.resourceAdapters().register(adapter, stableBitOffset);
```

应在模组公共初始化阶段、服务器第一次加载物流数据前完成注册。

## 完整骨架

```java
package example.integration;

import com.coobird.staticlogistics.api.StaticLogisticsApi;
import com.coobird.staticlogistics.api.transfer.CommitResult;
import com.coobird.staticlogistics.api.transfer.ResourceAdapter;
import com.coobird.staticlogistics.api.transfer.ResourceValue;
import com.coobird.staticlogistics.api.transfer.SimulationResult;
import com.coobird.staticlogistics.api.transfer.TransactionCapabilities;
import com.coobird.staticlogistics.api.transfer.TransferRequest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class MyResourceAdapter implements ResourceAdapter<MyHandle, MyValue> {
    private static final ResourceLocation TYPE_ID =
        ResourceLocation.fromNamespaceAndPath("mymod", "my_resource");

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public Class<MyValue> valueType() {
        return MyValue.class;
    }

    @Override
    public int color() {
        return 0xFF66FF66;
    }

    @Override
    public String translationKey() {
        return "transfer_type.mymod.my_resource";
    }

    @Override
    public Supplier<ItemStack> iconSupplier() {
        return () -> new ItemStack(MyItems.RESOURCE_ICON.get());
    }

    @Override
    public IntSupplier baseStackSizeSupplier() {
        return MyConfig::transferAmount;
    }

    @Override
    public TransactionCapabilities transactionCapabilities() {
        return TransactionCapabilities.exactCompensating();
    }

    @Override
    public @Nullable MyHandle resolve(
        ServerLevel level,
        BlockPos pos,
        Direction face
    ) {
        return MyCapabilities.find(level, pos, face);
    }

    @Override
    public SimulationResult<MyValue> simulateExtract(
        MyHandle source,
        TransferRequest request
    ) {
        MyHandle.Simulation simulation = source.simulateExtract(request.maxAmount());
        if (simulation.isEmpty()) return SimulationResult.empty();
        return SimulationResult.of(
            new ResourceValue<>(simulation.value(), simulation.amount()),
            simulation.token()
        );
    }

    @Override
    public CommitResult<MyValue> commitExtract(
        MyHandle source,
        SimulationResult<MyValue> simulation,
        TransferRequest request
    ) {
        return source.commitExtract(simulation, request.maxAmount());
    }

    @Override
    public long simulateInsert(
        MyHandle target,
        ResourceValue<MyValue> resource,
        TransferRequest request
    ) {
        return target.insert(resource.value(), resource.amount(), true);
    }

    @Override
    public long commitInsert(
        MyHandle target,
        ResourceValue<MyValue> resource,
        TransferRequest request
    ) {
        return target.insert(resource.value(), resource.amount(), false);
    }

    @Override
    public ResourceValue<MyValue> resize(
        ResourceValue<MyValue> resource,
        long amount
    ) {
        return new ResourceValue<>(resource.value(), amount);
    }

    @Override
    public long rollback(
        MyHandle source,
        ResourceValue<MyValue> resource,
        TransferRequest request
    ) {
        return source.restore(resource.value(), resource.amount());
    }

    public static void register() {
        StaticLogisticsApi.resourceAdapters().register(
            new MyResourceAdapter(),
            11
        );
    }
}
```

示例中的 `MyHandle`、`MyValue`、`MyItems`、`MyConfig` 和 `MyCapabilities` 由集成模组自行实现。

## 事务契约

统一管线按以下顺序调用适配器：

1. `resolve` 获取源端和目标端句柄；
2. `simulateExtract` 生成候选资源；
3. `simulateInsert` 计算目标可接收量；
4. `resize` 将候选资源缩小到实际提交量；
5. `commitExtract` 执行真实提取；
6. `commitInsert` 执行真实插入；
7. 插入未完全兑现时，根据声明的回滚模式调用 `rollback`。

### `TransactionCapabilities`

`exactSimulation` 表示同一 tick、同一有效句柄下，真实提交不会无原因少于模拟结果。

`exactSplit` 表示 `resize` 能保留资源身份，并精确构造指定数量。

回滚模式：

| 模式             | 说明                  |
|----------------|---------------------|
| `NATIVE`       | 句柄具有原生事务或可撤销提交能力    |
| `COMPENSATING` | 通过反向写入源端补偿未插入资源     |
| `NONE`         | 没有回滚能力，不满足统一管线的注册要求 |

常用声明：

```java
TransactionCapabilities.exactCompensating()
```

不要把“通常能成功”声明为精确模拟。错误声明可能导致资源丢失或复制。

真实提交阶段抛出异常时，管线无法证明底层是否已经修改状态，因此会把本次传输标记为“提交状态未知”并立即终止。适配器可以在模拟阶段安全返回失败，但不得吞掉真实提交异常。

## 稳定资源 ID 与位序

资源类型的 `ResourceLocation` 是持久化身份，发布后不能随意修改。

Forge 1.20.1 已占用的位序：

| bitOffset | ID                             |
|----------:|--------------------------------|
|         0 | `staticlogistics:item`         |
|         1 | `staticlogistics:fluid`        |
|         2 | `staticlogistics:energy`       |
|         3 | `staticlogistics:mek_gas`      |
|         4 | `staticlogistics:mek_infusion` |
|         5 | `staticlogistics:mek_pigment`  |
|         6 | `staticlogistics:mek_slurry`   |
|         7 | `staticlogistics:mek_heat`     |
|         8 | `staticlogistics:ars_source`   |
|         9 | `staticlogistics:botania_mana` |
|        10 | `staticlogistics:gtceu_energy` |

第三方适配器应从 11 开始分配，并与 StaticLogistics 维护者协调以避免冲突。位序 0–31 还会投影到旧版 `int`
mask，修改已发布位序会破坏旧物品和旧存档选择。

注册中心会拒绝：

- 重复的资源 ID；
- 重复的位序；
- 负数或大于 31 的位序；
- 缺少精确模拟、精确缩量或回滚能力的适配器。

## 过滤、权限与路由

第三方适配器不应直接读取或修改面配置。

以下行为由 StaticLogistics 统一处理：

- 输入/输出开关；
- 资源类型选择；
- 频道匹配；
- 分组作用域和双向链接；
- 玩家及 FTB Teams 权限；
- 维度升级、距离和区块加载检查；
- 输入/输出过滤器；
- 存量维持；
- 失败冷却和传输统计。

适配器只负责资源句柄的解析和事务传输。

## 能力缓存

Forge 1.20.1 内建适配器使用 StaticLogistics 的 `LazyOptional` 能力缓存，并监听能力失效。第三方 `ResourceAdapter` 的
`resolve` 由第三方实现；如果自行缓存能力，必须监听 `LazyOptional` 失效并在方块替换、区块卸载或能力重建后丢弃旧句柄。

不得永久保存从方块实体取得的能力实例。

## 传输事件

```java
@SubscribeEvent
public static void beforeTransfer(PreTransferEvent event) {
    ResourceLocation typeId = event.getResourceTypeId();
    if (shouldBlock(typeId, event.getSourceNode(), event.getTargetNode())) {
        event.setCanceled(true);
    }
}

@SubscribeEvent
public static void afterTransfer(PostTransferEvent event) {
    ResourceLocation typeId = event.getResourceTypeId();
    long amount = event.getTransferredAmount();
    boolean complete = event.isSuccess();
}
```

事件只暴露稳定资源 ID，不暴露内部 `LogisticsResource` 实例。

## 错误处理要求

- 注释使用中文，异常与日志消息使用英文。
- `resolve` 无能力时返回 `null`，不要抛出正常缺失异常。
- 不要吞掉提交、缩量或回滚错误。
- 数量必须为正数，并对底层 `int` API 做非负饱和转换。
- 模拟方法不得改变资源状态。
- `rollback` 返回实际恢复的数量，而不是请求恢复的数量。

## 发布前检查

- 使用唯一且稳定的资源 ID；
- 分配未占用且固定的 bitOffset；
- 提供资源类型翻译键和图标；
- 验证空句柄、空资源、目标已满和源端变化；
- 验证部分插入及回滚；
- 验证方块替换和能力失效；
- 验证旧存档 mask 不会映射到错误类型；
- 验证专用服务器环境不加载客户端类。
