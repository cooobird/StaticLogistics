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
 */
public class ResourceAdapterProtocol<C> implements TransferUtils.TransferProtocol<C, Object> {
    private final LogisticsResource<C> adapter;
    private @Nullable FaceConfigComposite sourceCfg;
    private boolean isPullMode;
    private @Nullable TransferContext transferContext;

    public ResourceAdapterProtocol(LogisticsResource<C> adapter) {
        this.adapter = adapter;
    }

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
        if (isPullMode) return true;
        ServerLevel targetLevel = transferContext.level().getServer().getLevel(
            targetNode.gPos().dimension());
        if (targetLevel == null) return true;
        FaceConfigComposite targetCfg = LinkManager.get(targetLevel).getFaceConfig(targetNode.toKey());
        if (targetCfg == null) return true;
        return adapter.canInsertToTarget(dest, value, targetCfg);
    }

    public LogisticsResource<C> getAdapter() {
        return adapter;
    }
}
