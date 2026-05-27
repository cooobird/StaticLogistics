package com.coobird.staticlogistics.storage.config;

import net.minecraft.core.BlockPos;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 面容基础配置 —— 记录分组ID集合（支持多组）、所有者信息、坐标。
 * <p>
 * 只有 groupIds 一份真相，不再有独立的 groupId 字段。
 * getGroupId() 返回第一个组ID以保持向后兼容。
 */
public class FaceConfig {
    private final Set<String> groupIds = new LinkedHashSet<>();
    private UUID owner = null;
    private String ownerName = "Unknown";
    private BlockPos pos = BlockPos.ZERO;

    private Consumer<FaceConfig> onDirty = (c) -> {
    };

    public FaceConfig() {
    }

    public FaceConfig(BlockPos pos) {
        this.pos = pos;
    }

    public BlockPos getPos() {
        return pos;
    }

    public void setPos(BlockPos pos) {
        this.pos = pos;
    }

    /**
     * 返回第一个组ID，空集合时返回 ""。
     * 向后兼容旧代码：旧逻辑只认单组，这里永远返回排第一的那个。
     */
    public String getGroupId() {
        return groupIds.isEmpty() ? "" : groupIds.iterator().next();
    }

    /**
     * 所有组ID（不可变视图）
     */
    public Set<String> getGroupIds() {
        return Collections.unmodifiableSet(groupIds);
    }

    /**
     * 设组：清空已有组然后只保留这一个。
     * 适合重命名、从多组改回单组等场景。
     */
    public void setGroupId(String groupId) {
        groupIds.clear();
        if (groupId != null && !groupId.isEmpty()) groupIds.add(groupId);
        markDirty();
    }

    /**
     * 添加一个组，已存在则忽略
     */
    public void addGroupId(String gid) {
        if (gid == null || gid.isEmpty()) return;
        if (groupIds.add(gid)) markDirty();
    }

    /**
     * 移除一个组
     */
    public void removeGroupId(String gid) {
        if (gid != null && groupIds.remove(gid)) markDirty();
    }

    public boolean hasGroup() {
        return !groupIds.isEmpty();
    }

    public boolean isDefault() {
        return groupIds.isEmpty();
    }

    public void setOwner(UUID owner, String ownerName) {
        this.owner = owner;
        this.ownerName = ownerName != null ? ownerName : "Unknown";
        markDirty();
    }

    public UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void markDirty() {
        if (onDirty != null) onDirty.accept(this);
    }

    public void setOnDirty(Consumer<FaceConfig> onDirty) {
        this.onDirty = onDirty;
    }
}