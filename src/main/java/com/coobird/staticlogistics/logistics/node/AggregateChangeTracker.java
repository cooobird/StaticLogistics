package com.coobird.staticlogistics.logistics.node;

import java.util.Objects;

/**
 * 聚合根的嵌套变更批次跟踪器。
 *
 * <p>最外层批次结束时只发布一次；空批次不发布；作用域重复关闭无副作用。
 */
public final class AggregateChangeTracker {
    private final Runnable publisher;
    private int depth;
    private boolean changed;

    public AggregateChangeTracker(Runnable publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    public Scope begin() {
        depth++;
        return new Scope(this);
    }

    public void markChanged() {
        if (depth > 0) changed = true;
        else publisher.run();
    }

    private void end() {
        if (depth <= 0) throw new IllegalStateException("Aggregate change scope is not active");
        depth--;
        if (depth == 0 && changed) {
            changed = false;
            publisher.run();
        }
    }

    public static final class Scope implements AutoCloseable {
        private final AggregateChangeTracker tracker;
        private boolean closed;

        private Scope(AggregateChangeTracker tracker) {
            this.tracker = tracker;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            tracker.end();
        }
    }
}
