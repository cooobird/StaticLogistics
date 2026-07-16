package com.coobird.staticlogistics.logistics.group;

import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.*;

/**
 * 玩家分组的稳定身份目录，并兼容 1.0.4 的名称索引存档。
 */
public class PlayerGroupStore extends SavedData {
    private static final String DATA_NAME = "static_logistics_player_groups";
    private static final int SCHEMA_VERSION = 2;
    private static final int MAX_STORED_OWNERS = 100_000;
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<UUID, LinkedHashMap<UUID, String>> groupsByOwner = new LinkedHashMap<>();
    private final Map<UUID, Integer> playerNextGroupCounter = new LinkedHashMap<>();
    private boolean loadedFromDedicatedStorage;

    private PlayerGroupStore() {
    }

    public static PlayerGroupStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            tag -> {
                PlayerGroupStore store = new PlayerGroupStore();
                store.load(tag);
                return store;
            },
            PlayerGroupStore::new,
            DATA_NAME
        );
    }

    public synchronized GroupRef addGroup(UUID ownerId, String displayName) {
        String normalized = GroupConstraints.normalizeName(displayName);
        Optional<GroupRef> existing = findByName(ownerId, normalized);
        if (existing.isPresent()) return existing.get();
        LinkedHashMap<UUID, String> groups = groupsByOwner.computeIfAbsent(ownerId,
            ignored -> new LinkedHashMap<>());
        if (groups.size() >= GroupConstraints.MAX_GROUPS_PER_OWNER) {
            throw new IllegalStateException("Group limit exceeded");
        }
        GroupKey key = GroupKey.create(ownerId);
        groups.put(key.internalId(), normalized);
        setDirty();
        return new GroupRef(key, normalized);
    }

    public synchronized GroupRef createGroup(UUID ownerId, String displayName) {
        return addGroup(ownerId, displayName);
    }

    public synchronized GroupRef resolveOrCreateGroup(UUID ownerId, String displayName) {
        return addGroup(ownerId, displayName);
    }

    public synchronized GroupRef findGroup(UUID ownerId, String displayName) {
        return findByName(ownerId, displayName).orElse(null);
    }

    public synchronized GroupRef findGroup(GroupKey key) {
        return key == null ? null : find(key).orElse(null);
    }

    public synchronized boolean removeGroup(UUID ownerId, String displayName) {
        Optional<GroupRef> found = findByName(ownerId, displayName);
        if (found.isEmpty()) return false;
        return removeGroup(found.get().key());
    }

    public synchronized boolean removeGroup(GroupKey key) {
        LinkedHashMap<UUID, String> groups = groupsByOwner.get(key.ownerId());
        if (groups == null || groups.remove(key.internalId()) == null) return false;
        if (groups.isEmpty()) groupsByOwner.remove(key.ownerId());
        setDirty();
        return true;
    }

    private synchronized Optional<GroupRef> find(GroupKey key) {
        LinkedHashMap<UUID, String> groups = groupsByOwner.get(key.ownerId());
        if (groups == null) return Optional.empty();
        String name = groups.get(key.internalId());
        return name == null ? Optional.empty() : Optional.of(new GroupRef(key, name));
    }

    private synchronized Optional<GroupRef> findByName(UUID ownerId, String displayName) {
        if (ownerId == null || displayName == null) return Optional.empty();
        String normalized;
        try {
            normalized = GroupConstraints.normalizeName(displayName);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        LinkedHashMap<UUID, String> groups = groupsByOwner.get(ownerId);
        if (groups == null) return Optional.empty();
        return groups.entrySet().stream()
            .filter(entry -> entry.getValue().equals(normalized))
            .map(entry -> new GroupRef(new GroupKey(ownerId, entry.getKey()), entry.getValue()))
            .findFirst();
    }

    public synchronized boolean renameGroup(GroupKey key, String newDisplayName) {
        if (!canRenameGroup(key, newDisplayName)) return false;
        String normalized = GroupConstraints.normalizeName(newDisplayName);
        LinkedHashMap<UUID, String> groups = groupsByOwner.get(key.ownerId());
        if (normalized.equals(groups.get(key.internalId()))) return true;
        groups.put(key.internalId(), normalized);
        setDirty();
        return true;
    }

    /**
     * 只检查重命名是否合法，不修改分组目录。
     */
    public synchronized boolean canRenameGroup(GroupKey key, String newDisplayName) {
        if (key == null || key.isLegacyUnowned()) return false;
        String normalized;
        try {
            normalized = GroupConstraints.normalizeName(newDisplayName);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        LinkedHashMap<UUID, String> groups = groupsByOwner.get(key.ownerId());
        if (groups == null || !groups.containsKey(key.internalId())) return false;
        GroupRef collision = findGroup(key.ownerId(), normalized);
        return collision == null || collision.key().equals(key);
    }

    private synchronized List<GroupRef> groups(UUID ownerId) {
        LinkedHashMap<UUID, String> groups = groupsByOwner.get(ownerId);
        if (groups == null) return List.of();
        List<GroupRef> result = new ArrayList<>();
        groups.forEach((id, name) -> result.add(new GroupRef(new GroupKey(ownerId, id), name)));
        result.sort(Comparator.comparing(GroupRef::displayName).thenComparing(ref -> ref.key().internalId()));
        return List.copyOf(result);
    }

    public synchronized Set<GroupRef> getGroupRefs(UUID ownerId) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(groups(ownerId)));
    }

    /**
     * 旧调用方只读取显示名称；稳定身份始终保留在目录内部。
     */
    public synchronized Set<String> getGroups(UUID ownerId) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (GroupRef group : groups(ownerId)) names.add(group.displayName());
        return Collections.unmodifiableSet(names);
    }

    /**
     * 校验一批认领分组，但不修改目录。
     */
    public synchronized void validateClaimedGroups(UUID ownerId, Collection<GroupRef> claimedGroups) {
        if (ownerId == null || claimedGroups == null) {
            throw new IllegalArgumentException("Claimed group owner and groups are required");
        }
        LinkedHashMap<UUID, String> existing = groupsByOwner.getOrDefault(ownerId, new LinkedHashMap<>());
        Map<UUID, String> additions = new LinkedHashMap<>();
        for (GroupRef group : claimedGroups) {
            if (group == null || !ownerId.equals(group.key().ownerId())) {
                throw new IllegalArgumentException("Claimed group owner does not match target owner");
            }
            String storedName = existing.get(group.key().internalId());
            if (storedName != null && !storedName.equals(group.displayName())) {
                throw new IllegalStateException("Target owner has a conflicting group identity");
            }
            boolean nameCollision = existing.entrySet().stream().anyMatch(entry ->
                !entry.getKey().equals(group.key().internalId())
                    && entry.getValue().equals(group.displayName()));
            if (nameCollision) throw new IllegalStateException("Target owner already has a group with this name");
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

    /**
     * 所有节点认领成功后再提交分组目录。
     */
    public synchronized void registerClaimedGroups(UUID ownerId, Collection<GroupRef> claimedGroups) {
        validateClaimedGroups(ownerId, claimedGroups);
        if (claimedGroups.isEmpty()) return;
        LinkedHashMap<UUID, String> groups = groupsByOwner.computeIfAbsent(ownerId,
            ignored -> new LinkedHashMap<>());
        boolean changed = false;
        for (GroupRef group : claimedGroups) {
            if (groups.putIfAbsent(group.key().internalId(), group.displayName()) == null) changed = true;
        }
        if (changed) setDirty();
    }

    /**
     * 从旧链接存档导入一次嵌入式分组快照。
     * 独立分组存档已有内容时拒绝导入，避免复活被删除的历史分组。
     */
    public synchronized boolean importLegacyStorage(CompoundTag legacyTag) {
        if (loadedFromDedicatedStorage) return false;
        boolean changed = false;
        CompoundTag counters = legacyTag.getCompound("player_group_counter");
        for (String ownerText : counters.getAllKeys()) {
            if (playerNextGroupCounter.size() >= MAX_STORED_OWNERS) break;
            UUID owner;
            try {
                owner = UUID.fromString(ownerText);
            } catch (IllegalArgumentException exception) {
                continue;
            }
            playerNextGroupCounter.merge(owner, Math.max(0, counters.getInt(ownerText)), Math::max);
            changed = true;
        }
        CompoundTag owners = legacyTag.getCompound("player_groups");
        for (String ownerText : owners.getAllKeys()) {
            if (groupsByOwner.size() >= MAX_STORED_OWNERS) break;
            UUID owner;
            try {
                owner = UUID.fromString(ownerText);
            } catch (IllegalArgumentException exception) {
                continue;
            }
            CompoundTag legacyGroups = owners.getCompound(ownerText);
            LinkedHashMap<UUID, String> groups = groupsByOwner.computeIfAbsent(owner,
                ignored -> new LinkedHashMap<>());
            for (String entryKey : new java.util.TreeSet<>(legacyGroups.getAllKeys())) {
                if (groups.size() >= GroupConstraints.MAX_GROUPS_PER_OWNER) break;
                String displayName;
                try {
                    displayName = GroupConstraints.normalizeName(legacyGroups.getString(entryKey));
                } catch (IllegalArgumentException exception) {
                    continue;
                }
                GroupKey key = GroupKey.migrated(owner, displayName);
                if (groups.putIfAbsent(key.internalId(), displayName) == null) changed = true;
            }
        }
        if (changed) {
            loadedFromDedicatedStorage = true;
            setDirty();
        }
        return changed;
    }

    public synchronized void validateGroupTransfer(UUID previousOwner, UUID newOwner,
                                                   Collection<GroupRef> transferred) {
        if (previousOwner == null || newOwner == null || transferred == null) {
            throw new IllegalArgumentException("Group transfer owners and groups are required");
        }
        if (previousOwner.equals(newOwner) || transferred.isEmpty()) return;
        LinkedHashMap<UUID, String> target = groupsByOwner.getOrDefault(newOwner, new LinkedHashMap<>());
        long additions = transferred.stream().map(group -> group.key().internalId()).distinct()
            .filter(id -> !target.containsKey(id)).count();
        if ((long) target.size() + additions > GroupConstraints.MAX_GROUPS_PER_OWNER) {
            throw new IllegalStateException("Group limit exceeded");
        }
        for (GroupRef group : transferred) {
            if (!previousOwner.equals(group.key().ownerId())) {
                throw new IllegalArgumentException("Group does not belong to previous owner");
            }
            String sameId = target.get(group.key().internalId());
            if (sameId != null && !sameId.equals(group.displayName())) {
                throw new IllegalStateException("Target owner has a conflicting group identity");
            }
            boolean sameName = target.entrySet().stream().anyMatch(entry ->
                !entry.getKey().equals(group.key().internalId())
                    && entry.getValue().equals(group.displayName()));
            if (sameName) throw new IllegalStateException("Target owner already has a group with this name");
        }
    }

    public synchronized void transferGroups(UUID previousOwner, UUID newOwner,
                                            Collection<GroupRef> transferred) {
        validateGroupTransfer(previousOwner, newOwner, transferred);
        if (previousOwner.equals(newOwner) || transferred.isEmpty()) return;
        LinkedHashMap<UUID, String> source = groupsByOwner.get(previousOwner);
        LinkedHashMap<UUID, String> target = groupsByOwner.computeIfAbsent(newOwner,
            ignored -> new LinkedHashMap<>());
        for (GroupRef group : transferred) {
            if (source != null) source.remove(group.key().internalId());
            target.put(group.key().internalId(), group.displayName());
        }
        if (source != null && source.isEmpty()) groupsByOwner.remove(previousOwner);
        setDirty();
    }

    public synchronized String getNextGroupIdForPlayer(UUID ownerId) {
        Set<Integer> used = new LinkedHashSet<>();
        for (String name : getGroups(ownerId)) {
            try {
                used.add(Integer.parseInt(name));
            } catch (NumberFormatException ignored) {
                // 非数字显示名称不参与旧版自动编号。
            }
        }
        int next = Math.max(playerNextGroupCounter.getOrDefault(ownerId, 0),
            used.stream().max(Integer::compareTo).orElse(0)) + 1;
        while (used.contains(next)) next++;
        playerNextGroupCounter.put(ownerId, next);
        setDirty();
        return Integer.toString(next);
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        tag.putInt("schema_version", SCHEMA_VERSION);
        CompoundTag counters = new CompoundTag();
        playerNextGroupCounter.forEach((owner, counter) -> counters.putInt(owner.toString(), counter));
        tag.put("player_group_counter", counters);

        CompoundTag owners = new CompoundTag();
        groupsByOwner.forEach((owner, groups) -> {
            CompoundTag values = new CompoundTag();
            groups.forEach((internalId, displayName) -> values.putString(internalId.toString(), displayName));
            if (!values.isEmpty()) owners.put(owner.toString(), values);
        });
        tag.put("player_groups", owners);
        return tag;
    }

    private synchronized void load(CompoundTag source) {
        CompoundTag tag = source.copy();
        // 只有已有独立 SavedData 文件才会进入此加载分支；即使内容为空，也不能复活旧嵌入快照。
        loadedFromDedicatedStorage = true;
        int version = readVersion(tag);
        readCounters(tag);
        if (tag.contains("player_groups") && !tag.contains("player_groups", Tag.TAG_COMPOUND)) {
            throw new IllegalStateException("Player groups field must be a compound tag");
        }
        CompoundTag owners = tag.getCompound("player_groups");
        for (String ownerText : owners.getAllKeys()) {
            if (groupsByOwner.size() >= MAX_STORED_OWNERS) break;
            UUID owner;
            try {
                owner = UUID.fromString(ownerText);
            } catch (IllegalArgumentException exception) {
                LOGGER.warn("Skipping invalid player group owner: {}", ownerText);
                continue;
            }
            if (!owners.contains(ownerText, Tag.TAG_COMPOUND)) {
                LOGGER.warn("Skipping non-compound player group owner entry: {}", ownerText);
                continue;
            }
            CompoundTag values = owners.getCompound(ownerText);
            LinkedHashMap<UUID, String> groups = new LinkedHashMap<>();
            for (String key : new java.util.TreeSet<>(values.getAllKeys())) {
                if (groups.size() >= GroupConstraints.MAX_GROUPS_PER_OWNER) break;
                try {
                    String name = GroupConstraints.normalizeName(values.getString(key));
                    UUID internalId = version == 1
                        ? GroupKey.migrated(owner, name).internalId()
                        : UUID.fromString(key);
                    groups.putIfAbsent(internalId, name);
                } catch (IllegalArgumentException exception) {
                    LOGGER.warn("Skipping invalid player group entry {} for owner {}", key, owner);
                }
            }
            if (!groups.isEmpty()) groupsByOwner.put(owner, groups);
        }
        if (version < SCHEMA_VERSION) setDirty();
    }

    private static int readVersion(CompoundTag tag) {
        if (tag.contains("schema_version") && !tag.contains("schema_version", Tag.TAG_INT)) {
            throw new IllegalStateException("Player group schema version must be an int");
        }
        int version = tag.contains("schema_version") ? tag.getInt("schema_version") : 1;
        if (version < 1 || version > SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported player group schema version: " + version);
        }
        return version;
    }

    private void readCounters(CompoundTag tag) {
        if (!tag.contains("player_group_counter")) return;
        if (!tag.contains("player_group_counter", Tag.TAG_COMPOUND)) {
            throw new IllegalStateException("Player group counter field must be a compound tag");
        }
        CompoundTag counters = tag.getCompound("player_group_counter");
        for (String ownerText : counters.getAllKeys()) {
            if (playerNextGroupCounter.size() >= MAX_STORED_OWNERS) break;
            try {
                UUID owner = UUID.fromString(ownerText);
                playerNextGroupCounter.put(owner, Math.max(0, counters.getInt(ownerText)));
            } catch (IllegalArgumentException exception) {
                LOGGER.warn("Skipping invalid player group counter owner: {}", ownerText);
            }
        }
    }
}
