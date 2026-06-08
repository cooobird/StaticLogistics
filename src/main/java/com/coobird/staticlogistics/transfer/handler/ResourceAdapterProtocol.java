package com.coobird.staticlogistics.transfer.handler;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.LogisticsResource;
import com.coobird.staticlogistics.storage.link.LinkManager;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.TransferContext;
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
    public void commitExtract(C source, ExtractionResult<Object> result, long actual) {
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

    /**
     * 获取适配器（用于简单资源的直接传输）。
     */
    public LogisticsResource<C> getAdapter() {
        return adapter;
    }
}
