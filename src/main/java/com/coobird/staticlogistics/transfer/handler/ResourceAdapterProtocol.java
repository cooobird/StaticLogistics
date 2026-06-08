package com.coobird.staticlogistics.transfer.handler;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.LogisticsResource;
import com.coobird.staticlogistics.storage.link.LinkManager;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.TransferContext;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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
    private @Nullable FaceConfigComposite sourceCfg;
    private boolean isPullMode;
    private @Nullable TransferContext transferContext;

    public ResourceAdapterProtocol(LogisticsResource<C> adapter) {
        this.adapter = adapter;
    }

    /**
     * 更新上下文（不重建实例）。每次 performTransfer 调用前更新。
     */
    public void updateContext(@Nullable FaceConfigComposite sourceCfg, boolean isPullMode,
                              @Nullable TransferContext transferContext) {
        this.sourceCfg = sourceCfg;
        this.isPullMode = isPullMode;
        this.transferContext = transferContext;
    }

    @Override
    public ExtractionResult<Object> simulateExtract(C source, int max) {
        @SuppressWarnings("unchecked")
        ExtractionResult<Object> result = (ExtractionResult<Object>) adapter.extractTyped(source, max, true, sourceCfg, isPullMode, transferContext);
        return result;
    }

    @Override
    public int executeInsert(C dest, Object value) {
        return (int) adapter.insertTyped(dest, value, false, sourceCfg, isPullMode, transferContext);
    }

    @Override
    public void commitExtract(C source, ExtractionResult<Object> result, int actual) {
        adapter.commitExtract(source, result, actual, sourceCfg, isPullMode, transferContext);
    }

    @Override
    public boolean isEmpty(ExtractionResult<Object> result) {
        return adapter.isEmptyResult(result.value());
    }

    @Override
    public boolean canInsert(C dest, Object value, LogisticsNode targetNode) {
        if (transferContext == null) return true;
        // 拉模式下不过滤目标端（源端过滤已在 extractTyped 中处理）
        if (isPullMode) return true;
        // 查找目标面配置，检查输入过滤器
        ServerLevel targetLevel = transferContext.level().getServer().getLevel(
            targetNode.gPos().dimension());
        if (targetLevel == null) return true;
        FaceConfigComposite targetCfg = LinkManager.get(targetLevel).getFaceConfig(targetNode.toKey());
        if (targetCfg == null) return true;
        return adapter.canInsertToTarget(dest, value, targetCfg);
    }

    // ── 批量模式 ──

    @Override
    @SuppressWarnings("unchecked")
    public BulkExtractionResult<Object> extractBulk(C source, long maxAmount) {
        BulkExtractionResult<?> result = adapter.extractBulkTyped(source, maxAmount, true, sourceCfg, isPullMode, transferContext);
        return (BulkExtractionResult<Object>) result;
    }

    @Override
    public long insertBulk(C dest, BulkExtractionResult<Object> bulk, LogisticsNode targetNode) {
        // 检查目标过滤器
        if (transferContext != null && !isPullMode) {
            ServerLevel targetLevel = transferContext.level().getServer().getLevel(
                targetNode.gPos().dimension());
            if (targetLevel != null) {
                FaceConfigComposite targetCfg = LinkManager.get(targetLevel).getFaceConfig(targetNode.toKey());
                if (targetCfg != null) {
                    // 过滤掉目标不接受的栈
                    List<ExtractionResult<Object>> filtered = new ArrayList<>();
                    for (ExtractionResult<Object> r : bulk.results()) {
                        if (adapter.canInsertToTarget(dest, r.value(), targetCfg)) {
                            filtered.add(r);
                        }
                    }
                    bulk = new BulkExtractionResult<>(filtered);
                }
            }
        }
        return adapter.insertBulkTyped(dest, bulk, false, sourceCfg, isPullMode, transferContext);
    }

    @Override
    public void commitBulkExtract(C source, BulkExtractionResult<Object> bulk, long actual) {
        adapter.commitBulkExtract(source, bulk, actual, sourceCfg, isPullMode, transferContext);
    }
}
