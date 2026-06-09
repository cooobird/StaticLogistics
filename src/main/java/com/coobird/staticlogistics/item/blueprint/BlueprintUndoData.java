package com.coobird.staticlogistics.item.blueprint;

import com.coobird.staticlogistics.api.LogisticsNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * 蓝图撤销数据 —— 记录粘贴前的完整状态，用于撤销操作。
 */
public record BlueprintUndoData(
    List<FaceSnapshot> faces,
    List<ContainerSnapshot> containers,
    List<LinkSnapshot> links,
    List<GroupSnapshot> groups
) {
    /**
     * 单个面的快照
     */
    public record FaceSnapshot(
        BlockPos pos,
        Direction face,
        boolean existed,           // 粘贴前是否存在
        @Nullable CompoundTag nbt, // 存在时的完整 NBT（含 version、groups、linkedNodes 等）
        @Nullable Set<LogisticsNode> linkedNodes // 存在时的链接列表
    ) {
    }

    /**
     * 容器配置快照
     */
    public record ContainerSnapshot(
        BlockPos pos,
        boolean existed,
        @Nullable CompoundTag upgradesNbt
    ) {
    }

    /**
     * 链接关系快照（粘贴前不存在的链接）
     */
    public record LinkSnapshot(
        LogisticsNode src,
        LogisticsNode dst
    ) {
    }

    /**
     * 分组关系快照（粘贴前不存在的分组）
     */
    public record GroupSnapshot(
        BlockPos pos,
        Direction face,
        String groupId
    ) {
    }
}
