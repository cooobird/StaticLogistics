package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.api.ITransferHandler;
import com.coobird.staticlogistics.api.transfer.ResourceAdapter;
import com.coobird.staticlogistics.api.transfer.ResourceAdapterRegistrar;
import com.coobird.staticlogistics.api.transfer.TransactionCapabilities;
import com.coobird.staticlogistics.logistics.node.EndpointConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/// 传输类型注册中心 —— 管理所有 LogisticsResource 实例。
/// 运行时位号由注册中心自动分配；持久化身份使用资源 ID。
public final class TransferRegistries {
    private record RegistryState(Map<ResourceLocation, LogisticsResource<?>> resources,
                                 Map<ResourceLocation, ITransferHandler> handlers,
                                 Collection<LogisticsResource<?>> active,
                                 int generation) {
    }

    private static volatile RegistryState state = new RegistryState(Map.of(), Map.of(), List.of(), 0);
    private static volatile boolean frozen;

    private TransferRegistries() {
    }

    /// 注册适配器并自动分配空闲位。
    public static synchronized <C> void registerAdapter(LogisticsResource<C> adapter) {
        ensureRegistrationOpen();
        ResourceLocation id = adapter.typeId();
        RegistryState current = state;
        if (current.resources().containsKey(id)) {
            throw new IllegalStateException("Logistics resource type is already registered: " + id);
        }
        validateTransactionalContract(adapter);

        int bitOffset = nextBitOffset();
        LogisticsResource<C> wrapped = new BitOffsetWrapper<>(adapter, bitOffset);
        publish(current, id, wrapped);
    }

    /// 注册公开的类型安全资源适配器。
    public static synchronized <C, V> void registerAdapter(ResourceAdapter<C, V> adapter) {
        registerAdapter(new TypedResourceAdapterBridge<>(adapter));
    }

    public static ResourceAdapterRegistrar registrar() {
        return TransferRegistries::registerAdapter;
    }

    private static void publish(RegistryState current, ResourceLocation id, LogisticsResource<?> resource) {
        Map<ResourceLocation, LogisticsResource<?>> resources = new LinkedHashMap<>(current.resources());
        Map<ResourceLocation, ITransferHandler> handlers = new LinkedHashMap<>(current.handlers());
        resources.put(id, resource);
        handlers.put(id, new ResourceAdapterHandler<>(resource));
        Map<ResourceLocation, LogisticsResource<?>> immutableResources =
            Collections.unmodifiableMap(resources);
        state = new RegistryState(immutableResources, Collections.unmodifiableMap(handlers),
            List.copyOf(immutableResources.values()), current.generation() + 1);
    }

    private static int nextBitOffset() {
        long occupied = 0L;
        for (LogisticsResource<?> resource : state.resources().values()) occupied |= 1L << resource.bitOffset();
        return TypeBitAllocator.firstFree(occupied);
    }

    private static void validateTransactionalContract(LogisticsResource<?> adapter) {
        TransactionCapabilities capabilities = adapter.transactionCapabilities();
        if (!capabilities.exactSimulation() || !capabilities.exactSplit()
            || capabilities.rollbackMode() == TransactionCapabilities.RollbackMode.NONE) {
            throw new IllegalArgumentException(
                "Logistics resource must support exact simulation, value splitting and rollback: " + adapter.typeId());
        }
    }

    public static synchronized void freeze() {
        frozen = true;
    }

    private static void ensureRegistrationOpen() {
        if (frozen) throw new IllegalStateException("Logistics resource registration is already frozen");
    }

    @Nullable
    public static LogisticsResource<?> get(ResourceLocation id) {
        return state.resources().get(id);
    }

    public static Collection<LogisticsResource<?>> getAllActive() {
        return state.active();
    }

    @Nullable
    public static ITransferHandler getHandler(LogisticsResource<?> resource) {
        return state.handlers().get(resource.typeId());
    }

    public static int generation() {
        return state.generation();
    }

    /// 包装器：覆写 bitOffset() 为注册时分配的运行时值。
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
        public Component unit() {
            return delegate.unit();
        }

        @Override
        public Supplier<ItemStack> iconSupplier() {
            return delegate.iconSupplier();
        }

        @Override
        public LongSupplier baseStackSizeSupplier() {
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
        public TransactionCapabilities transactionCapabilities() {
            return delegate.transactionCapabilities();
        }

        @Override
        public @Nullable C resolve(ServerLevel level, BlockPos pos, Direction face) {
            return delegate.resolve(level, pos, face);
        }

        @Override
        public @Nullable Capability<C> blockCapability() {
            return delegate.blockCapability();
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
        public ExtractionResult<?> extractTyped(C handle, long amount, boolean simulate, @Nullable EndpointConfig sourceCfg, boolean isPullMode, @Nullable TransferContext context) {
            return delegate.extractTyped(handle, amount, simulate, sourceCfg, isPullMode, context);
        }

        @Override
        public long insertTyped(C handle, Object value, boolean simulate, @Nullable EndpointConfig sourceCfg, boolean isPullMode, @Nullable TransferContext context) {
            return delegate.insertTyped(handle, value, simulate, sourceCfg, isPullMode, context);
        }

        @Override
        public boolean canInsertToTarget(C handle, Object value, EndpointConfig targetCfg) {
            return delegate.canInsertToTarget(handle, value, targetCfg);
        }

        @Override
        public long maxInsertToTarget(C handle, Object value, EndpointConfig targetCfg) {
            return delegate.maxInsertToTarget(handle, value, targetCfg);
        }

        @Override
        public long evaluateInsertToTarget(C handle, Object value, EndpointConfig targetCfg) {
            return delegate.evaluateInsertToTarget(handle, value, targetCfg);
        }

        @Override
        public ExtractionResult<?> executeExtract(C handle, ExtractionResult<?> simulated, long requested,
                                                  @Nullable EndpointConfig sourceCfg, boolean isPullMode,
                                                  @Nullable TransferContext context) {
            return delegate.executeExtract(handle, simulated, requested, sourceCfg, isPullMode, context);
        }

        @Override
        public ResourceExtractionSession openExtractionSession(
            C handle, @Nullable EndpointConfig sourceCfg, boolean isPullMode,
            @Nullable TransferContext context
        ) {
            return delegate.openExtractionSession(handle, sourceCfg, isPullMode, context);
        }

        @Override
        public long amountOf(Object value) {
            return delegate.amountOf(value);
        }

        @Override
        public Object withAmount(Object value, long amount) {
            return delegate.withAmount(value, amount);
        }

        @Override
        public boolean rollback(C source, Object value, long amount,
                                @Nullable EndpointConfig sourceConfig,
                                boolean pullMode,
                                @Nullable TransferContext context) {
            return delegate.rollback(source, value, amount, sourceConfig, pullMode, context);
        }
    }
}
