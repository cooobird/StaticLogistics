package com.coobird.staticlogistics.transfer.strategy;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.LogisticsResource;
import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.logic.DistributionStrategyRegistry;
import com.coobird.staticlogistics.logic.GlobalLogisticsManager;
import com.coobird.staticlogistics.storage.link.LinkManager;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.TransferContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

/**
 * 基于分发策略的目标选择器。
 * <p>
 * 目标收集 + 频道过滤在这里统一做，每个 priority 组内的排序
 * 委托给 {@link com.coobird.staticlogistics.api.type.GroupSorter}（每种分发策略独立实现）。
 * <p>
 * 线程安全：所有操作在服务器主线程上执行。复用字段避免每次分配。
 */
public class StrategyBasedTargetSelector implements TargetSelector {

    private final Set<LogisticsNode> targetSet = new HashSet<>();
    private final Map<LogisticsNode, FaceConfigComposite> targetConfigCache = new HashMap<>();
    private final List<LogisticsNode> allTargets = new ArrayList<>();

    @Override
    public List<LogisticsNode> selectTargets(TransferContext context) {
        ServerLevel level = context.level();
        GlobalLogisticsManager globalManager = GlobalLogisticsManager.get(level.getServer());
        LogisticsNode sourceNode = context.sourceNode();
        LogisticsResource<?> type = context.type();

        FaceConfigComposite sourceConfig = context.sourceConfig();
        targetSet.clear();
        targetConfigCache.clear();
        allTargets.clear();

        try {
            for (LogisticsNode target : sourceConfig.getLinkedNodes()) {
                ServerLevel targetLevel = globalManager.getLevel(target.gPos().dimension());
                if (targetLevel == null) continue;

                FaceConfigComposite targetCfg = LinkManager.get(targetLevel).getFaceConfig(target.toKey());
                if (targetCfg == null) continue;
                if (!targetCfg.isGlobalInputEnabled()) continue;

                int srcOut = sourceConfig.linkConfig.getOutputChannel();
                int dstIn = targetCfg.linkConfig.getInputChannel();
                if (srcOut != 0 && dstIn != 0 && srcOut != dstIn) continue;

                targetSet.add(target);
                targetConfigCache.put(target, targetCfg);
            }

            int outputChannel = sourceConfig.linkConfig.getOutputChannel();
            if (outputChannel != 0) {
                targetSet.addAll(globalManager.getReceiversForChannel(type, outputChannel));
            }

            if (targetSet.isEmpty()) return Collections.emptyList();

            allTargets.addAll(targetSet);

            long configVersion = sourceConfig.getVersion();
            DistributionStrategy strategy = sourceConfig.linkConfig.getStrategy();
            if (strategy != DistributionStrategyRegistry.RANDOM) {
                List<LogisticsNode> cached = sourceConfig.getCachedTargets(configVersion);
                if (cached != null) return cached;
            }

            BlockPos sourcePos = sourceNode.gPos().pos();

            TreeMap<Integer, List<LogisticsNode>> priorityGroups = new TreeMap<>(Comparator.reverseOrder());
            for (LogisticsNode node : allTargets) {
                FaceConfigComposite cfg = targetConfigCache.get(node);
                if (cfg == null) {
                    ServerLevel tl = globalManager.getLevel(node.gPos().dimension());
                    if (tl != null) cfg = LinkManager.get(tl).getFaceConfig(node.toKey());
                }
                int p = cfg != null ? cfg.linkConfig.getPriority() : 0;
                priorityGroups.computeIfAbsent(p, k -> new ArrayList<>()).add(node);
            }

            var sorter = strategy.sorter();
            List<LogisticsNode> sorted = new ArrayList<>(allTargets.size());
            for (List<LogisticsNode> group : priorityGroups.values()) {
                if (group.size() <= 1) {
                    sorted.addAll(group);
                } else {
                    sorted.addAll(sorter.sort(group, sourcePos, sourceNode, globalManager::getCursor));
                }
            }

            if (strategy != DistributionStrategyRegistry.RANDOM) {
                sourceConfig.setCachedTargets(sorted, configVersion);
            }

            return sorted;
        } finally {
            targetSet.clear();
            targetConfigCache.clear();
            allTargets.clear();
        }
    }
}
