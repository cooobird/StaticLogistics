package com.coobird.staticlogistics.api.event;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.LogisticsResource;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 传输开始前触发的事件。可取消以阻止传输。
 */
public class PreTransferEvent extends Event {
    private static final Deque<PreTransferEvent> POOL = new ArrayDeque<>(64);

    private LogisticsNode sourceNode;
    private LogisticsNode targetNode;
    private LogisticsResource<?> resource;
    private long requestedAmount;
    private boolean canceled;

    private PreTransferEvent() {
    }

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

    public void recycle() {
        this.sourceNode = null;
        this.targetNode = null;
        this.resource = null;
        this.requestedAmount = 0;
        this.canceled = false;
        this.setPhase(EventPriority.NORMAL);
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
