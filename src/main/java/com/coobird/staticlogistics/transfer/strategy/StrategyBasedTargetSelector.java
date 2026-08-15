package com.coobird.staticlogistics.transfer.strategy;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.GroupSorter;
import com.coobird.staticlogistics.logistics.group.GlobalLogisticsManager;
import com.coobird.staticlogistics.logistics.node.FaceAddress;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import com.coobird.staticlogistics.transfer.TransferContext;
import com.coobird.staticlogistics.transfer.TransferUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

/**
 * 基于分发策略的目标选择器。
 * <p>
 * 目标收集与有效链接过滤在这里统一做，每个 priority 组内的排序
 * 委托给 {@link GroupSorter}（每种分发策略独立实现）。
 * <p>
 * 线程安全：所有操作在服务器主线程上执行。复用字段避免每次分配。
 */
public class StrategyBasedTargetSelector {

    private final Map<LogisticsNode, FaceConfigComposite> targets = new LinkedHashMap<>();
    private final List<LogisticsNode> orderedTargets = new ArrayList<>();

    public List<LogisticsNode> selectTargets(TransferContext context) {
        ServerLevel level = context.level();
        GlobalLogisticsManager globalManager = GlobalLogisticsManager.get(level.getServer());
        LogisticsNode sourceNode = context.sourceNode();

        FaceConfigComposite sourceConfig = context.sourceConfig();
        targets.clear();
        orderedTargets.clear();

        try {
            sourceConfig.forEachLinkedNode((groupKey, target) -> {
                if (!sourceConfig.faceConfig.containsGroup(groupKey)) return;
                ServerLevel targetLevel = globalManager.getLevel(target.gPos().dimension());
                if (targetLevel == null) return;

                FaceConfigComposite targetCfg = LinkManager.get(targetLevel)
                    .getFaceConfig(FaceAddress.of(target));
                if (targetCfg != null && TransferUtils.isTransferLinkActive(
                    sourceNode, sourceConfig, target, targetCfg, groupKey)) {
                    targets.put(target, targetCfg);
                }
            });

            if (targets.isEmpty()) return Collections.emptyList();
            orderedTargets.addAll(targets.keySet());
            orderedTargets.sort(Comparator.comparingInt((LogisticsNode node) ->
                targets.get(node).linkConfig.getPriority()).reversed());

            DistributionStrategy strategy = sourceConfig.linkConfig.getStrategy();

            BlockPos sourcePos = sourceNode.gPos().pos();

            var sorter = strategy.sorter();
            List<LogisticsNode> sorted = new ArrayList<>(orderedTargets.size());
            int start = 0;
            while (start < orderedTargets.size()) {
                int priority = targets.get(orderedTargets.get(start)).linkConfig.getPriority();
                int end = start + 1;
                while (end < orderedTargets.size()
                    && targets.get(orderedTargets.get(end)).linkConfig.getPriority() == priority) end++;
                List<LogisticsNode> group = orderedTargets.subList(start, end);
                if (group.size() <= 1) {
                    sorted.addAll(group);
                } else {
                    sorted.addAll(sorter.sort(group, sourcePos, sourceNode, globalManager::getCursor));
                }
                start = end;
            }

            return sorted;
        } finally {
            targets.clear();
            orderedTargets.clear();
        }
    }
}
