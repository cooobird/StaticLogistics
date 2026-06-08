package com.coobird.staticlogistics.transfer.log;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 传输日志条目。
 */
public class TransferEntry {
    private static final Deque<TransferEntry> POOL = new ArrayDeque<>(256);

    private long timestamp;
    private String sourceDim;
    private int sx, sy, sz;
    private String sourceFace;
    private String targetDim;
    private int tx, ty, tz;
    private String targetFace;
    private String typeName;
    private int typeColor;
    private long amount;
    private boolean success;
    private String failureReason;

    private TransferEntry() {
    }

    /**
     * 从对象池获取或创建新实例。
     */
    public static TransferEntry obtain(
        long timestamp,
        String sourceDim, int sx, int sy, int sz, String sourceFace,
        String targetDim, int tx, int ty, int tz, String targetFace,
        String typeName, int typeColor,
        long amount, boolean success, String failureReason
    ) {
        TransferEntry entry = POOL.poll();
        if (entry == null) entry = new TransferEntry();
        entry.timestamp = timestamp;
        entry.sourceDim = sourceDim;
        entry.sx = sx;
        entry.sy = sy;
        entry.sz = sz;
        entry.sourceFace = sourceFace;
        entry.targetDim = targetDim;
        entry.tx = tx;
        entry.ty = ty;
        entry.tz = tz;
        entry.targetFace = targetFace;
        entry.typeName = typeName;
        entry.typeColor = typeColor;
        entry.amount = amount;
        entry.success = success;
        entry.failureReason = failureReason;
        return entry;
    }

    /**
     * 回收到对象池。
     */
    public void recycle() {
        this.sourceDim = null;
        this.sourceFace = null;
        this.targetDim = null;
        this.targetFace = null;
        this.typeName = null;
        this.failureReason = null;
        if (POOL.size() < 256) {
            POOL.offer(this);
        }
    }

    public long timestamp() {
        return timestamp;
    }

    public String sourceDim() {
        return sourceDim;
    }

    public int sx() {
        return sx;
    }

    public int sy() {
        return sy;
    }

    public int sz() {
        return sz;
    }

    public String sourceFace() {
        return sourceFace;
    }

    public String targetDim() {
        return targetDim;
    }

    public int tx() {
        return tx;
    }

    public int ty() {
        return ty;
    }

    public int tz() {
        return tz;
    }

    public String targetFace() {
        return targetFace;
    }

    public String typeName() {
        return typeName;
    }

    public int typeColor() {
        return typeColor;
    }

    public long amount() {
        return amount;
    }

    public boolean success() {
        return success;
    }

    public String failureReason() {
        return failureReason;
    }
}
