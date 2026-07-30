package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.api.transfer.TransactionCapabilities;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 物流资源适配器接口 —— 所有传输类型（物品/流体/能量/化学品/魔源/热量/魔力）的统一抽象。
 *
 * <p>实现此接口并通过 {@code TransferRegistries.registerAdapter(adapter, bitOffset)} 注册，
 * 即可将任意模组的资源类型接入 StaticLogistics 物流管线。
 *
 * <p><b>实现层级：</b></p>
 * <ul>
 *   <li><b>简单资源</b>（能量/魔源/热量）：覆写 {@link #extract} / {@link #insert}</li>
 *   <li><b>类型化资源</b>（化学品）：覆写 {@link #extractTyped} / {@link #insertTyped} / {@link #isEmptyResult}</li>
 *   <li><b>上下文感知资源</b>（物品/流体）：覆写带 {@code sourceCfg}/{@code context} 参数的重载，
 *       实现过滤器检查、提取策略、存量维持等高级逻辑</li>
 * </ul>
 *
 * @param <C> 资源句柄类型，由 {@link #resolve} 创建，单次传输迭代内有效
 */
public interface LogisticsResource<C> {

    /**
     * 唯一标识，如 {@code "staticlogistics:item"}
     */
    ResourceLocation typeId();

    /**
     * ARGB 显示颜色
     */
    int color();

    /**
     * 位掩码偏移，由 TransferRegistries 注册时显式分配。
     * 子类不应覆写此方法。
     * 返回 -1 表示尚未由注册中心包装。
     */
    default int bitOffset() {
        return -1;
    }

    /**
     * GUI 翻译键
     */
    String translationKey();

    /**
     * 类型图标
     */
    Supplier<ItemStack> iconSupplier();

    /**
     * 基础每 tick 传输量
     */
    IntSupplier baseStackSizeSupplier();

    /**
     * 传输失败后是否需要冷却。默认 {@code true}。
     */
    default boolean requiresCooldown() {
        return true;
    }

    /**
     * 是否需要有效链接才能传输。默认 {@code true}。
     */
    default boolean requiresValidLinks() {
        return true;
    }

    /**
     * 声明底层句柄可兑现的事务保证；注册中心会拒绝不满足统一管线要求的实现。
     */
    TransactionCapabilities transactionCapabilities();

    /**
     * 获取图标 ItemStack
     */
    default ItemStack getIcon() {
        return iconSupplier().get();
    }

    /**
     * 获取基础传输量
     */
    default int getBaseStackSize() {
        return baseStackSizeSupplier().getAsInt();
    }

    /**
     * 获取指定位置和面上的资源操作句柄。
     *
     * @return 句柄，不可用时返回 {@code null}
     */
    @Nullable C resolve(ServerLevel level, BlockPos pos, Direction face);

    /**
     * 返回可由 NeoForge 自动失效的原生方块能力；自定义句柄保持 {@code null}。
     * 声明后传输和只读探测都会走维度级能力缓存。
     */
    @Nullable
    default BlockCapability<C, Direction> blockCapability() {
        return null;
    }

    /**
     * 快速检查该位置是否支持此资源。默认委托给 {@link #resolve}，可覆盖做轻量判断。
     */
    default boolean isPresent(ServerLevel level, BlockPos pos, Direction face) {
        BlockCapability<C, Direction> capability = blockCapability();
        return capability == null
            ? resolve(level, pos, face) != null
            : LinkManager.get(level)
            .getCapability(pos, face, capability) != null;
    }

    /**
     * 从句柄中提取资源（简单模式）。
     *
     * @param simulate {@code true} 仅探测不修改状态
     */
    default long extract(C handle, long amount, boolean simulate) {
        return 0;
    }

    /**
     * 向句柄中注入资源（简单模式）。
     *
     * @param simulate {@code true} 仅探测不修改状态
     */
    default long insert(C handle, long amount, boolean simulate) {
        return 0;
    }

    /**
     * 类型化提取。
     * <p>返回值可能是 {@code Long}（简单资源）、{@code ChemicalStack}（化学品）、
     * {@code FluidStack}（流体）或 {@code ItemStack}（物品）。
     * 默认实现委托给 {@link #extract}，返回 {@code ExtractionResult<Long>}。
     *
     * @param simulate {@code true} 仅探测不修改状态
     */
    @SuppressWarnings("unchecked")
    default ExtractionResult<?> extractTyped(C handle, long amount, boolean simulate) {
        return ExtractionResult.of(extract(handle, amount, simulate));
    }

    /**
     * 类型化注入。
     * <p>{@code value} 的类型取决于 {@link #extractTyped} 的返回值类型。
     * 默认实现将 {@code Long} 转发给 {@link #insert}，其他类型返回 0。
     *
     * @param simulate {@code true} 仅探测不修改状态
     * @return 实际注入量
     */
    default long insertTyped(C handle, Object value, boolean simulate) {
        if (value instanceof Long amount) {
            return insert(handle, amount, simulate);
        }
        if (value instanceof Number num) {
            return insert(handle, num.longValue(), simulate);
        }
        return 0;
    }

    /**
     * 检查类型化提取结果是否为空。
     * <p>默认实现：{@code null} 或 {@code Long <= 0} 视为空。
     * 化学品应覆写检查 {@code ChemicalStack.isEmpty()}，流体检查 {@code FluidStack.isEmpty()}。
     */
    default boolean isEmptyResult(@Nullable Object value) {
        if (value == null) return true;
        if (value instanceof Long l) return l <= 0;
        return false;
    }

    /**
     * 上下文感知提取 —— 用于需要过滤器检查和提取策略的资源。
     * <p>默认实现委托给无上下文版本。物品/流体应覆写此方法实现输出过滤器检查。
     *
     * @param sourceCfg  源面配置（含过滤器），可为 null
     * @param isPullMode 是否为拉模式
     * @param context    传输上下文（含提取策略/游标），可为 null
     */
    default ExtractionResult<?> extractTyped(C handle, long amount, boolean simulate,
                                             @Nullable FaceConfigComposite sourceCfg, boolean isPullMode,
                                             @Nullable TransferContext context) {
        return extractTyped(handle, amount, simulate);
    }

    /**
     * 上下文感知注入 —— 用于需要过滤器检查的资源。
     * <p>默认实现委托给无上下文版本。物品/流体应覆写此方法实现输入过滤器检查。
     *
     * @param sourceCfg  源面配置（用于判断推/拉模式下的过滤规则），可为 null
     * @param isPullMode 是否为拉模式
     * @param context    传输上下文，可为 null
     * @return 实际注入量
     */
    default long insertTyped(C handle, Object value, boolean simulate,
                             @Nullable FaceConfigComposite sourceCfg, boolean isPullMode,
                             @Nullable TransferContext context) {
        return insertTyped(handle, value, simulate);
    }

    /**
     * 目标端过滤器检查 —— 检查目标是否接受该资源。
     * <p>默认返回 {@code true}（不做过滤）。物品/流体应覆写此方法检查目标端输入过滤器和存量维持。
     *
     * @param handle    目标句柄
     * @param value     待插入的资源值
     * @param targetCfg 目标面配置（含输入过滤器和存量维持设置）
     */
    default boolean canInsertToTarget(C handle, Object value, FaceConfigComposite targetCfg) {
        return true;
    }

    /**
     * 返回目标当前允许接收的最大数量。
     */
    default long maxInsertToTarget(C handle, Object value, FaceConfigComposite targetCfg) {
        return Long.MAX_VALUE;
    }

    /**
     * 提交提取并返回源端实际取出的资源。
     * 默认实现重新执行真实提取；需要固定槽位等语义的资源应覆盖此方法。
     */
    default ExtractionResult<?> executeExtract(C handle, ExtractionResult<?> simulated, long requested,
                                               @Nullable FaceConfigComposite sourceCfg, boolean isPullMode,
                                               @Nullable TransferContext context) {
        return extractTyped(handle, requested, false, sourceCfg, isPullMode, context);
    }

    /**
     * 返回资源值携带的数量；无法判断时返回负数。
     */
    default long amountOf(Object value) {
        return value instanceof Number number ? Math.max(0L, number.longValue()) : -1L;
    }

    /**
     * 创建同类型且指定数量的资源值；无法安全拆分时返回 null。
     */
    @Nullable
    default Object withAmount(Object value, long amount) {
        return value instanceof Number ? Math.max(0L, amount) : null;
    }

    /**
     * 将未提交的资源补偿回源端。
     */
    default boolean rollback(C source, Object value, long amount,
                             @Nullable FaceConfigComposite sourceConfig,
                             boolean pullMode,
                             @Nullable TransferContext context) {
        if (amount <= 0L) return true;
        Object rollbackValue = withAmount(value, amount);
        return rollbackValue != null
            && insertTyped(source, rollbackValue, false, sourceConfig, pullMode, context) >= amount;
    }
}
