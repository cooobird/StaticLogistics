package com.coobird.staticlogistics.api.event;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.LogisticsResource;
import net.neoforged.bus.api.Event;

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
    private final LogisticsNode sourceNode;
    private final LogisticsNode targetNode;
    private final LogisticsResource<?> resource;
    private final int transferredAmount;
    private final boolean success;

    public PostTransferEvent(LogisticsNode sourceNode, LogisticsNode targetNode,
                             LogisticsResource<?> resource, int transferredAmount, boolean success) {
        this.sourceNode = sourceNode;
        this.targetNode = targetNode;
        this.resource = resource;
        this.transferredAmount = transferredAmount;
        this.success = success;
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

    public int getTransferredAmount() {
        return transferredAmount;
    }

    public boolean isSuccess() {
        return success;
    }
}
