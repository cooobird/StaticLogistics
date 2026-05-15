package com.coobird.staticlogistics.transfer.strategy;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.storage.config.LinkConfig;
import com.coobird.staticlogistics.transfer.context.TransferContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

public class StrategyBasedTargetSelector implements TargetSelector {

    @Override
    public List<LogisticsNode> selectTargets(TransferContext context, LinkConfig.SideData settings) {
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

            LinkConfig.SideData targetData = targetCfg.linkConfig.getSettings(type);

            boolean bothUnset = (settings.outputChannel == 0 && targetData.inputChannel == 0);
            boolean bothSetAndEqual = (settings.outputChannel != 0 && targetData.inputChannel != 0 && targetData.inputChannel == settings.outputChannel);
            if (!(bothUnset || bothSetAndEqual)) continue;

            targetSet.add(target);
        }

        if (settings.outputChannel != 0) {
            targetSet.addAll(globalManager.getReceiversForChannel(type, settings.outputChannel));
        }

        if (targetSet.isEmpty()) return Collections.emptyList();

        List<LogisticsNode> allTargets = new ArrayList<>(targetSet);
        BlockPos sourcePos = sourceNode.gPos().pos();

        Map<LogisticsNode, Integer> priorityCache = new HashMap<>();
        if (settings.strategy == DistributionStrategy.SEQUENTIAL) {
            for (LogisticsNode node : allTargets) {
                priorityCache.put(node, getPriority(globalManager, node, type));
            }
        }

        return switch (settings.strategy) {
            case SEQUENTIAL -> allTargets.stream()
                .sorted(Comparator.comparingInt((LogisticsNode node) -> priorityCache.getOrDefault(node, 0))
                    .reversed()
                    .thenComparingDouble(node -> node.gPos().pos().distSqr(sourcePos)))
                .toList();

            case NEAREST -> allTargets.stream()
                .sorted(Comparator.comparingDouble(node -> node.gPos().pos().distSqr(sourcePos)))
                .toList();

            case FURTHEST -> allTargets.stream()
                .sorted(Comparator.comparingDouble((LogisticsNode node) -> node.gPos().pos().distSqr(sourcePos))
                    .reversed())
                .toList();

            case RANDOM -> {
                Collections.shuffle(allTargets);
                yield allTargets;
            }

            case ROUND_ROBIN, SLOT_ROUND_ROBIN -> {
                if (allTargets.size() <= 1) yield allTargets;
                int index = globalManager.getNextRoundRobinIndex(sourceNode.toKey(), allTargets.size());
                List<LogisticsNode> result = new ArrayList<>();
                for (int i = 0; i < allTargets.size(); i++) {
                    result.add(allTargets.get((index + i) % allTargets.size()));
                }
                yield result;
            }
        };
    }

    private int getPriority(GlobalLogisticsManager globalManager, LogisticsNode node, TransferType type) {
        ServerLevel targetLevel = globalManager.getLevel(node.gPos().dimension());
        if (targetLevel != null) {
            FaceConfigComposite cfg = LinkManager.get(targetLevel).getFaceConfig(node.toKey());
            if (cfg != null) {
                return cfg.linkConfig.getSettings(type).priority;
            }
        }
        return 0;
    }
}