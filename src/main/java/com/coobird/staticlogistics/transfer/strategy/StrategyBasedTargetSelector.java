package com.coobird.staticlogistics.transfer.strategy;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.context.TransferContext;
import com.coobird.staticlogistics.transfer.strategy.distribute.GroupSorter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

/**
 * 基于分发策略的目标选择器。
 * <p>
 * 目标收集 + 频道过滤在这里统一做，每个 priority 组内的排序
 * 委托给 {@link GroupSorter}（每种分发策略独立实现）。
 */
public class StrategyBasedTargetSelector implements TargetSelector {

    @Override
    public List<LogisticsNode> selectTargets(TransferContext context) {
        ServerLevel level = context.level();
        GlobalLogisticsManager globalManager = GlobalLogisticsManager.get(level.getServer());
        LogisticsNode sourceNode = context.sourceNode();
        TransferType type = context.type();

        FaceConfigComposite sourceConfig = context.sourceConfig();
        Set<LogisticsNode> targetSet = new HashSet<>();
        Map<LogisticsNode, FaceConfigComposite> targetConfigCache = new HashMap<>();

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

        List<LogisticsNode> allTargets = new ArrayList<>(targetSet);

        int configVersion = sourceConfig.getVersion();
        DistributionStrategy strategy = sourceConfig.linkConfig.getStrategy();
        if (strategy != DistributionStrategy.RANDOM) {
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

        GroupSorter sorter = GroupSorter.forStrategy(strategy);
        List<LogisticsNode> sorted = new ArrayList<>(allTargets.size());
        for (List<LogisticsNode> group : priorityGroups.values()) {
            if (group.size() <= 1) {
                sorted.addAll(group);
            } else {
                sorted.addAll(sorter.sort(group, sourcePos, sourceNode, globalManager));
            }
        }

        if (strategy != DistributionStrategy.RANDOM) {
            sourceConfig.setCachedTargets(sorted, configVersion);
        }

        return sorted;
    }
}
