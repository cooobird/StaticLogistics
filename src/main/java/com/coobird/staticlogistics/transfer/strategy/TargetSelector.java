package com.coobird.staticlogistics.transfer.strategy;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.transfer.context.TransferContext;

import java.util.List;

@FunctionalInterface
public interface TargetSelector {
    /**
     * 根据上下文和配置，对目标节点进行排序/筛选后返回列表
     */
    List<LogisticsNode> selectTargets(TransferContext context);
}