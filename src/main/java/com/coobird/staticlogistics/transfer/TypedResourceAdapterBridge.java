package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.api.transfer.*;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 将公开的类型安全 SPI 接入核心传输协议。
 */
final class TypedResourceAdapterBridge<C, V> implements LogisticsResource<C> {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ResourceAdapter<C, V> adapter;
    private final int bitOffset;

    TypedResourceAdapterBridge(ResourceAdapter<C, V> adapter, int bitOffset) {
        this.adapter = adapter;
        this.bitOffset = bitOffset;
    }

    @Override
    public ResourceLocation typeId() {
        return adapter.typeId();
    }

    @Override
    public int bitOffset() {
        return bitOffset;
    }

    @Override
    public int color() {
        return adapter.color();
    }

    @Override
    public String translationKey() {
        return adapter.translationKey();
    }

    @Override
    public Supplier<ItemStack> iconSupplier() {
        return adapter.iconSupplier();
    }

    @Override
    public IntSupplier baseStackSizeSupplier() {
        return adapter.baseStackSizeSupplier();
    }

    @Override
    public boolean requiresCooldown() {
        return adapter.requiresCooldown();
    }

    @Override
    public boolean requiresValidLinks() {
        return adapter.requiresValidLinks();
    }

    @Override
    public TransactionCapabilities transactionCapabilities() {
        return adapter.transactionCapabilities();
    }

    @Override
    public @Nullable C resolve(ServerLevel level, BlockPos pos, Direction face) {
        return adapter.resolve(level, pos, face);
    }

    @Override
    public ExtractionResult<?> extractTyped(C handle, long amount, boolean simulate,
                                            @Nullable FaceConfigComposite sourceConfig,
                                            boolean pullMode, @Nullable TransferContext context) {
        if (!simulate || context == null || amount <= 0L) return ExtractionResult.of(null);
        TransferRequest request = request(context, amount);
        try {
            SimulationResult<V> result = adapter.simulateExtract(handle, request);
            ResourceValue<V> resource = validateSimulation(result, request);
            return resource == null ? ExtractionResult.of(null) : ExtractionResult.of(resource, result);
        } catch (RuntimeException exception) {
            LOGGER.error("Resource adapter simulation failed for {}", typeId(), exception);
            return ExtractionResult.of(null);
        }
    }

    @Override
    public long insertTyped(C handle, Object value, boolean simulate,
                            @Nullable FaceConfigComposite sourceConfig,
                            boolean pullMode, @Nullable TransferContext context) {
        ResourceValue<V> resource = castResource(value);
        if (resource == null || context == null) return 0L;
        TransferRequest request = request(context, resource.amount());
        try {
            long accepted = simulate
                ? adapter.simulateInsert(handle, resource, request)
                : adapter.commitInsert(handle, resource, request);
            return validateAmount(accepted, resource.amount(),
                simulate ? "simulation insert" : "commit insert");
        } catch (RuntimeException exception) {
            LOGGER.error("Resource adapter insert failed for {}", typeId(), exception);
            return 0L;
        }
    }

    @Override
    public ExtractionResult<?> executeExtract(C handle, ExtractionResult<?> simulated,
                                              long requested,
                                              @Nullable FaceConfigComposite sourceConfig,
                                              boolean pullMode,
                                              @Nullable TransferContext context) {
        if (context == null || requested <= 0L
            || !(simulated.context() instanceof SimulationResult<?> rawSimulation)) {
            return ExtractionResult.of(null);
        }
        SimulationResult<V> simulation = castSimulation(rawSimulation);
        if (simulation == null) return ExtractionResult.of(null);
        TransferRequest request = request(context, requested);
        try {
            CommitResult<V> result = adapter.commitExtract(handle, simulation, request);
            if (result == null || result.status() != CommitResult.Status.SUCCESS
                || result.resource().isEmpty()) {
                if (result == null) {
                    LOGGER.error("Resource adapter returned null commit result for {}", typeId());
                }
                return ExtractionResult.of(null);
            }
            ResourceValue<V> resource = result.resource().orElseThrow();
            if (!isValidResource(resource)) {
                LOGGER.error("Resource adapter returned an invalid committed value for {}", typeId());
                return ExtractionResult.of(null);
            }
            return ExtractionResult.of(resource, simulation);
        } catch (RuntimeException exception) {
            LOGGER.error("Resource adapter commit failed for {}", typeId(), exception);
            return ExtractionResult.of(null);
        }
    }

    @Override
    public boolean isEmptyResult(@Nullable Object value) {
        return castResource(value) == null;
    }

    @Override
    public long amountOf(Object value) {
        ResourceValue<V> resource = castResource(value);
        return resource == null ? -1L : resource.amount();
    }

    @Override
    public Object withAmount(Object value, long amount) {
        ResourceValue<V> resource = castResource(value);
        if (resource == null || amount <= 0L || amount > resource.amount()) return null;
        try {
            ResourceValue<V> resized = adapter.resize(resource, amount);
            return isValidResource(resized) && resized.amount() == amount ? resized : null;
        } catch (RuntimeException exception) {
            LOGGER.error("Resource adapter resize failed for {}", typeId(), exception);
            return null;
        }
    }

    @Override
    public boolean rollback(C source, Object value, long amount,
                            @Nullable FaceConfigComposite sourceConfig,
                            boolean pullMode, @Nullable TransferContext context) {
        ResourceValue<V> resource = castResource(value);
        if (context == null || resource == null || amount <= 0L || amount > resource.amount()) return false;
        try {
            ResourceValue<V> rollbackValue = amount == resource.amount()
                ? resource : adapter.resize(resource, amount);
            if (!isValidResource(rollbackValue) || rollbackValue.amount() != amount) return false;
            long restored = adapter.rollback(source, rollbackValue, request(context, amount));
            if (restored != amount) {
                LOGGER.error("Resource adapter rollback restored {} of {} for {}",
                    restored, amount, typeId());
                return false;
            }
            return true;
        } catch (RuntimeException exception) {
            LOGGER.error("Resource adapter rollback failed for {}", typeId(), exception);
            return false;
        }
    }

    private TransferRequest request(TransferContext context, long amount) {
        return new TransferRequest(typeId(), context.sourceNode(), amount,
            context.isPullMode(), context.currentTick());
    }

    @Nullable
    private ResourceValue<V> validateSimulation(@Nullable SimulationResult<V> result,
                                                TransferRequest request) {
        if (result == null) {
            LOGGER.error("Resource adapter returned null simulation result for {}", typeId());
            return null;
        }
        ResourceValue<V> resource = result.resource().orElse(null);
        if (resource == null) return null;
        if (!isValidResource(resource) || resource.amount() > request.maxAmount()) {
            LOGGER.error("Resource adapter returned an invalid simulated value for {}", typeId());
            return null;
        }
        return resource;
    }

    private long validateAmount(long amount, long maximum, String phase) {
        if (amount >= 0L && amount <= maximum) return amount;
        LOGGER.error("Resource adapter returned invalid {} amount {} for {} (maximum {})",
            phase, amount, typeId(), maximum);
        return 0L;
    }

    private boolean isValidResource(@Nullable ResourceValue<V> resource) {
        return resource != null && resource.amount() > 0L
            && adapter.valueType().isInstance(resource.value());
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private ResourceValue<V> castResource(@Nullable Object value) {
        if (!(value instanceof ResourceValue<?> resource)
            || !adapter.valueType().isInstance(resource.value())
            || resource.amount() <= 0L) return null;
        return (ResourceValue<V>) resource;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private SimulationResult<V> castSimulation(SimulationResult<?> simulation) {
        ResourceValue<?> resource = simulation.resource().orElse(null);
        if (resource != null && !adapter.valueType().isInstance(resource.value())) return null;
        return (SimulationResult<V>) simulation;
    }
}
