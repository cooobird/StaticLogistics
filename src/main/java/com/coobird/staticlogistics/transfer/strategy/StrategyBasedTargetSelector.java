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
        }

        int outputChannel = sourceConfig.linkConfig.getOutputChannel();
        if (outputChannel != 0) {
            targetSet.addAll(globalManager.getReceiversForChannel(type, outputChannel));
        }

        if (targetSet.isEmpty()) return Collections.emptyList();

        List<LogisticsNode> allTargets = new ArrayList<>(targetSet);
        int configVersion = sourceConfig.getVersion();
        List<LogisticsNode> cached = sourceConfig.getCachedTargets(configVersion);

        DistributionStrategy strategy = sourceConfig.linkConfig.getStrategy();
        if (cached != null && strategy != DistributionStrategy.RANDOM) {
            return cached; // getCachedTargets 已返回防御性拷贝，无需再次拷贝
        }

        BlockPos sourcePos = sourceNode.gPos().pos();

        List<LogisticsNode> sorted;
        switch (strategy) {
            case SEQUENTIAL -> {
                Map<LogisticsNode, Integer> priorityCache = new HashMap<>();
                for (LogisticsNode node : allTargets) {
                    priorityCache.put(node, getPriority(globalManager, node, type));
                }
                sorted = allTargets.stream()
                    .sorted(Comparator.comparingInt((LogisticsNode node) -> priorityCache.getOrDefault(node, 0))
                        .reversed()
                        .thenComparingDouble(node -> node.gPos().pos().distSqr(sourcePos)))
                    .toList();
            }
            case NEAREST -> sorted = allTargets.stream()
                .sorted(Comparator.comparingDouble(node -> node.gPos().pos().distSqr(sourcePos)))
                .toList();
            case FURTHEST -> sorted = allTargets.stream()
                .sorted(Comparator.comparingDouble((LogisticsNode node) -> node.gPos().pos().distSqr(sourcePos)).reversed())
                .toList();
            case RANDOM -> {
                Collections.shuffle(allTargets);
                sorted = allTargets;
            }
            case ROUND_ROBIN, SLOT_ROUND_ROBIN -> {
                if (allTargets.size() <= 1) {
                    sorted = allTargets;
                } else {
                    int index = globalManager.getNextRoundRobinIndex(sourceNode.toKey(), allTargets.size());
                    List<LogisticsNode> result = new ArrayList<>();
                    for (int i = 0; i < allTargets.size(); i++) {
                        result.add(allTargets.get((index + i) % allTargets.size()));
                    }
                    sorted = result;
                }
            }
            default -> sorted = allTargets;
        }

        if (strategy != DistributionStrategy.RANDOM) {
            sourceConfig.setCachedTargets(sorted, configVersion);
        }

        return sorted;
    }

    private int getPriority(GlobalLogisticsManager globalManager, LogisticsNode node, TransferType type) {
        ServerLevel targetLevel = globalManager.getLevel(node.gPos().dimension());
        if (targetLevel != null) {
            FaceConfigComposite cfg = LinkManager.get(targetLevel).getFaceConfig(node.toKey());
            if (cfg != null) {
                return cfg.linkConfig.getPriority();
            }
        }
        return 0;
    }
}