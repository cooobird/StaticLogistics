package com.coobird.staticlogistics.api.event;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.LogisticsResource;
import net.minecraftforge.eventbus.api.Event;

/**
 * 传输开始前触发的事件。可取消以阻止传输。
 */
public class PreTransferEvent extends Event {
    private final LogisticsNode sourceNode;
    private final LogisticsNode targetNode;
    private final LogisticsResource<?> resource;
    private final long requestedAmount;
    private boolean canceled;

    public PreTransferEvent(LogisticsNode sourceNode, LogisticsNode targetNode, LogisticsResource<?> resource, long requestedAmount) {
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

