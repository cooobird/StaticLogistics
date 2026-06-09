package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.api.ITransferContext;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.LogisticsResource;
import com.coobird.staticlogistics.logic.GlobalLogisticsManager;
import com.coobird.staticlogistics.storage.link.LinkManager;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import com.coobird.staticlogistics.util.LogisticsConstants;
import net.minecraft.server.level.ServerLevel;

import java.util.concurrent.ConcurrentLinkedDeque;

public final class TransferContext implements ITransferContext {
    public static final int MAX_DEPTH = 3;

    private static final ConcurrentLinkedDeque<TransferContext> POOL = new ConcurrentLinkedDeque<>();

    private ServerLevel level;
    private LogisticsNode sourceNode;
    private FaceConfigComposite sourceConfig;
    private LogisticsResource<?> type;
    private long limit;
    private boolean isPullMode;
    private long currentTick;
    private int depth;
    private LinkManager linkManager;

    private TransferContext() {
    }

    public static TransferContext obtain(ServerLevel level, LogisticsNode sourceNode, FaceConfigComposite sourceConfig,
                                         LogisticsResource<?> type, long limit, boolean isPullMode, long currentTick,
                                         LinkManager linkManager) {
        TransferContext ctx = POOL.poll();
        if (ctx == null) ctx = new TransferContext();
        ctx.level = level;
        ctx.sourceNode = sourceNode;
        ctx.sourceConfig = sourceConfig;
        ctx.type = type;
        ctx.limit = limit;
        ctx.isPullMode = isPullMode;
        ctx.currentTick = currentTick;
        ctx.depth = 0;
        ctx.linkManager = linkManager;
        return ctx;
    }

    public void recycle() {
        this.level = null;
        this.sourceNode = null;
        this.sourceConfig = null;
        this.type = null;
        this.limit = 0;
        this.isPullMode = false;
        this.currentTick = 0;
        this.depth = 0;
        this.linkManager = null;
        if (POOL.size() < Math.min(LogisticsConstants.Performance.getTransferContextPoolSize(), 200)) {
            POOL.offer(this);
        }
    }

    public TransferContext withIncrementedDepth() {
        TransferContext newCtx = obtain(level, sourceNode, sourceConfig, type, limit, isPullMode, currentTick, linkManager);
        newCtx.depth = this.depth + 1;
        return newCtx;
    }

    public boolean isDepthExceeded() {
        return depth >= MAX_DEPTH;
    }

    public int[] getSlotCursor() {
        return GlobalLogisticsManager.get(level.getServer()).getCursor(sourceNode.toKey(), type);
    }

    public ServerLevel level() {
        return level;
    }

    public LogisticsNode sourceNode() {
        return sourceNode;
    }

    public FaceConfigComposite sourceConfig() {
        return sourceConfig;
    }

    public LogisticsResource<?> type() {
        return type;
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

    public int depth() {
        return depth;
    }

    public LinkManager linkManager() {
        return linkManager;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }
}
