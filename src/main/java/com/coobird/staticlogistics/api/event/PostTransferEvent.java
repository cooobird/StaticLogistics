package com.coobird.staticlogistics.api.event;

import com.coobird.staticlogistics.api.LogisticsNode;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

/**
 * 传输提交后发布的不可变独立事件。
 */
public final class PostTransferEvent extends Event {
    private final LogisticsNode sourceNode;
    private final LogisticsNode targetNode;
    private final ResourceLocation resourceTypeId;
    private final long transferredAmount;
    private final boolean success;

    public PostTransferEvent(LogisticsNode sourceNode, LogisticsNode targetNode,
                             ResourceLocation resourceTypeId, long transferredAmount,
                             boolean success) {
        this.sourceNode = sourceNode;
        this.targetNode = targetNode;
        this.resourceTypeId = resourceTypeId;
        this.transferredAmount = transferredAmount;
        this.success = success;
    }

    public LogisticsNode getSourceNode() {
        return sourceNode;
    }

    public LogisticsNode getTargetNode() {
        return targetNode;
    }

    public ResourceLocation getResourceTypeId() {
        return resourceTypeId;
    }

    public long getTransferredAmount() {
        return transferredAmount;
    }

    public boolean isSuccess() {
        return success;
    }
}
