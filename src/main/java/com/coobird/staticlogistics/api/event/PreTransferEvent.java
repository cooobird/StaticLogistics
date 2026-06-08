package com.coobird.staticlogistics.api.event;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.LogisticsResource;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 传输开始前触发的事件。可取消以阻止传输。
 *
 * <p>监听此事件可以实现：
 * <ul>
 *   <li>传输限制（如每秒总量限制）</li>
 *   <li>权限检查（如禁止向特定区域传输）</li>
 *   <li>反作弊（如检测异常传输量）</li>
 *   <li>条件传输（如红石信号控制）</li>
 * </ul>
 *
 * <p>在服务器主线程的传输管线中触发，监听器应尽快返回。
 */
public class PreTransferEvent extends Event implements ICancellableEvent {
    private static final Deque<PreTransferEvent> POOL = new ArrayDeque<>(64);

    private LogisticsNode sourceNode;
    private LogisticsNode targetNode;
    private LogisticsResource<?> resource;
    private long requestedAmount;
    private boolean canceled;

    private PreTransferEvent() {
    }

    /**
     * 从对象池获取或创建新实例。
     */
    public static PreTransferEvent obtain(LogisticsNode sourceNode, LogisticsNode targetNode,
                                          LogisticsResource<?> resource, long requestedAmount) {
        PreTransferEvent event = POOL.poll();
        if (event == null) event = new PreTransferEvent();
        event.sourceNode = sourceNode;
        event.targetNode = targetNode;
        event.resource = resource;
        event.requestedAmount = requestedAmount;
        event.canceled = false;
        return event;
    }

    /**
     * 回收到对象池。
     */
    public void recycle() {
        this.sourceNode = null;
        this.targetNode = null;
        this.resource = null;
        this.requestedAmount = 0;
        this.canceled = false;
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

    public long getRequestedAmount() {
        return requestedAmount;
    }

    @Override
    public boolean isCanceled() {
        return canceled;
    }

    @Override
    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }
}
