package com.coobird.staticlogistics.api.transfer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 第三方资源类型的类型安全事务适配接口。
 *
 * @param <C> Forge 能力或资源句柄类型
 * @param <V> 资源值类型
 */
public interface ResourceAdapter<C, V> {
    ResourceLocation typeId();

    Class<V> valueType();

    int color();

    String translationKey();

    Supplier<ItemStack> iconSupplier();

    IntSupplier baseStackSizeSupplier();

    TransactionCapabilities transactionCapabilities();

    @Nullable C resolve(ServerLevel level, BlockPos pos, Direction face);

    SimulationResult<V> simulateExtract(C source, TransferRequest request);

    CommitResult<V> commitExtract(C source, SimulationResult<V> simulation, TransferRequest request);

    long simulateInsert(C target, ResourceValue<V> resource, TransferRequest request);

    long commitInsert(C target, ResourceValue<V> resource, TransferRequest request);

    ResourceValue<V> resize(ResourceValue<V> resource, long amount);

    /**
     * 将未插入的资源回滚到源端，并返回成功恢复的数量。
     */
    default long rollback(C source, ResourceValue<V> resource, TransferRequest request) {
        return 0L;
    }

    default boolean requiresCooldown() {
        return true;
    }

    default boolean requiresValidLinks() {
        return true;
    }
}
