package com.coobird.staticlogistics.transfer.context;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.util.LogisticsConstants;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 传输上下文类，用于管理物流传输过程中的状态信息
 * 采用对象池模式复用对象实例，减少GC压力
 */
public final class TransferContext {
    // 最大递归深度限制，防止无限递归
    public static final int MAX_DEPTH = 3;

    // 对象池，使用双端队列存储可复用的TransferContext实例
    private static final Deque<TransferContext> POOL = new ArrayDeque<>();

    private ServerLevel level;                // 服务器世界实例
    private LogisticsNode sourceNode;         // 源物流节点
    private FaceConfigComposite sourceConfig; // 源面配置组合
    private TransferType type;                // 传输类型
    private int limit;                        // 传输数量限制
    private boolean isPullMode;               // 是否为拉取模式
    private long currentTick;                 // 当前游戏刻数
    private int depth;                        // 当前递归深度
    private LinkManager linkManager;          // 链接管理器

    private TransferContext() {
    }

    /**
     * 从对象池获取或创建新的传输上下文实例
     *
     * @param level        服务器世界实例
     * @param sourceNode   源物流节点
     * @param sourceConfig 源面配置组合
     * @param type         传输类型
     * @param limit        传输数量限制
     * @param isPullMode   是否为拉取模式
     * @param currentTick  当前游戏刻数
     * @param linkManager  链接管理器
     * @return 初始化后的传输上下文实例
     */
    public static TransferContext obtain(ServerLevel level, LogisticsNode sourceNode, FaceConfigComposite sourceConfig,
                                         TransferType type, int limit, boolean isPullMode, long currentTick, LinkManager linkManager) {
        TransferContext ctx = POOL.poll();
        if (ctx == null) {
            ctx = new TransferContext();
        }
        ctx.level = level;
        ctx.sourceNode = sourceNode;
        ctx.sourceConfig = sourceConfig;
        ctx.type = type;
        ctx.limit = limit;
        ctx.isPullMode = isPullMode;
        ctx.currentTick = currentTick;
        ctx.depth = 0;  // 新实例深度初始化为0
        ctx.linkManager = linkManager;
        return ctx;
    }

    /**
     * 回收当前上下文实例到对象池
     * 如果对象池已满则丢弃该实例
     */
    public void recycle() {
        if (POOL.size() < LogisticsConstants.Performance.getTransferContextPoolSize()) {
            this.level = null;
            this.sourceNode = null;
            this.sourceConfig = null;
            this.type = null;
            this.limit = 0;
            this.isPullMode = false;
            this.currentTick = 0;
            this.depth = 0;
            this.linkManager = null;
            POOL.offer(this);
        }
    }

    /**
     * 创建深度加1的新上下文实例
     * 用于递归调用时传递上下文
     *
     * @return 深度增加后的新上下文实例
     */
    public TransferContext withIncrementedDepth() {
        TransferContext newCtx = obtain(level, sourceNode, sourceConfig, type, limit, isPullMode, currentTick, linkManager);
        newCtx.depth = this.depth + 1;
        return newCtx;
    }

    /**
     * 检查当前深度是否超过最大限制
     *
     * @return 如果深度超过MAX_DEPTH则返回true
     */
    public boolean isDepthExceeded() {
        return depth >= MAX_DEPTH;
    }

    /**
     * 获取当前槽位游标
     * 用于记录上次传输的槽位位置，实现轮询调度
     *
     * @return 槽位游标数组
     */
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

    public TransferType type() {
        return type;
    }

    public int limit() {
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

    /**
     * 设置当前深度
     *
     * @param depth 要设置的深度值
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }
}