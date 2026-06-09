package com.coobird.staticlogistics.api;

import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.TransferContext;
import com.coobird.staticlogistics.transfer.handler.ExtractionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 物流资源适配器接口 —— 所有传输类型的统一抽象。
 *
 * <p>bitOffset 由 {@link com.coobird.staticlogistics.logic.TransferRegistries} 自动分配，
 * 子类无需覆写。
 *
 * @param <C> 资源句柄类型
 */
public interface LogisticsResource<C> {

    ResourceLocation typeId();

    int color();

    String translationKey();

    Supplier<ItemStack> iconSupplier();

    IntSupplier baseStackSizeSupplier();

    /**
     * 位掩码偏移，由注册中心自动分配。
     * 子类不应覆写此方法。
     * 返回 -1 表示未分配（由 TransferRegistries 的 wrapper 覆盖）。
     */
    default int bitOffset() {
        return -1;
    }

    default boolean requiresCooldown() {
        return true;
    }

    default boolean requiresValidLinks() {
        return true;
    }

    default boolean isSimpleResource() {
        return false;
    }

    default int getFlag() {
        return 1 << bitOffset();
    }

    default ItemStack getIcon() {
        return iconSupplier().get();
    }

    default int getBaseStackSize() {
        return baseStackSizeSupplier().getAsInt();
    }

    @Nullable C resolve(ServerLevel level, BlockPos pos, Direction face);

    default boolean isPresent(ServerLevel level, BlockPos pos, Direction face) {
        return resolve(level, pos, face) != null;
    }

    default long extract(C handle, long amount, boolean simulate) {
        return 0;
    }

    default long insert(C handle, long amount, boolean simulate) {
        return 0;
    }

    default ExtractionResult<?> extractTyped(C handle, long amount, boolean simulate) {
        return ExtractionResult.of(extract(handle, amount, simulate));
    }

    default long insertTyped(C handle, Object value, boolean simulate) {
        if (value instanceof Long amount) return insert(handle, amount, simulate);
        if (value instanceof Number num) return insert(handle, num.longValue(), simulate);
        return 0;
    }

    default boolean isEmptyResult(@Nullable Object value) {
        if (value == null) return true;
        if (value instanceof Long l) return l <= 0;
        return false;
    }

    default ExtractionResult<?> extractTyped(C handle, long amount, boolean simulate,
                                             @Nullable FaceConfigComposite sourceCfg, boolean isPullMode,
                                             @Nullable TransferContext context) {
        return extractTyped(handle, amount, simulate);
    }

    default long insertTyped(C handle, Object value, boolean simulate,
                             @Nullable FaceConfigComposite sourceCfg, boolean isPullMode,
                             @Nullable TransferContext context) {
        return insertTyped(handle, value, simulate);
    }

    default boolean canInsertToTarget(C handle, Object value, FaceConfigComposite targetCfg) {
        return true;
    }

    default void commitExtract(C handle, ExtractionResult<?> result, long actual,
                               @Nullable FaceConfigComposite sourceCfg, boolean isPullMode,
                               @Nullable TransferContext context) {
        extractTyped(handle, actual, false, sourceCfg, isPullMode, context);
    }
}
