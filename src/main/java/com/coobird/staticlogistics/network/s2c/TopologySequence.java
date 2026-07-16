package com.coobird.staticlogistics.network.s2c;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 为全量快照和增量拓扑提供同一条单调时序。
 */
public final class TopologySequence {
    private static final AtomicLong NEXT = new AtomicLong();

    private TopologySequence() {
    }

    public static long next() {
        return NEXT.incrementAndGet();
    }
}

