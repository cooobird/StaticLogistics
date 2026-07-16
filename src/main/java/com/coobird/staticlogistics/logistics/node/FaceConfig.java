package com.coobird.staticlogistics.logistics.node;

import com.mojang.authlib.GameProfile;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.logistics.group.OwnershipMutationPermit;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 面配置类，用于管理物流系统中某个面的配置信息
 * 包括所属组、所有者信息、位置等
 */
public class FaceConfig {
    private static final int MAX_OWNER_NAME_LENGTH = 64;
    private static final int MAX_PROFILE_PROPERTIES = 16;
    private static final int MAX_PROFILE_PROPERTY_NAME_LENGTH = 64;
    private static final int MAX_PROFILE_PROPERTY_VALUE_LENGTH = 16_384;
    private static final int MAX_PROFILE_PROPERTY_SIGNATURE_LENGTH = 4_096;
    private final Map<UUID, String> groups = new LinkedHashMap<>();
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

    void setPos(BlockPos pos) {
        this.pos = pos;
    }

    public String getGroupId() {
        return groups.isEmpty() ? "" : groups.values().iterator().next();
    }

    public Set<String> getGroupIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(groups.values()));
    }

    public Set<GroupKey> getGroupKeys() {
        UUID effectiveOwner = owner == null ? GroupKey.LEGACY_UNOWNED : owner;
        Set<GroupKey> keys = new LinkedHashSet<>();
        for (UUID internalId : groups.keySet()) {
            keys.add(new GroupKey(effectiveOwner, internalId));
        }
        return Collections.unmodifiableSet(keys);
    }

    public Set<GroupRef> getGroups() {
        UUID effectiveOwner = owner == null ? GroupKey.LEGACY_UNOWNED : owner;
        Set<GroupRef> refs = new LinkedHashSet<>();
        groups.forEach((internalId, displayName) ->
            refs.add(new GroupRef(new GroupKey(effectiveOwner, internalId), displayName)));
        return Collections.unmodifiableSet(refs);
    }

    void addGroupId(Object permit, String gid) {
        requireMutationPermit(permit);
        gid = GroupConstraints.normalizeName(gid);
        GroupKey key = GroupKey.migrated(owner, gid);
        addGroupEntry(key.internalId(), gid);
    }

    void addGroup(Object permit, GroupRef group) {
        requireMutationPermit(permit);
        if (group == null) return;
        UUID effectiveOwner = owner == null ? GroupKey.LEGACY_UNOWNED : owner;
        if (!effectiveOwner.equals(group.key().ownerId())) {
            throw new IllegalArgumentException("Group owner does not match face owner");
        }
        String displayName = GroupConstraints.normalizeName(group.displayName());
        addGroupEntry(group.key().internalId(), displayName);
    }

    void removeGroup(LinkMutationPermit permit, GroupKey key) {
        requireMutationPermit(permit);
        if (key != null && ownsGroupKey(key) && groups.remove(key.internalId()) != null) markDirty();
    }

    void renameGroup(LinkMutationPermit permit, GroupKey key, String displayName) {
        requireMutationPermit(permit);
        if (key == null) return;
        displayName = GroupConstraints.normalizeName(displayName);
        if (ownsGroupKey(key) && groups.containsKey(key.internalId())
            && !displayName.equals(groups.get(key.internalId()))) {
            groups.put(key.internalId(), displayName);
            markDirty();
        }
    }

    public boolean hasGroup() {
        return !groups.isEmpty();
    }

    private void addGroupEntry(UUID internalId, String displayName) {
        if (!groups.containsKey(internalId)
            && groups.size() >= GroupConstraints.MAX_GROUPS_PER_OWNER) {
            throw new IllegalStateException("Face group limit exceeded");
        }
        if (groups.putIfAbsent(internalId, displayName) == null) markDirty();
    }

    public boolean isDefault() {
        return groups.isEmpty() && owner == null;
    }

    void setOwner(Object permit, UUID owner, String ownerName) {
        setOwner(permit, owner, ownerName, null);
    }

    void setOwner(Object permit, UUID owner, String ownerName, @Nullable GameProfile profile) {
        requireMutationPermit(permit);
        UUID previousOwner = this.owner;
        if (previousOwner != null && !previousOwner.equals(owner)) {
            throw new IllegalStateException("Use FaceConfigComposite.transferOwnership to change owner");
        }
        applyOwner(owner, ownerName, profile);
    }

    void transferOwnership(UUID owner, String ownerName, @Nullable GameProfile profile) {
        UUID previousOwner = this.owner;
        if (previousOwner == null || owner == null) {
            throw new IllegalStateException("Ownership transfer requires existing and new owners");
        }
        applyOwner(owner, ownerName, profile);
    }

    void resetForOwnershipRestore(OwnershipMutationPermit permit) {
        if (permit == null) throw new IllegalArgumentException("Ownership mutation permit is required");
        owner = null;
        ownerName = "Unknown";
        ownerProfileTag = new CompoundTag();
        groups.clear();
    }

    /** 以快照中的完整元数据替换当前状态，同时保留运行时位置。 */
    void restoreSnapshot(Object permit, FaceConfig snapshot) {
        requireMutationPermit(permit);
        if (snapshot == null) throw new IllegalArgumentException("Face snapshot must not be null");
        groups.clear();
        groups.putAll(snapshot.groups);
        owner = snapshot.owner;
        ownerName = snapshot.ownerName;
        ownerProfileTag = snapshot.ownerProfileTag.copy();
        markDirty();
    }

    private void applyOwner(UUID owner, String ownerName, @Nullable GameProfile profile) {
        this.owner = owner;
        this.ownerName = normalizeOwnerName(ownerName);
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
            ownerProfileTag = sanitizeOwnerProfileTag(ownerProfileTag);
        }
        markDirty();
    }

    private boolean ownsGroupKey(GroupKey key) {
        UUID effectiveOwner = owner == null ? GroupKey.LEGACY_UNOWNED : owner;
        return effectiveOwner.equals(key.ownerId());
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
        return ownerProfileTag.copy();
    }

    void setOwnerProfileTag(Object permit, CompoundTag tag) {
        requireMutationPermit(permit);
        this.ownerProfileTag = sanitizeOwnerProfileTag(tag);
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

    private static void requireMutationPermit(Object permit) {
        if (!(permit instanceof LinkMutationPermit)
            && !(permit instanceof OwnershipMutationPermit)) {
            throw new IllegalArgumentException("Face metadata mutation permit is required");
        }
    }

    private static String normalizeOwnerName(@Nullable String value) {
        String normalized = value == null || value.isBlank() ? "Unknown" : value.trim();
        if (normalized.length() > MAX_OWNER_NAME_LENGTH
            || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid owner name");
        }
        return normalized;
    }

    /** 持久化资料只保留权限显示需要的有界字段，拒绝异常 NBT 扩大内存与同步开销。 */
    private static CompoundTag sanitizeOwnerProfileTag(@Nullable CompoundTag source) {
        if (source == null || source.isEmpty()) return new CompoundTag();
        CompoundTag result = new CompoundTag();
        if (source.hasUUID("Id")) result.putUUID("Id", source.getUUID("Id"));
        if (source.contains("Name", Tag.TAG_STRING)) {
            result.putString("Name", normalizeOwnerName(source.getString("Name")));
        }
        if (!source.contains("Properties")) return result;
        if (!source.contains("Properties", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Owner profile properties must be a compound tag");
        }
        CompoundTag sourceProperties = source.getCompound("Properties");
        if (sourceProperties.getAllKeys().size() > MAX_PROFILE_PROPERTIES) {
            throw new IllegalArgumentException("Owner profile property count exceeds maximum");
        }
        CompoundTag properties = new CompoundTag();
        for (String key : sourceProperties.getAllKeys()) {
            if (key.isEmpty() || key.length() > MAX_PROFILE_PROPERTY_NAME_LENGTH
                || key.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("Invalid owner profile property name");
            }
            if (!sourceProperties.contains(key, Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Owner profile property must be a compound tag");
            }
            CompoundTag sourceProperty = sourceProperties.getCompound(key);
            String value = sourceProperty.getString("Value");
            if (value.isEmpty() || value.length() > MAX_PROFILE_PROPERTY_VALUE_LENGTH) {
                throw new IllegalArgumentException("Invalid owner profile property value");
            }
            CompoundTag property = new CompoundTag();
            property.putString("Value", value);
            if (sourceProperty.contains("Signature", Tag.TAG_STRING)) {
                String signature = sourceProperty.getString("Signature");
                if (signature.length() > MAX_PROFILE_PROPERTY_SIGNATURE_LENGTH) {
                    throw new IllegalArgumentException("Owner profile property signature exceeds maximum");
                }
                property.putString("Signature", signature);
            }
            properties.put(key, property);
        }
        if (!properties.isEmpty()) result.put("Properties", properties);
        return result;
    }
}
