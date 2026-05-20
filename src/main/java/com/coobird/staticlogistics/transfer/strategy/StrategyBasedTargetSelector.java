package com.coobird.staticlogistics.transfer.strategy;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.context.TransferContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

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
            boolean channelMatch = (srcOut != 0 && dstIn != 0 && srcOut == dstIn);
            if (!channelMatch) continue;

            targetSet.add(target);
            targetConfigCache.put(target, targetCfg);
        }

        int outputChannel = sourceConfig.linkConfig.getOutputChannel();
        if (outputChannel != 0) {
            targetSet.addAll(globalManager.getReceiversForChannel(type, outputChannel));
        }

        if (targetSet.isEmpty()) return Collections.emptyList();

        List<LogisticsNode> allTargets = new ArrayList<>(targetSet);

        // priority 凌驾于分发策略之上：始终按 priority 降序排列
        // 复用前面的 targetConfigCache 避免重复 FaceConfig 查找
        Map<LogisticsNode, Integer> priorityCache = new HashMap<>();
        for (LogisticsNode node : allTargets) {
            FaceConfigComposite cfg = targetConfigCache.get(node);
            if (cfg == null) {
                ServerLevel tl = globalManager.getLevel(node.gPos().dimension());
                if (tl != null) cfg = LinkManager.get(tl).getFaceConfig(node.toKey());
            }
            priorityCache.put(node, cfg != null ? cfg.linkConfig.getPriority() : 0);
        }
        allTargets.sort(Comparator.comparingInt((LogisticsNode n) -> priorityCache.getOrDefault(n, 0)).reversed());

        int configVersion = sourceConfig.getVersion();
        List<LogisticsNode> cached = sourceConfig.getCachedTargets(configVersion);

        DistributionStrategy strategy = sourceConfig.linkConfig.getStrategy();
        if (cached != null && strategy != DistributionStrategy.RANDOM) {
            return cached;
        }

        BlockPos sourcePos = sourceNode.gPos().pos();

        // 按 priority 分组，每组内应用分发策略
        List<LogisticsNode> sorted = new ArrayList<>();
        List<LogisticsNode> currentGroup = new ArrayList<>();
        int currentPriority = Integer.MAX_VALUE;
        for (LogisticsNode node : allTargets) {
            int p = priorityCache.getOrDefault(node, 0);
            if (p != currentPriority) {
                if (!currentGroup.isEmpty()) {
                    sorted.addAll(applyStrategy(currentGroup, strategy, sourcePos, sourceNode, globalManager));
                    currentGroup.clear();
                }
                currentPriority = p;
            }
            currentGroup.add(node);
        }
        if (!currentGroup.isEmpty()) {
            sorted.addAll(applyStrategy(currentGroup, strategy, sourcePos, sourceNode, globalManager));
        }

        if (strategy != DistributionStrategy.RANDOM) {
            sourceConfig.setCachedTargets(sorted, configVersion);
        }

        return sorted;
    }

    private List<LogisticsNode> applyStrategy(List<LogisticsNode> group, DistributionStrategy strategy,
                                              BlockPos sourcePos, LogisticsNode sourceNode,
                                              GlobalLogisticsManager globalManager) {
        if (group.size() <= 1) return new ArrayList<>(group);
        return switch (strategy) {
            case SEQUENTIAL -> group.stream()
                .sorted(Comparator.comparingDouble(n -> n.gPos().pos().distSqr(sourcePos)))
                .toList();
            case NEAREST -> group.stream()
                .sorted(Comparator.comparingDouble(n -> n.gPos().pos().distSqr(sourcePos)))
                .toList();
            case FURTHEST -> group.stream()
                .sorted(Comparator.<LogisticsNode>comparingDouble(n -> n.gPos().pos().distSqr(sourcePos)).reversed())
                .toList();
            case RANDOM -> {
                List<LogisticsNode> shuffled = new ArrayList<>(group);
                Collections.shuffle(shuffled);
                yield shuffled;
            }
            case ROUND_ROBIN -> {
                int index = globalManager.getNextRoundRobinIndex(sourceNode.toKey(), group.size());
                List<LogisticsNode> result = new ArrayList<>();
                for (int i = 0; i < group.size(); i++) {
                    result.add(group.get((index + i) % group.size()));
                }
                yield result;
            }
            default -> new ArrayList<>(group);
        };
    }
}