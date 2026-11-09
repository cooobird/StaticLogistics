package com.coobird.staticlogistics.transfer;

/// 不可变的传输日志条目。
public record TransferEntry(
    long timestamp,
    String sourceDim,
    int sx,
    int sy,
    int sz,
    String sourceFace,
    int sourceChannel,
    String targetDim,
    int tx,
    int ty,
    int tz,
    String targetFace,
    int targetChannel,
    String typeName,
    int typeColor,
    long amount,
    boolean success,
    String failureReason
) {

    /// 创建日志快照；保留工厂入口以集中构造参数顺序。
    public static TransferEntry obtain(
        long timestamp,
        String sourceDim, int sx, int sy, int sz, String sourceFace, int sourceChannel,
        String targetDim, int tx, int ty, int tz, String targetFace, int targetChannel,
        String typeName, int typeColor,
        long amount, boolean success, String failureReason
    ) {
        return new TransferEntry(
            timestamp,
            sourceDim, sx, sy, sz, sourceFace, sourceChannel,
            targetDim, tx, ty, tz, targetFace, targetChannel,
            typeName, typeColor,
            amount, success, failureReason
        );
    }
}
