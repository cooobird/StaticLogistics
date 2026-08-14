package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.logistics.node.FaceAddress;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * 将 {@link LogisticsResource} 适配为 {@link TransferUtils.TransferProtocol} 的协议适配器。
 *
 * <p>携带传输上下文（源面配置、推拉模式、TransferContext），
 * 调用 {@link LogisticsResource} 的上下文感知方法，使物品/流体等需要过滤器检查的资源
 * 能统一走 {@link TransferUtils#doTransferNodes} 管线。
 *
 * @param <C> 资源句柄类型
 */
public class ResourceAdapterProtocol<C> implements TransferUtils.TransferProtocol<C, Object> {
    private final LogisticsResource<C> adapter;
    private final @Nullable FaceConfigComposite sourceCfg;
    private final boolean isPullMode;
    private final @Nullable TransferContext transferContext;
    private long targetLimit = Long.MAX_VALUE;

    public ResourceAdapterProtocol(LogisticsResource<C> adapter,
                                   @Nullable FaceConfigComposite sourceCfg,
                                   boolean isPullMode,
                                   @Nullable TransferContext transferContext) {
        this.adapter = adapter;
        this.sourceCfg = sourceCfg;
        this.isPullMode = isPullMode;
        this.transferContext = transferContext;
    }

    @Override
    public ExtractionResult<Object> simulateExtract(C source, long max) {
        @SuppressWarnings("unchecked")
        ExtractionResult<Object> result = (ExtractionResult<Object>) adapter.extractTyped(source, max, true, sourceCfg, isPullMode, transferContext);
        return result;
    }

    @Override
    public long executeInsert(C dest, Object value) {
        return adapter.insertTyped(dest, value, false, sourceCfg, isPullMode, transferContext);
    }

    @Override
    public long simulateInsert(C dest, Object value) {
        return Math.min(targetLimit,
            adapter.insertTyped(dest, value, true, sourceCfg, isPullMode, transferContext));
    }

    @Override
    public ExtractionResult<Object> executeExtract(C source, ExtractionResult<Object> simulated, long requested) {
        @SuppressWarnings("unchecked")
        ExtractionResult<Object> actual = (ExtractionResult<Object>) adapter.executeExtract(
            source, simulated, requested, sourceCfg, isPullMode, transferContext);
        return actual;
    }

    @Override
    public long amountOf(Object value) {
        return adapter.amountOf(value);
    }

    @Override
    public Object withAmount(Object value, long amount) {
        return adapter.withAmount(value, amount);
    }

    @Override
    public boolean rollbackRemainder(C source, ExtractionResult<Object> extracted, long accepted) {
        long amount = adapter.amountOf(extracted.value());
        if (amount < 0L) return false;
        long remainder = amount - Math.max(0L, accepted);
        if (remainder <= 0L) return true;
        return adapter.rollback(source, extracted.value(), remainder,
            sourceCfg, isPullMode, transferContext);
    }

    @Override
    public boolean isEmpty(ExtractionResult<Object> result) {
        return adapter.isEmptyResult(result.value());
    }

    @Override
    public int maxTransactionsPerActivation() {
        return Math.max(1, adapter.maxTransactionsPerActivation());
    }

    @Override
    public boolean advanceRejectedCandidate(ExtractionResult<Object> simulated) {
        return adapter.advanceRejectedCandidate(simulated, sourceCfg, transferContext);
    }

    @Override
    public boolean canInsert(C dest, Object value, LogisticsNode targetNode) {
        targetLimit = Long.MAX_VALUE;
        if (transferContext == null) return true;
        // 拉模式下不过滤目标端（源端过滤已在 extractTyped 中处理）
        if (isPullMode) return true;
        // 查找目标面配置，检查输入过滤器
        ServerLevel targetLevel = transferContext.level().getServer().getLevel(
            targetNode.gPos().dimension());
        if (targetLevel == null) return true;
        FaceConfigComposite targetCfg = LinkManager.get(targetLevel).getFaceConfig(FaceAddress.of(targetNode));
        if (targetCfg == null) return true;
        targetLimit = Math.max(0L, adapter.maxInsertToTarget(dest, value, targetCfg));
        return targetLimit > 0L && adapter.canInsertToTarget(dest, value, targetCfg);
    }

    /**
     * 获取适配器（用于简单资源的直接传输）。
     */
    public LogisticsResource<C> getAdapter() {
        return adapter;
    }
}
