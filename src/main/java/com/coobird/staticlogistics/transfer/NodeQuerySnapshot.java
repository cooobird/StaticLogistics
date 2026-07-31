package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.type.ExtractionMode;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/**
 * 面向 GUI、Jade、命令和集成层的不可变节点查询结果。
 */
public record NodeQuerySnapshot(
    List<String> groups,
    List<GroupKey> groupKeys,
    NodeRole role,
    boolean inputEnabled,
    boolean outputEnabled,
    int priority,
    int keepStock,
    ResourceLocation strategyId,
    String strategyDescriptionId,
    ExtractionMode extractionMode,
    String extractionDescriptionId,
    UUID ownerId,
    String ownerName,
    long version,
    List<ResourceLocation> selectedTypeIds,
    List<ResourceLocation> presentTypeIds,
    List<ResourceLocation> outputTypeIds,
    List<ResourceLocation> acceptedTypeIds,
    List<LogisticsNode> linkedNodes,
    long sentAmount,
    long receivedAmount,
    double transfersPerMinute,
    long lastTransferAgeTicks
) {
    public NodeQuerySnapshot {
        groups = List.copyOf(groups);
        groupKeys = List.copyOf(groupKeys);
        selectedTypeIds = List.copyOf(selectedTypeIds);
        presentTypeIds = List.copyOf(presentTypeIds);
        outputTypeIds = List.copyOf(outputTypeIds);
        acceptedTypeIds = List.copyOf(acceptedTypeIds);
        linkedNodes = List.copyOf(linkedNodes);
        ownerName = ownerName == null ? "" : ownerName;
    }

    public NodeQuerySnapshot withStats(long sent, long received, double rate, long lastAgeTicks) {
        return new NodeQuerySnapshot(
            groups, groupKeys, role, inputEnabled, outputEnabled,
            priority, keepStock, strategyId, strategyDescriptionId, extractionMode,
            extractionDescriptionId, ownerId, ownerName, version, selectedTypeIds,
            presentTypeIds, outputTypeIds, acceptedTypeIds, linkedNodes,
            sent, received, rate, lastAgeTicks);
    }
}
