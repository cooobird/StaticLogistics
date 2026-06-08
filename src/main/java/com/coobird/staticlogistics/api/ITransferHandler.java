package com.coobird.staticlogistics.api;

import java.util.List;

/**
 * 传输处理器，执行一次物流传输。
 *
 * <p>这是传输管线的最底层抽象。第三方模组通常不需要直接实现此接口，
 * 推荐通过 {@link LogisticsResource} + {@code registerAdapter()}
 * 或 {@link TransferProvider} + {@code registerProvider()} 接入。
 *
 * @see LogisticsResource 推荐的集成方式
 * @see TransferProvider 简化集成方式
 */
@FunctionalInterface
public interface ITransferHandler {

    /**
     * 执行传输。调用方保证 targets 已排序和类型过滤。
     *
     * @param targets 已排序筛选的目标节点列表
     * @return 是否至少产生了一次成功传输
     */
    boolean performTransfer(ITransferContext context, List<LogisticsNode> targets);
}
