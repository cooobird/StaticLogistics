package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.api.ITransferContext;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.LogisticsResource;
import com.coobird.staticlogistics.logic.GlobalLogisticsManager;
import com.coobird.staticlogistics.storage.link.LinkManager;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import com.coobird.staticlogistics.util.LogisticsConstants;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 传输上下文 —— 携载一次传输所需的全部参数，通过对象池复用避免频繁 GC。
 *
 * <p>生命周期：
 * <ol>
 *   <li>{@link #obtain} 从池中取出或新建，填充参数</li>
 *   <li>传输管线中读取参数（level、sourceNode、type、limit 等）</li>
 *   <li>{@link #recycle} 清空引用并归还池中</li>
 * </ol>
 *
 * <p>深度控制：通过 {@link #withIncrementedDepth()} 创建递增深度的副本，
 * 防止 A→B→A 的双向传输形成无限循环。{@link #MAX_DEPTH} = 3。
 *
 * <p>线程安全：每个 tick 的传输在服务器主线程上串行执行，
 * 对象池使用 ArrayDeque（主线程单线程访问）。
 */
public final class TransferContext implements ITransferContext {
    public static final int MAX_DEPTH = 3;

    private static final Deque<TransferContext> POOL = new ArrayDeque<>();

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
