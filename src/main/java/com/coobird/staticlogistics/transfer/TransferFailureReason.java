package com.coobird.staticlogistics.transfer;

/**
 * 传输失败原因枚举，用于 TransferLogManager 记录失败详情
 */
public enum TransferFailureReason {
    NO_DIMENSION_UPGRADE("no_dim"),
    OUT_OF_RANGE("out_of_range"),
    CHUNK_UNLOADED("chunk_unloaded"),
    CAPABILITY_NULL("no_capability"),
    SOURCE_EMPTY("source_empty"),
    TARGET_FILTER_REJECTED("filter_rejected"),
    TARGET_FULL("target_full"),
    DEPTH_EXCEEDED("depth_exceeded"),
    NO_DESTINATION("no_dest"),
    NO_CONTAINER("no_container"),
    KEEP_STOCK_REACHED("keep_stock");

    private final String id;

    TransferFailureReason(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
