package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.logistics.node.EndpointAddress;
import com.coobird.staticlogistics.logistics.node.EndpointConfig;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

/// 将资源适配器操作转换为传输事务协议。
public class ResourceAdapterProtocol<C> implements TransferProtocol<C, Object> {
    private final LogisticsResource<C> adapter;
    private final @Nullable EndpointConfig sourceCfg;
    private final boolean isPullMode;
    private final @Nullable TransferContext transferContext;
    private C firstSource;
    private ResourceExtractionSession firstSession;
    private Map<C, ResourceExtractionSession> otherSessions;
    private Map<Object, ResourceExtractionSession> candidateSessions;
    private long targetLimit = Long.MAX_VALUE;

    public ResourceAdapterProtocol(LogisticsResource<C> adapter,
                                   @Nullable EndpointConfig sourceCfg,
                                   boolean isPullMode,
                                   @Nullable TransferContext transferContext) {
        this.adapter = adapter;
        this.sourceCfg = sourceCfg;
        this.isPullMode = isPullMode;
        this.transferContext = transferContext;
    }

    @Override
    public ExtractionResult<Object> simulateExtract(C source, long max) {
        ResourceExtractionSession session = extractionSession(source);
        @SuppressWarnings("unchecked")
        ExtractionResult<Object> result = (ExtractionResult<Object>) session.simulate(max);
        if (isPullMode && result.context() != null) {
            if (candidateSessions == null) candidateSessions = new IdentityHashMap<>();
            candidateSessions.put(result.context(), session);
        }
        return result;
    }

    @Override
    public long executeInsert(C dest, Object value) {
        return adapter.insertTyped(dest, value, false, sourceCfg, isPullMode, transferContext);
    }

    @Override
    public long simulateInsert(C dest, Object value) {
        return Math.min(targetLimit, adapter.insertTyped(dest, value, true, sourceCfg, isPullMode, transferContext));
    }

    @Override
    public ExtractionResult<Object> executeExtract(C source, ExtractionResult<Object> simulated, long requested) {
        ResourceExtractionSession session = extractionSession(source);
        if (candidateSessions != null && simulated.context() != null) candidateSessions.remove(simulated.context());
        @SuppressWarnings("unchecked")
        ExtractionResult<Object> actual = (ExtractionResult<Object>) session.execute(simulated, requested);
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
        return adapter.rollback(source, extracted.value(), remainder, sourceCfg, isPullMode, transferContext);
    }

    @Override
    public boolean isEmpty(ExtractionResult<Object> result) {
        return adapter.isEmptyResult(result.value());
    }

    @Override
    public void onActivationCompleted() {
        if (firstSession != null) firstSession.onCompleted();
        if (otherSessions != null) otherSessions.values().forEach(ResourceExtractionSession::onCompleted);
    }

    @Override
    public Object candidateKey(ExtractionResult<Object> simulated) {
        ResourceExtractionSession session = isPullMode
            ? candidateSessions == null ? null : candidateSessions.get(simulated.context()) : firstSession;
        return session == null ? null : session.candidateKey(simulated);
    }

    @Override
    public boolean advanceRejectedCandidate(ExtractionResult<Object> simulated) {
        if (simulated.context() == null) return false;
        ResourceExtractionSession session = isPullMode
            ? candidateSessions == null ? null : candidateSessions.remove(simulated.context()) : firstSession;
        return session != null && session.advanceRejected(simulated);
    }

    @Override
    public boolean canInsert(C dest, Object value, LogisticsNode targetNode) {
        targetLimit = Long.MAX_VALUE;
        if (transferContext == null) return true;
        /// 拉模式下不过滤目标端（源端过滤已由提取会话处理）
        if (isPullMode) return true;
        /// 查找目标面配置，检查输入过滤器
        ServerLevel targetLevel = transferContext.level().getServer().getLevel(
            targetNode.gPos().dimension());
        if (targetLevel == null) return false;
        EndpointConfig targetCfg = LinkManager.get(targetLevel).getEndpointConfig(EndpointAddress.of(targetNode));
        if (targetCfg == null) return false;
        targetLimit = Math.max(0L, adapter.evaluateInsertToTarget(dest, value, targetCfg));
        return targetLimit > 0L;
    }

    private ResourceExtractionSession extractionSession(C source) {
        if (firstSession == null) {
            firstSource = source;
            firstSession = adapter.openExtractionSession(source, sourceCfg, isPullMode, transferContext);
        }
        if (source == firstSource) return firstSession;
        /// 推送只有一个源；仅拉取多个源时才创建映射。
        if (otherSessions == null) otherSessions = new IdentityHashMap<>();
        return otherSessions.computeIfAbsent(source,
            handle -> adapter.openExtractionSession(handle, sourceCfg, isPullMode, transferContext));
    }

}
