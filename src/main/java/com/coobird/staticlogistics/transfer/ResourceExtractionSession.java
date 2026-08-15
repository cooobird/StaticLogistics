package com.coobird.staticlogistics.transfer;

/**
 * 单次节点激活期间的资源提取会话。
 *
 * <p>会话负责保存本轮扫描状态，避免管线每提交一次资源就重新扫描底层容器。
 * 会话对象只在当前传输协议实例内使用，不跨 tick 持久化。</p>
 */
public interface ResourceExtractionSession {
    ExtractionResult<?> simulate(long amount);

    ExtractionResult<?> execute(ExtractionResult<?> simulated, long requested);

    default boolean advanceRejected(ExtractionResult<?> simulated) {
        return false;
    }

    default void onCompleted() {
    }
}
