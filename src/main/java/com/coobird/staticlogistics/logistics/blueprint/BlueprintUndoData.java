package com.coobird.staticlogistics.logistics.blueprint;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.node.FaceAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 记录一次蓝图粘贴前后的状态，用于安全撤销。
 */
public record BlueprintUndoData(
    ResourceKey<Level> dimension,
    List<FaceSnapshot> faces,
    List<ContainerSnapshot> containers,
    List<LinkSnapshot> links,
    List<GroupSnapshot> groups,
    Map<FaceAddress, Long> postVersions,
    Map<Long, CompoundTag> postContainerUpgrades
) {
    public BlueprintUndoData {
        if (dimension == null || faces == null || containers == null || links == null
            || groups == null || postVersions == null || postContainerUpgrades == null) {
            throw new IllegalArgumentException("Blueprint undo fields must not be null");
        }
        validateWorkItemCount(faces.size(), containers.size(), links.size(), groups.size(),
            postVersions.size(), postContainerUpgrades.size());
        faces = List.copyOf(faces);
        containers = List.copyOf(containers);
        links = List.copyOf(links);
        groups = List.copyOf(groups);
        postVersions = Map.copyOf(postVersions);
        postContainerUpgrades = Map.copyOf(postContainerUpgrades);
    }

    static void validateWorkItemCount(
        int faces,
        int containers,
        int links,
        int groups,
        int postVersions,
        int postContainerUpgrades
    ) {
        long workItems = (long) faces + containers + links + groups
            + postVersions + postContainerUpgrades;
        if (workItems > BlueprintDataValidator.MAX_UNDO_WORK_ITEMS) {
            throw new IllegalArgumentException("Blueprint undo work item limit exceeded");
        }
    }

    /**
     * 单个面的粘贴前快照。
     */
    public record FaceSnapshot(
        BlockPos pos,
        Direction face,
        boolean existed,
        @Nullable CompoundTag nbt,
        @Nullable Set<LogisticsNode> linkedNodes
    ) {
    }

    /**
     * 容器配置的粘贴前快照。
     */
    public record ContainerSnapshot(
        BlockPos pos,
        boolean existed,
        @Nullable CompoundTag upgradesNbt
    ) {
    }

    /**
     * 粘贴过程新增的链接。
     */
    public record LinkSnapshot(
        LogisticsNode src,
        LogisticsNode dst,
        GroupKey groupKey
    ) {
    }

    /**
     * 粘贴过程新增的稳定分组关系。
     */
    public record GroupSnapshot(
        BlockPos pos,
        Direction face,
        GroupKey groupKey
    ) {
    }
}
