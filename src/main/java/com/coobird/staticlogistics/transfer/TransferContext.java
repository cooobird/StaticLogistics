package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.api.ITransferContext;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.logistics.group.GlobalLogisticsManager;
import com.coobird.staticlogistics.logistics.node.EndpointAddress;
import com.coobird.staticlogistics.logistics.node.EndpointConfig;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import com.coobird.staticlogistics.logistics.util.LogisticsConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/// 一次传输的固定参数；只在服务器主线程使用，遍历进度单独管理。
public final class TransferContext implements ITransferContext {
    private final ServerLevel level;
    private final LogisticsNode sourceNode;
    private final EndpointConfig sourceConfig;
    private final LogisticsResource<?> type;
    private final long limit;
    private final boolean isPullMode;
    private final long currentTick;
    private final long deadlineNanos;
    private final LinkManager linkManager;

    /// 跨 tick 只保存遍历位置和候选值标识，不保留能力、提取计划或提取会话。
    public static final class Progress {
        int targetIndex;
        Object candidateKey;
        boolean yielded;

        void reset() {
            targetIndex = 0;
            candidateKey = null;
        }
    }

    private Progress progress;

    /// 实时上下文不允许进入工作线程，包括能力查询和事件回调。
    public static void requireServerThread(ServerLevel level) {
        if (!level.getServer().isSameThread())
            throw new IllegalStateException("Live logistics access requires the server thread");
    }

    private TransferContext(ServerLevel level, LogisticsNode sourceNode, EndpointConfig sourceConfig,
                            LogisticsResource<?> type, long limit, boolean isPullMode, long currentTick,
                            long deadlineNanos, LinkManager linkManager, Progress progress) {
        this.level = level;
        this.sourceNode = sourceNode;
        this.sourceConfig = sourceConfig;
        this.type = type;
        this.limit = limit;
        this.isPullMode = isPullMode;
        this.currentTick = currentTick;
        this.deadlineNanos = deadlineNanos;
        this.linkManager = linkManager;
        this.progress = progress;
    }

    public static TransferContext obtain(ServerLevel level, LogisticsNode sourceNode,
                                         EndpointConfig sourceConfig, LogisticsResource<?> type,
                                         long limit, boolean isPullMode, long currentTick,
                                         LinkManager linkManager) {
        return obtain(level, sourceNode, sourceConfig, type, limit, isPullMode,
            currentTick, System.nanoTime() + LogisticsConstants.Performance.getTickerTimeBudgetNanos(), linkManager);
    }

    public static TransferContext obtain(ServerLevel level, LogisticsNode sourceNode, EndpointConfig sourceConfig,
                                         LogisticsResource<?> type, long limit, boolean isPullMode, long currentTick,
                                         long deadlineNanos, LinkManager linkManager) {
        requireServerThread(level);
        return new TransferContext(level, sourceNode, sourceConfig, type, limit, isPullMode,
            currentTick, deadlineNanos, linkManager, new Progress());
    }

    /// 从公开只读上下文建立一次 core 执行快照，不依赖调用方的具体实现类。
    @Nullable
    public static TransferContext copyOf(ITransferContext source, LogisticsResource<?> expectedType) {
        if (source == null || expectedType == null || source.level() == null
            || source.sourceNode() == null || source.typeId() == null
            || !expectedType.typeId().equals(source.typeId()) || source.limit() <= 0L) {
            return null;
        }
        requireServerThread(source.level());
        ServerLevel sourceLevel = source.level().getServer().getLevel(
            source.sourceNode().gPos().dimension());
        if (sourceLevel == null) return null;
        LinkManager manager = LinkManager.get(sourceLevel);
        EndpointConfig config = manager.getEndpointConfig(EndpointAddress.of(source.sourceNode()));
        if (config == null) return null;
        long deadline = source instanceof TransferContext transferContext
            ? transferContext.deadlineNanos
            : System.nanoTime() + LogisticsConstants.Performance.getTickerTimeBudgetNanos();
        Progress progress = source instanceof TransferContext original ? original.progress : new Progress();
        return new TransferContext(sourceLevel, source.sourceNode(), config, expectedType,
            source.limit(), source.isPullMode(), source.currentTick(), deadline, manager, progress);
    }

    public void bindProgress(Progress progress) {
        this.progress = progress;
        progress.yielded = false;
    }

    public void yieldExecution() {
        progress.yielded = true;
    }

    public boolean isYielded() {
        return progress.yielded;
    }

    Progress progress() {
        return progress;
    }

    public int targetIndex() {
        return progress.targetIndex;
    }

    public void targetIndex(int index) {
        progress.targetIndex = index;
    }

    public int slotCursor() {
        return GlobalLogisticsManager.get(level.getServer())
            .getAndUpdateCursor(sourceNode, type.typeId(), current -> current);
    }

    public void slotCursor(int value) {
        GlobalLogisticsManager.get(level.getServer())
            .getAndUpdateCursor(sourceNode, type.typeId(), current -> value);
    }

    public ServerLevel level() {
        return level;
    }

    public LogisticsNode sourceNode() {
        return sourceNode;
    }

    public EndpointConfig sourceConfig() {
        return sourceConfig;
    }

    public LogisticsResource<?> type() {
        return type;
    }

    @Override
    public ResourceLocation typeId() {
        return type.typeId();
    }

    public long limit() {
        return limit;
    }

    public boolean isPullMode() {
        return isPullMode;
    }

    public long currentTick() {
        return currentTick;
    }

    public boolean hasTimeRemaining() {
        return deadlineNanos == Long.MAX_VALUE || System.nanoTime() - deadlineNanos < 0L;
    }

    public LinkManager linkManager() {
        return linkManager;
    }

}
