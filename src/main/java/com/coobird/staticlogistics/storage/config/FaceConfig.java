package com.coobird.staticlogistics.storage.config;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 面配置类，用于管理物流系统中某个面的配置信息
 * 包括所属组、所有者信息、位置等
 */
public class FaceConfig {
    private final Set<String> groupIds = new LinkedHashSet<>();
    private UUID owner = null;
    private String ownerName = "Unknown";
    private CompoundTag ownerProfileTag = new CompoundTag();
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
        return groupIds.isEmpty() ? "" : groupIds.iterator().next();
    }

    public Set<String> getGroupIds() {
        return new LinkedHashSet<>(groupIds);
    }

    public void setGroupId(String groupId) {
        groupIds.clear();
        if (groupId != null && !groupId.isEmpty()) groupIds.add(groupId);
        markDirty();
    }

    public void addGroupId(String gid) {
        if (gid != null && !gid.isEmpty() && groupIds.add(gid)) markDirty();
    }

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
        setOwner(owner, ownerName, null);
    }

    public void setOwner(UUID owner, String ownerName, @Nullable GameProfile profile) {
        this.owner = owner;
        this.ownerName = ownerName != null ? ownerName : "Unknown";
        this.ownerProfileTag = new CompoundTag();
        if (profile != null) {
            ownerProfileTag.putUUID("Id", profile.getId());
            ownerProfileTag.putString("Name", profile.getName());
            CompoundTag props = new CompoundTag();
            profile.getProperties().forEach((key, prop) -> {
                CompoundTag pt = new CompoundTag();
                pt.putString("Value", prop.value());
                if (prop.signature() != null) pt.putString("Signature", prop.signature());
                props.put(key, pt);
            });
            if (!props.isEmpty()) ownerProfileTag.put("Properties", props);
        }
        markDirty();
    }

    public UUID getOwner() {
        return owner;
    }

    @Nullable
    public GameProfile getOwnerProfile() {
        if (ownerProfileTag.isEmpty()) return null;
        UUID id = ownerProfileTag.hasUUID("Id") ? ownerProfileTag.getUUID("Id") : owner;
        String name = ownerProfileTag.getString("Name");
        GameProfile profile = new GameProfile(id, name.isEmpty() ? ownerName : name);
        CompoundTag props = ownerProfileTag.getCompound("Properties");
        props.getAllKeys().forEach(key -> {
            CompoundTag pt = props.getCompound(key);
            profile.getProperties().put(key,
                new com.mojang.authlib.properties.Property(key, pt.getString("Value"), pt.contains("Signature") ? pt.getString("Signature") : null));
        });
        return profile;
    }

    public CompoundTag getOwnerProfileTag() {
        return ownerProfileTag;
    }

    public void setOwnerProfileTag(CompoundTag tag) {
        this.ownerProfileTag = tag != null ? tag : new CompoundTag();
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