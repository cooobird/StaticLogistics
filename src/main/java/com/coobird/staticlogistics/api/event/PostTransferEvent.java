package com.coobird.staticlogistics.api.event;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.LogisticsResource;
import net.minecraftforge.eventbus.api.Event;

/**
 * 传输完成后触发的事件。
 */
public class PostTransferEvent extends Event {
    private final LogisticsNode sourceNode;
    private final LogisticsNode targetNode;
    private final LogisticsResource<?> resource;
    private final long transferredAmount;
    private final boolean success;

    public PostTransferEvent(LogisticsNode sourceNode, LogisticsNode targetNode,
                             LogisticsResource<?> resource, long transferredAmount, boolean success) {
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

    public long getTransferredAmount() {
        return transferredAmount;
    }

    public boolean isSuccess() {
        return success;
    }
}
