package com.coobird.staticlogistics.logic.type;

import com.coobird.staticlogistics.api.ITransferHandler;
import com.coobird.staticlogistics.api.LogisticsResource;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.TransferContext;
import com.coobird.staticlogistics.transfer.handler.ExtractionResult;
import com.coobird.staticlogistics.transfer.handler.ResourceAdapterHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 传输类型注册中心 —— 管理所有 {@link LogisticsResource} 实例。
 * 资源类型 bitOffset 必须显式分配；0-31 同时用于兼容旧 int 掩码。
 */
public class TransferRegistries {
    private static final Map<ResourceLocation, LogisticsResource<?>> RESOURCES = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ITransferHandler> HANDLERS = new LinkedHashMap<>();
    private static int generation = 0;

    /**
     * 注册一个显式分配稳定 bitOffset 的资源适配器。
     */
    public static <C> void registerAdapter(LogisticsResource<C> adapter, int bitOffset) {
        if (bitOffset < 0) {
            throw new IllegalArgumentException("bitOffset must be non-negative for logistics resource type " + adapter.typeId());
        }

        ResourceLocation id = adapter.typeId();
        ensureBitOffsetAvailable(id, bitOffset);

        LogisticsResource<C> wrapped = new BitOffsetWrapper<>(adapter, bitOffset);
        RESOURCES.put(id, wrapped);
        HANDLERS.put(id, new ResourceAdapterHandler<>(wrapped));
        generation++;
    }

    private static void ensureBitOffsetAvailable(ResourceLocation id, int bitOffset) {
        LogisticsResource<?> existing = RESOURCES.get(id);
        if (existing != null && existing.bitOffset() != bitOffset) {
            throw new IllegalArgumentException("Resource type " + id + " is already assigned to bitOffset "
                + existing.bitOffset() + "; cannot reassign it to " + bitOffset);
        }

        for (LogisticsResource<?> resource : RESOURCES.values()) {
            if (resource.bitOffset() == bitOffset && !resource.typeId().equals(id)) {
                throw new IllegalArgumentException("bitOffset " + bitOffset + " is already used by "
                    + resource.typeId() + "; cannot assign it to " + id);
            }
        }
    }

    @Nullable
    public static LogisticsResource<?> get(ResourceLocation id) {
        return RESOURCES.get(id);
    }

    public static Collection<LogisticsResource<?>> getAllActive() {
        return RESOURCES.values();
    }

    @Nullable
    public static ITransferHandler getHandler(LogisticsResource<?> resource) {
        return HANDLERS.get(resource.typeId());
    }

    public static int generation() {
        return generation;
    }

    /**
     * 包装器：覆写 bitOffset() 为注册时分配的稳定值。
     */
    private record BitOffsetWrapper<C>(LogisticsResource<C> delegate,
                                       int assignedOffset) implements LogisticsResource<C> {

        @Override
        public int bitOffset() {
            return assignedOffset;
        }

        @Override
        public ResourceLocation typeId() {
            return delegate.typeId();
        }

        @Override
        public int color() {
            return delegate.color();
        }

        @Override
        public String translationKey() {
            return delegate.translationKey();
        }

        @Override
        public Supplier<ItemStack> iconSupplier() {
            return delegate.iconSupplier();
        }

        @Override
        public IntSupplier baseStackSizeSupplier() {
            return delegate.baseStackSizeSupplier();
        }

        @Override
        public boolean requiresCooldown() {
            return delegate.requiresCooldown();
        }

        @Override
        public boolean requiresValidLinks() {
            return delegate.requiresValidLinks();
        }

        @Override
        public boolean isSimpleResource() {
            return delegate.isSimpleResource();
        }

        @Override
        public @Nullable C resolve(ServerLevel level, BlockPos pos, Direction face) {
            return delegate.resolve(level, pos, face);
        }

        @Override
        public boolean isPresent(ServerLevel level, BlockPos pos, Direction face) {
            return delegate.isPresent(level, pos, face);
        }

        @Override
        public long extract(C handle, long amount, boolean simulate) {
            return delegate.extract(handle, amount, simulate);
        }

        @Override
        public long insert(C handle, long amount, boolean simulate) {
            return delegate.insert(handle, amount, simulate);
        }

        @Override
        public ExtractionResult<?> extractTyped(C handle, long amount, boolean simulate) {
            return delegate.extractTyped(handle, amount, simulate);
        }

        @Override
        public long insertTyped(C handle, Object value, boolean simulate) {
            return delegate.insertTyped(handle, value, simulate);
        }

        @Override
        public boolean isEmptyResult(@Nullable Object value) {
            return delegate.isEmptyResult(value);
        }

        @Override
        public ExtractionResult<?> extractTyped(C handle, long amount, boolean simulate, @Nullable FaceConfigComposite sourceCfg, boolean isPullMode, @Nullable TransferContext context) {
            return delegate.extractTyped(handle, amount, simulate, sourceCfg, isPullMode, context);
        }

        @Override
        public long insertTyped(C handle, Object value, boolean simulate, @Nullable FaceConfigComposite sourceCfg, boolean isPullMode, @Nullable TransferContext context) {
            return delegate.insertTyped(handle, value, simulate, sourceCfg, isPullMode, context);
        }

        @Override
        public boolean canInsertToTarget(C handle, Object value, FaceConfigComposite targetCfg) {
            return delegate.canInsertToTarget(handle, value, targetCfg);
        }

        @Override
        public void commitExtract(C handle, ExtractionResult<?> result, long actual, @Nullable FaceConfigComposite sourceCfg, boolean isPullMode, @Nullable TransferContext context) {
            delegate.commitExtract(handle, result, actual, sourceCfg, isPullMode, context);
        }
    }
}
