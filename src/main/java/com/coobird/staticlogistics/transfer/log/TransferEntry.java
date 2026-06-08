package com.coobird.staticlogistics.transfer.log;

/**
 * 传输日志条目。
 */
public record TransferEntry(
    long timestamp,
    String sourceDim, int sx, int sy, int sz, String sourceFace,
    String targetDim, int tx, int ty, int tz, String targetFace,
    String typeName, int typeColor,
    int amount, boolean success, String failureReason
) {
}
