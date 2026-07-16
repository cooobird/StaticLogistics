package com.coobird.staticlogistics.api.event;

import com.coobird.staticlogistics.api.LogisticsNode;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.Event;

/**
 * 传输开始前发布的独立可取消事件；发布后字段不会被框架清空或复用。
 */
public final class PreTransferEvent extends Event {
    private final LogisticsNode sourceNode;
    private final LogisticsNode targetNode;
    private final ResourceLocation resourceTypeId;
    private final long requestedAmount;
    private boolean canceled;

    public PreTransferEvent(LogisticsNode sourceNode, LogisticsNode targetNode,
                            ResourceLocation resourceTypeId, long requestedAmount) {
        this.sourceNode = sourceNode;
        this.targetNode = targetNode;
        this.resourceTypeId = resourceTypeId;
        this.requestedAmount = requestedAmount;
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

    public long getRequestedAmount() {
        return requestedAmount;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }

    @Override
    public boolean isCancelable() {
        return true;
    }
}

