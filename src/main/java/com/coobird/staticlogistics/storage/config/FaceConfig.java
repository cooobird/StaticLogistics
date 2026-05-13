package com.coobird.staticlogistics.storage.config;

import net.minecraft.core.BlockPos;

import java.util.UUID;
import java.util.function.Consumer;

public class FaceConfig {
    private String groupId = "";
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

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId != null ? groupId : "";
        markDirty();
    }

    public boolean hasGroup() {
        return !groupId.isEmpty();
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

    public void setOwner(UUID owner) {
        this.owner = owner;
        markDirty();
    }

    public void markDirty() {
        if (onDirty != null) onDirty.accept(this);
    }

    public void setOnDirty(Consumer<FaceConfig> onDirty) {
        this.onDirty = onDirty;
    }

    public boolean isDefault() {
        return groupId.isEmpty();
    }
}