package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.logistics.group.OwnershipMutationPermit;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

/**
 * 单个方块面的身份元数据。
 * 分组使用稳定内部 UUID 存储，显示名称仅用于界面和旧协议兼容。
 */
public class FaceConfig {
    private static final int MAX_OWNER_NAME_LENGTH = 64;
    private static final int MAX_PROFILE_PROPERTIES = 16;
    private static final int MAX_PROFILE_PROPERTY_NAME_LENGTH = 64;
    private static final int MAX_PROFILE_PROPERTY_VALUE_LENGTH = 16_384;
    private static final int MAX_PROFILE_PROPERTY_SIGNATURE_LENGTH = 4_096;

    private final Map<UUID, String> groups = new LinkedHashMap<>();
    private UUID owner;
    private String ownerName = "Unknown";
    private CompoundTag ownerProfileTag = new CompoundTag();
    private BlockPos pos = BlockPos.ZERO;
    private Consumer<FaceConfig> onDirty = ignored -> {
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
        Set<GroupKey> result = new LinkedHashSet<>();
        for (UUID internalId : groups.keySet()) result.add(new GroupKey(effectiveOwner, internalId));
        return Collections.unmodifiableSet(result);
    }

    public Set<GroupRef> getGroups() {
        UUID effectiveOwner = owner == null ? GroupKey.LEGACY_UNOWNED : owner;
        Set<GroupRef> result = new LinkedHashSet<>();
        groups.forEach((internalId, displayName) ->
            result.add(new GroupRef(new GroupKey(effectiveOwner, internalId), displayName)));
        return Collections.unmodifiableSet(result);
    }

    void addGroup(Object permit, GroupRef group) {
        requireMutationPermit(permit);
        if (group == null) return;
        UUID effectiveOwner = owner == null ? GroupKey.LEGACY_UNOWNED : owner;
        if (!effectiveOwner.equals(group.key().ownerId())) {
            throw new IllegalArgumentException("Group owner does not match face owner");
        }
        String normalized = GroupConstraints.normalizeName(group.displayName());
        if (!groups.containsKey(group.key().internalId())
            && groups.size() >= GroupConstraints.MAX_GROUPS_PER_OWNER) {
            throw new IllegalStateException("Face group limit exceeded");
        }
        String previous = groups.putIfAbsent(group.key().internalId(), normalized);
        if (previous == null) {
            markDirty();
        } else if (!previous.equals(normalized)) {
            throw new IllegalStateException("Group identity has conflicting display names");
        }
    }

    void removeGroup(LinkMutationPermit permit, GroupKey key) {
        requireMutationPermit(permit);
        if (key != null && ownsGroup(key) && groups.remove(key.internalId()) != null) markDirty();
    }

    void renameGroup(LinkMutationPermit permit, GroupKey key, String displayName) {
        requireMutationPermit(permit);
        if (key == null || !ownsGroup(key) || !groups.containsKey(key.internalId())) return;
        String normalized = GroupConstraints.normalizeName(displayName);
        if (!normalized.equals(groups.get(key.internalId()))) {
            groups.put(key.internalId(), normalized);
            markDirty();
        }
    }

    public boolean hasGroup() {
        return !groups.isEmpty();
    }

    public boolean isDefault() {
        return groups.isEmpty() && owner == null;
    }

    void setOwner(Object permit, UUID owner, String ownerName) {
        setOwner(permit, owner, ownerName, null);
    }

    void setOwner(Object permit, UUID owner, String ownerName, @Nullable GameProfile profile) {
        requireMutationPermit(permit);
        if (this.owner != null && owner != null && !this.owner.equals(owner)) {
            throw new IllegalStateException(
                "Use FaceConfigComposite.transferOwnership to change owner");
        }
        applyOwner(owner, ownerName, profile);
    }

    void transferOwnership(UUID owner, String ownerName, @Nullable GameProfile profile) {
        if (this.owner == null || owner == null) {
            throw new IllegalStateException("Ownership transfer requires existing and new owners");
        }
        applyOwner(owner, ownerName, profile);
    }

    private void applyOwner(UUID owner, String ownerName, @Nullable GameProfile profile) {
        this.owner = owner;
        this.ownerName = normalizeOwnerName(ownerName);
        this.ownerProfileTag = profile == null ? new CompoundTag() : profileToTag(profile);
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
        CompoundTag properties = ownerProfileTag.getCompound("Properties");
        properties.getAllKeys().forEach(key -> {
            CompoundTag property = properties.getCompound(key);
            profile.getProperties().put(key, new com.mojang.authlib.properties.Property(
                key,
                property.getString("Value"),
                property.contains("Signature") ? property.getString("Signature") : null));
        });
        return profile;
    }

    public CompoundTag getOwnerProfileTag() {
        return ownerProfileTag.copy();
    }

    void setOwnerProfileTag(Object permit, CompoundTag tag) {
        requireMutationPermit(permit);
        ownerProfileTag = sanitizeOwnerProfileTag(tag);
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

    void resetForOwnershipRestore(OwnershipMutationPermit permit) {
        if (permit == null) throw new IllegalArgumentException("Ownership mutation permit is required");
        groups.clear();
        owner = null;
        ownerName = "Unknown";
        ownerProfileTag = new CompoundTag();
    }

    /**
     * 以快照完整替换持久身份，同时保留当前运行时位置。
     */
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

    private boolean ownsGroup(GroupKey key) {
        UUID effectiveOwner = owner == null ? GroupKey.LEGACY_UNOWNED : owner;
        return effectiveOwner.equals(key.ownerId());
    }

    private static void requireMutationPermit(Object permit) {
        if (!(permit instanceof LinkMutationPermit)
            && !(permit instanceof OwnershipMutationPermit)) {
            throw new IllegalArgumentException("Face metadata mutation permit is required");
        }
    }

    private static CompoundTag profileToTag(GameProfile profile) {
        CompoundTag result = new CompoundTag();
        if (profile.getId() != null) result.putUUID("Id", profile.getId());
        result.putString("Name", normalizeOwnerName(profile.getName()));
        CompoundTag properties = new CompoundTag();
        profile.getProperties().forEach((key, property) -> {
            CompoundTag value = new CompoundTag();
            value.putString("Value", property.getValue());
            if (property.getSignature() != null) value.putString("Signature", property.getSignature());
            properties.put(key, value);
        });
        if (!properties.isEmpty()) result.put("Properties", properties);
        return sanitizeOwnerProfileTag(result);
    }

    private static String normalizeOwnerName(@Nullable String value) {
        String normalized = value == null || value.isBlank() ? "Unknown" : value.trim();
        if (normalized.length() > MAX_OWNER_NAME_LENGTH
            || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid owner name");
        }
        return normalized;
    }

    /**
     * 只保留权限显示需要的有界资料，防止异常 NBT 扩大内存和同步开销。
     */
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
