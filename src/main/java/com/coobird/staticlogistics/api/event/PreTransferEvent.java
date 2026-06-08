package com.coobird.staticlogistics.api.event;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.LogisticsResource;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

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
    private final LogisticsNode sourceNode;
    private final LogisticsNode targetNode;
    private final LogisticsResource<?> resource;
    private final int requestedAmount;

    public PreTransferEvent(LogisticsNode sourceNode, LogisticsNode targetNode,
                            LogisticsResource<?> resource, int requestedAmount) {
        this.sourceNode = sourceNode;
        this.targetNode = targetNode;
        this.resource = resource;
        this.requestedAmount = requestedAmount;
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

    public int getRequestedAmount() {
        return requestedAmount;
    }
}
