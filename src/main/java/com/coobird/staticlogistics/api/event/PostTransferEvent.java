package com.coobird.staticlogistics.api.event;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.LogisticsResource;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 传输完成后触发的事件。
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

    public void recycle() {
        this.sourceNode = null;
        this.targetNode = null;
        this.resource = null;
        this.transferredAmount = 0;
        this.success = false;
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

    public long getTransferredAmount() {
        return transferredAmount;
    }

    public boolean isSuccess() {
        return success;
    }
}
