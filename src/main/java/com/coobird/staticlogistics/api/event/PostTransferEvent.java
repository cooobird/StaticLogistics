package com.coobird.staticlogistics.api.event;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.LogisticsResource;
import net.neoforged.bus.api.Event;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 传输完成后触发的事件。
 *
 * <p>监听此事件可以实现：
 * <ul>
 *   <li>传输日志记录</li>
 *   <li>传输统计</li>
 *   <li>传输奖励（如成就系统）</li>
 *   <li>联动效果（如传输触发红石信号）</li>
 * </ul>
 *
 * <p>在服务器主线程的传输管线中触发，监听器应尽快返回。
 */
public class PostTransferEvent extends Event {
    private static final Deque<PostTransferEvent> POOL = new ArrayDeque<>(64);

    private LogisticsNode sourceNode;
    private LogisticsNode targetNode;
    private LogisticsResource<?> resource;
    private long transferredAmount;
    private boolean success;

    private PostTransferEvent() {
    }

    /**
     * 从对象池获取或创建新实例。
     */
    public static PostTransferEvent obtain(LogisticsNode sourceNode, LogisticsNode targetNode,
                                           LogisticsResource<?> resource, long transferredAmount, boolean success) {
        PostTransferEvent event = POOL.poll();
        if (event == null) event = new PostTransferEvent();
        event.sourceNode = sourceNode;
        event.targetNode = targetNode;
        event.resource = resource;
        event.transferredAmount = transferredAmount;
        event.success = success;
        return event;
    }

    /**
     * 回收到对象池。
     */
    public void recycle() {
        this.sourceNode = null;
        this.targetNode = null;
        this.resource = null;
        this.transferredAmount = 0;
        this.success = false;
        if (POOL.size() < 64) {
            POOL.offer(this);
        }
    }

    public LogisticsNode getSourceNode() {
        return sourceNode;
    }

    public LogisticsNode getTargetNode() {
        return targetNode;
    }

    public LogisticsResource<?> getResource() {
        return resource;
    }

    public long getTransferredAmount() {
        return transferredAmount;
    }

    public boolean isSuccess() {
        return success;
    }
}
