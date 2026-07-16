package com.coobird.staticlogistics.logistics.group;

import com.coobird.staticlogistics.api.group.*;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;
import org.slf4j.Logger;

/**
 * 独立的玩家分组持久化存储。
 * 继承 SavedData，修改后直接 setDirty()，由 Minecraft 自动保存。
 * 主线程单线程访问，无需 ConcurrentHashMap。
 */
public class PlayerGroupStore extends SavedData {
    private static final String DATA_NAME = "static_logistics_player_groups";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int SCHEMA_VERSION = 2;
    private static final int MAX_STORED_OWNERS = 100_000;
    private final Map<UUID, LinkedHashMap<UUID, String>> playerGroups = new HashMap<>();
    private final Map<UUID, Integer> playerNextGroupCounter = new HashMap<>();
    private boolean loadedFromDedicatedStorage;

    PlayerGroupStore() {
    }

    public static PlayerGroupStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(
                PlayerGroupStore::new,
                (tag, provider) -> {
                    PlayerGroupStore store = new PlayerGroupStore();
                    store.load(tag);
                    return store;
                }
            ),
            DATA_NAME
        );
    }

    public void addGroup(UUID playerId, String groupId) {
        createGroup(playerId, groupId);
    }

    public GroupRef createGroup(UUID playerId, String displayName) {
        if (playerId == null) throw new IllegalArgumentException("Group owner is required");
        displayName = GroupConstraints.normalizeName(displayName);
        GroupRef existing = findGroup(playerId, displayName);
        if (existing != null) return existing;
        if (playerGroups.getOrDefault(playerId, new LinkedHashMap<>()).size()
            >= GroupConstraints.MAX_GROUPS_PER_OWNER) {
            throw new IllegalStateException("Group limit exceeded");
        }
        GroupKey key = GroupKey.create(playerId);
        playerGroups.computeIfAbsent(playerId, ignored -> new LinkedHashMap<>())
            .put(key.internalId(), displayName);
        setDirty();
        return new GroupRef(key, displayName);
    }

    /** 解析已有分组；不存在时创建新的随机稳定身份。 */
    public GroupRef resolveOrCreateGroup(UUID playerId, String displayName) {
        return createGroup(playerId, displayName);
    }

    @org.jetbrains.annotations.Nullable
    public GroupRef findGroup(UUID playerId, String displayName) {
        LinkedHashMap<UUID, String> groups = playerGroups.get(playerId);
        if (groups == null) return null;
        for (var entry : groups.entrySet()) {
            if (entry.getValue().equals(displayName)) {
                return new GroupRef(new GroupKey(playerId, entry.getKey()), entry.getValue());
            }
        }
        return null;
    }

    @org.jetbrains.annotations.Nullable
    public GroupRef findGroup(GroupKey key) {
        if (key == null || key.isLegacyUnowned()) return null;
        Map<UUID, String> groups = playerGroups.get(key.ownerId());
        if (groups == null) return null;
        String displayName = groups.get(key.internalId());
        return displayName == null ? null : new GroupRef(key, displayName);
    }

    public void removeGroup(UUID playerId, String groupId) {
        if (playerId == null || groupId == null) return;
        GroupRef group = findGroup(playerId, groupId);
        if (group != null) removeGroup(group.key());
    }

    public boolean removeGroup(GroupKey key) {
        if (key == null || key.isLegacyUnowned()) return false;
        LinkedHashMap<UUID, String> groups = playerGroups.get(key.ownerId());
        if (groups == null || groups.remove(key.internalId()) == null) return false;
        if (groups.isEmpty()) playerGroups.remove(key.ownerId());
        setDirty();
        return true;
    }

    public boolean renameGroup(GroupKey key, String newDisplayName) {
        if (!canRenameGroup(key, newDisplayName)) return false;
        newDisplayName = GroupConstraints.normalizeName(newDisplayName);
        LinkedHashMap<UUID, String> groups = playerGroups.get(key.ownerId());
        if (newDisplayName.equals(groups.get(key.internalId()))) return true;
        groups.put(key.internalId(), newDisplayName);
        setDirty();
        return true;
    }

    /** 仅校验重命名，不修改分组目录。 */
    public boolean canRenameGroup(GroupKey key, String newDisplayName) {
        if (key == null || key.isLegacyUnowned()) return false;
        try {
            newDisplayName = GroupConstraints.normalizeName(newDisplayName);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        LinkedHashMap<UUID, String> groups = playerGroups.get(key.ownerId());
        if (groups == null || !groups.containsKey(key.internalId())) return false;
        GroupRef collision = findGroup(key.ownerId(), newDisplayName);
        if (collision != null && !collision.key().equals(key)) return false;
        return true;
    }

    public Set<String> getGroups(UUID playerId) {
        if (playerId == null) return Collections.emptySet();
        LinkedHashMap<UUID, String> groups = playerGroups.get(playerId);
        return groups == null ? Collections.emptySet()
            : Collections.unmodifiableSet(new LinkedHashSet<>(groups.values()));
    }

    public Set<GroupRef> getGroupRefs(UUID playerId) {
        LinkedHashMap<UUID, String> groups = playerGroups.get(playerId);
        if (groups == null) return Collections.emptySet();
        Set<GroupRef> refs = new LinkedHashSet<>();
        groups.forEach((internalId, displayName) ->
            refs.add(new GroupRef(new GroupKey(playerId, internalId), displayName)));
        return Collections.unmodifiableSet(refs);
    }

    /** 校验旧版无所有者分组能否原样归入玩家，校验阶段不修改存档。 */
    public void validateClaimedGroups(UUID owner, Collection<GroupRef> claimedGroups) {
        if (owner == null || claimedGroups == null) {
            throw new IllegalArgumentException("Claimed group owner and groups are required");
        }
        LinkedHashMap<UUID, String> existing = playerGroups.getOrDefault(owner, new LinkedHashMap<>());
        Map<UUID, String> additions = new LinkedHashMap<>();
        for (GroupRef group : claimedGroups) {
            if (group == null || !owner.equals(group.key().ownerId())) {
                throw new IllegalArgumentException("Claimed group owner does not match target owner");
            }
            String storedName = existing.get(group.key().internalId());
            if (storedName != null && !storedName.equals(group.displayName())) {
                throw new IllegalStateException("Target owner has a conflicting group identity");
            }
            boolean nameCollision = existing.entrySet().stream().anyMatch(entry ->
                !entry.getKey().equals(group.key().internalId())
                    && entry.getValue().equals(group.displayName()));
            if (nameCollision) {
                throw new IllegalStateException("Target owner already has a group with this name");
            }
            String previous = additions.putIfAbsent(group.key().internalId(), group.displayName());
            if (previous != null && !previous.equals(group.displayName())) {
                throw new IllegalStateException("Claimed groups contain a conflicting identity");
            }
        }
        long newCount = additions.keySet().stream().filter(key -> !existing.containsKey(key)).count();
        if ((long) existing.size() + newCount > GroupConstraints.MAX_GROUPS_PER_OWNER) {
            throw new IllegalStateException("Group limit exceeded");
        }
    }

    /** 在全部节点完成认领后提交分组目录。 */
    public void registerClaimedGroups(UUID owner, Collection<GroupRef> claimedGroups) {
        validateClaimedGroups(owner, claimedGroups);
        if (claimedGroups.isEmpty()) return;
        LinkedHashMap<UUID, String> groups = playerGroups.computeIfAbsent(
            owner, ignored -> new LinkedHashMap<>());
        boolean changed = false;
        for (GroupRef group : claimedGroups) {
            if (groups.putIfAbsent(group.key().internalId(), group.displayName()) == null) changed = true;
        }
        if (changed) setDirty();
    }

    /**
     * 从 1.0.4 之前嵌在链接存档中的分组数据执行一次性迁移。
     * 已存在独立分组存档时绝不导入，避免复活其中残留的历史快照。
     */
    public boolean importLegacyStorage(CompoundTag legacyTag) {
        if (loadedFromDedicatedStorage) return false;
        boolean changed = false;

        CompoundTag counterTag = legacyTag.getCompound("player_group_counter");
        for (String ownerKey : counterTag.getAllKeys()) {
            if (playerNextGroupCounter.size() >= MAX_STORED_OWNERS) break;
            try {
                UUID owner = UUID.fromString(ownerKey);
                playerNextGroupCounter.merge(
                    owner, Math.max(0, counterTag.getInt(ownerKey)), Math::max);
                changed = true;
            } catch (IllegalArgumentException exception) {
                LOGGER.warn("Skipping invalid legacy player group counter owner: {}", ownerKey);
            }
        }

        CompoundTag groupsTag = legacyTag.getCompound("player_groups");
        for (String ownerKey : groupsTag.getAllKeys()) {
            if (playerGroups.size() >= MAX_STORED_OWNERS) break;
            UUID owner;
            try {
                owner = UUID.fromString(ownerKey);
            } catch (IllegalArgumentException exception) {
                LOGGER.warn("Skipping invalid legacy player group owner: {}", ownerKey);
                continue;
            }
            CompoundTag legacyGroups = groupsTag.getCompound(ownerKey);
            LinkedHashMap<UUID, String> groups = playerGroups.computeIfAbsent(
                owner, ignored -> new LinkedHashMap<>());
            for (String entryKey : legacyGroups.getAllKeys()) {
                if (groups.size() >= GroupConstraints.MAX_GROUPS_PER_OWNER) break;
                String displayName;
                try {
                    displayName = GroupConstraints.normalizeName(legacyGroups.getString(entryKey));
                } catch (IllegalArgumentException exception) {
                    LOGGER.warn("Skipping invalid legacy player group name for owner {}", ownerKey);
                    continue;
                }
                GroupKey migrated = GroupKey.migrated(owner, displayName);
                if (groups.putIfAbsent(migrated.internalId(), displayName) == null) changed = true;
            }
        }

        if (changed) {
            loadedFromDedicatedStorage = true;
            setDirty();
        }
        return changed;
    }

    /** 将一组稳定分组身份迁移到新所有者；冲突时不修改任何状态。 */
    public void transferGroups(UUID previousOwner, UUID newOwner, Collection<GroupRef> groupsToTransfer) {
        validateGroupTransfer(previousOwner, newOwner, groupsToTransfer);
        if (previousOwner.equals(newOwner) || groupsToTransfer.isEmpty()) return;

        LinkedHashMap<UUID, String> sourceGroups = playerGroups.get(previousOwner);
        LinkedHashMap<UUID, String> destination = playerGroups.computeIfAbsent(
            newOwner, ignored -> new LinkedHashMap<>());
        for (GroupRef group : groupsToTransfer) {
            if (sourceGroups != null) sourceGroups.remove(group.key().internalId());
            destination.put(group.key().internalId(), group.displayName());
        }
        if (sourceGroups != null && sourceGroups.isEmpty()) playerGroups.remove(previousOwner);
        setDirty();
    }

    public void validateGroupTransfer(UUID previousOwner, UUID newOwner,
                                      Collection<GroupRef> groupsToTransfer) {
        if (previousOwner == null || newOwner == null || groupsToTransfer == null) {
            throw new IllegalArgumentException("Group transfer owners and groups are required");
        }
        if (previousOwner.equals(newOwner) || groupsToTransfer.isEmpty()) return;

        LinkedHashMap<UUID, String> targetGroups = playerGroups.getOrDefault(newOwner, new LinkedHashMap<>());
        long newGroupCount = groupsToTransfer.stream()
            .map(group -> group.key().internalId()).distinct()
            .filter(internalId -> !targetGroups.containsKey(internalId)).count();
        if ((long) targetGroups.size() + newGroupCount > GroupConstraints.MAX_GROUPS_PER_OWNER) {
            throw new IllegalStateException("Group limit exceeded");
        }
        for (GroupRef group : groupsToTransfer) {
            if (!previousOwner.equals(group.key().ownerId())) {
                throw new IllegalArgumentException("Group does not belong to previous owner");
            }
            String sameInternal = targetGroups.get(group.key().internalId());
            if (sameInternal != null && !sameInternal.equals(group.displayName())) {
                throw new IllegalStateException("Target owner has a conflicting group identity");
            }
            boolean sameName = targetGroups.entrySet().stream().anyMatch(entry ->
                !entry.getKey().equals(group.key().internalId())
                    && entry.getValue().equals(group.displayName()));
            if (sameName) {
                throw new IllegalStateException("Target owner already has a group with this name");
            }
        }

    }

    public synchronized String getNextGroupIdForPlayer(UUID playerId) {
        Set<Integer> used = getNumericGroupIdsForPlayer(playerId);
        if (used.isEmpty()) {
            playerNextGroupCounter.put(playerId, 1);
            return "1";
        }
        int counter = Math.max(
            playerNextGroupCounter.getOrDefault(playerId, 0),
            used.stream().max(Integer::compareTo).orElse(0));
        int next = counter + 1;
        while (used.contains(next)) {
            next++;
        }
        playerNextGroupCounter.put(playerId, next);
        return Integer.toString(next);
    }

    private Set<Integer> getNumericGroupIdsForPlayer(UUID playerId) {
        Set<Integer> ids = new HashSet<>();
        Collection<String> groups = playerGroups.containsKey(playerId)
            ? playerGroups.get(playerId).values() : Collections.emptyList();
        if (groups != null) {
            for (String gid : groups) {
                if (gid != null && gid.matches("\\d+")) {
                    try {
                        ids.add(Integer.parseInt(gid));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return ids;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        // 保存分组计数器
        CompoundTag counterTag = new CompoundTag();
        playerNextGroupCounter.forEach((uuid, counter) -> counterTag.putInt(uuid.toString(), counter));
        tag.putInt("schema_version", SCHEMA_VERSION);
        tag.put("player_group_counter", counterTag);

        // 保存玩家分组
        CompoundTag groupsTag = new CompoundTag();
        playerGroups.forEach((uuid, groups) -> {
            if (!groups.isEmpty()) {
                CompoundTag playerTag = new CompoundTag();
                groups.forEach((internalId, displayName) ->
                    playerTag.putString(internalId.toString(), displayName));
                groupsTag.put(uuid.toString(), playerTag);
            }
        });
        tag.put("player_groups", groupsTag);
        return tag;
    }

    void load(CompoundTag tag) {
        loadedFromDedicatedStorage = true;
        CompoundTag migrated = migrateStoredData(tag);
        if (migrated.contains("player_group_counter")) {
            CompoundTag counterTag = migrated.getCompound("player_group_counter");
            for (String key : counterTag.getAllKeys()) {
                if (playerNextGroupCounter.size() >= MAX_STORED_OWNERS) break;
                try {
                    playerNextGroupCounter.put(
                        UUID.fromString(key), Math.max(0, counterTag.getInt(key)));
                } catch (IllegalArgumentException exception) {
                    LOGGER.warn("Skipping invalid player group counter owner: {}", key);
                }
            }
        }

        if (migrated.contains("player_groups")) {
            CompoundTag groupsTag = migrated.getCompound("player_groups");
            for (String uuidStr : groupsTag.getAllKeys()) {
                if (playerGroups.size() >= MAX_STORED_OWNERS) break;
                UUID uuid;
                try {
                    uuid = UUID.fromString(uuidStr);
                } catch (IllegalArgumentException exception) {
                    LOGGER.warn("Skipping invalid player group owner: {}", uuidStr);
                    continue;
                }
                CompoundTag playerTag = groupsTag.getCompound(uuidStr);
                LinkedHashMap<UUID, String> groups = new LinkedHashMap<>();
                for (String storedKey : playerTag.getAllKeys()) {
                    if (groups.size() >= GroupConstraints.MAX_GROUPS_PER_OWNER) break;
                    try {
                        String displayName = GroupConstraints.normalizeName(
                            playerTag.getString(storedKey));
                        groups.put(UUID.fromString(storedKey), displayName);
                    } catch (IllegalArgumentException exception) {
                        LOGGER.warn("Skipping invalid player group entry: {}", storedKey);
                    }
                }
                if (!groups.isEmpty()) {
                    playerGroups.put(uuid, groups);
                }
            }
        }
    }

    /** 将旧显示名键目录显式迁移为第二版稳定 UUID 键目录。 */
    private static CompoundTag migrateStoredData(CompoundTag source) {
        CompoundTag migrated = source.copy();
        if (migrated.contains("schema_version")
            && !migrated.contains("schema_version", net.minecraft.nbt.Tag.TAG_INT)) {
            throw new IllegalStateException("Player group schema version must be an int");
        }
        int version = migrated.contains("schema_version")
            ? migrated.getInt("schema_version") : 1;
        if (version < 1 || version > SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported player group schema version: " + version);
        }
        while (version < SCHEMA_VERSION) {
            switch (version) {
                case 1 -> {
                    CompoundTag oldGroups = migrated.getCompound("player_groups");
                    CompoundTag newGroups = new CompoundTag();
                    for (String ownerText : oldGroups.getAllKeys()) {
                        CompoundTag oldOwnerGroups = oldGroups.getCompound(ownerText);
                        CompoundTag newOwnerGroups = new CompoundTag();
                        UUID owner;
                        try {
                            owner = UUID.fromString(ownerText);
                        } catch (IllegalArgumentException exception) {
                            newGroups.put(ownerText, oldOwnerGroups.copy());
                            continue;
                        }
                        for (String oldKey : oldOwnerGroups.getAllKeys()) {
                            if (newOwnerGroups.size() >= GroupConstraints.MAX_GROUPS_PER_OWNER) break;
                            String displayName;
                            try {
                                displayName = GroupConstraints.normalizeName(
                                    oldOwnerGroups.getString(oldKey));
                            } catch (IllegalArgumentException exception) {
                                LOGGER.warn("Skipping invalid migrated player group name for owner {}", ownerText);
                                continue;
                            }
                            GroupKey key = GroupKey.migrated(owner, displayName);
                            newOwnerGroups.putString(key.internalId().toString(), displayName);
                        }
                        if (!newOwnerGroups.isEmpty()) newGroups.put(ownerText, newOwnerGroups);
                    }
                    migrated.put("player_groups", newGroups);
                    migrated.putInt("schema_version", 2);
                    version = 2;
                }
                default -> throw new IllegalStateException(
                    "Missing player group migration from version: " + version);
            }
        }
        return migrated;
    }
}
