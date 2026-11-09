package com.coobird.staticlogistics.logistics.group;

import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;

/// 独立的玩家分组持久化存储。
/// 继承 SavedData，修改后直接 setDirty()，由 Minecraft 自动保存。
/// 主线程单线程访问，无需 ConcurrentHashMap。
public class PlayerGroupStore extends SavedData {
    private static final String DATA_NAME = "static_logistics_player_groups_v2";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int SCHEMA_VERSION = 3;
    private static final int MAX_STORED_OWNERS = 100_000;
    private static final int MAX_STORED_CONNECTION_NAMES = 1_000_000;
    private final Map<UUID, LinkedHashMap<UUID, String>> playerGroups = new HashMap<>();
    private final Map<ConnectionKey, String> connectionNames = new HashMap<>();
    private final Map<UUID, Map<UUID, Integer>> playerGrants = new HashMap<>();
    private final Set<UUID> disabledTeamAccess = new HashSet<>();

    public boolean teamAccessEnabled(UUID owner) {
        return !disabledTeamAccess.contains(owner);
    }

    public void setTeamAccessEnabled(UUID owner, boolean enabled) {
        Objects.requireNonNull(owner);
        if (enabled) disabledTeamAccess.remove(owner);
        else disabledTeamAccess.add(owner);
        setDirty();
    }

    /// 0：无授权；1：仅查看；2：可管理。所有者只能修改自己的授权表。
    public int grant(UUID owner, UUID actor) {
        return playerGrants.getOrDefault(owner, Map.of()).getOrDefault(actor, 0);
    }

    public Map<UUID, Integer> grantsByOwner(UUID owner) {
        return Map.copyOf(playerGrants.getOrDefault(owner, Map.of()));
    }

    public void setGrant(UUID owner, UUID actor, int permission) {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(actor);
        if (owner.equals(actor) || permission < 0 || permission > 2)
            throw new IllegalArgumentException("Invalid player grant");
        if (permission == 0) {
            var grants = playerGrants.get(owner);
            if (grants != null) {
                grants.remove(actor);
                if (grants.isEmpty()) playerGrants.remove(owner);
            }
        } else {
            var grants = playerGrants.computeIfAbsent(owner, ignored -> new HashMap<>());
            if (!grants.containsKey(actor) && grants.size() >= 1024)
                throw new IllegalStateException("Player grant limit exceeded");
            grants.put(actor, permission);
        }
        setDirty();
    }

    public Set<UUID> ownersGrantedTo(UUID actor) {
        Set<UUID> owners = new HashSet<>();
        playerGrants.forEach((owner, grants) -> {
            if (grants.getOrDefault(actor, 0) > 0) owners.add(owner);
        });
        return Set.copyOf(owners);
    }

    private RuntimeException loadFailure;
    private CompoundTag hierarchies = new CompoundTag();

    public CompoundTag getHierarchy(UUID player) {
        return hierarchies.getCompound(player.toString()).copy();
    }

    public void setHierarchy(UUID player, CompoundTag hierarchy) {
        if (player == null || hierarchy == null) {
            throw new IllegalArgumentException("Hierarchy owner and data are required");
        }
        if (!hierarchies.contains(player.toString())
            && hierarchies.size() >= MAX_STORED_OWNERS) {
            throw new IllegalStateException("Hierarchy owner limit exceeded");
        }
        hierarchies.put(player.toString(), GroupHierarchy.sanitize(hierarchy));
        setDirty();
    }

    PlayerGroupStore() {
    }

    public static PlayerGroupStore get(MinecraftServer server) {
        PlayerGroupStore result = server.overworld().getDataStorage().computeIfAbsent(
            tag -> {
                PlayerGroupStore store = new PlayerGroupStore();
                try {
                    store.load(tag, server.registryAccess());
                } catch (RuntimeException failure) {
                    store.loadFailure = failure;
                }
                return store;
            },
            PlayerGroupStore::new,
            DATA_NAME
        );
        result.requireLoaded();
        return result;
    }

    private void requireLoaded() {
        if (loadFailure != null) throw new IllegalStateException(
            "Cannot load Static Logistics group data; the original data has been preserved.", loadFailure);
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
        CompoundTag hierarchy = getHierarchy(playerId);
        String folder = hierarchy.getString("selectedFolder");
        if (!folder.isEmpty() && GroupHierarchy.edit(
            hierarchy, "assign", GroupHierarchy.groupId(key), folder, "")) {
            setHierarchy(playerId, hierarchy);
        }
        setDirty();
        return new GroupRef(key, displayName);
    }

    /// 解析已有分组；不存在时创建新的随机稳定身份。
    public GroupRef resolveOrCreateGroup(UUID playerId, String displayName) {
        return createGroup(playerId, displayName);
    }

    @Nullable
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

    @Nullable
    public GroupRef findGroup(GroupKey key) {
        if (key == null || key.isLegacyUnowned()) return null;
        Map<UUID, String> groups = playerGroups.get(key.ownerId());
        if (groups == null) return null;
        String displayName = groups.get(key.internalId());
        return displayName == null ? null : new GroupRef(key, displayName);
    }

    public boolean removeGroup(GroupKey key) {
        return removeGroup(key, Set.of());
    }

    public boolean removeGroup(GroupKey key, Collection<ConnectionKey> connections) {
        if (key == null || key.isLegacyUnowned()) return false;
        LinkedHashMap<UUID, String> groups = playerGroups.get(key.ownerId());
        if (groups == null || groups.remove(key.internalId()) == null) return false;
        if (groups.isEmpty()) playerGroups.remove(key.ownerId());
        connectionNames.keySet().removeIf(connection -> connection.groupKey().equals(key));
        Set<String> assignmentIds = new LinkedHashSet<>();
        assignmentIds.add(GroupHierarchy.groupId(key));
        connections.stream().filter(connection -> connection.groupKey().equals(key))
            .map(GroupHierarchy::connectionId).forEach(assignmentIds::add);
        removeHierarchyAssignments(assignmentIds);
        setDirty();
        return true;
    }

    public String getConnectionName(ConnectionKey key) {
        if (key == null) return "";
        return connectionNames.getOrDefault(key, "");
    }

    /// 设置连接显示名；空名称会删除自定义值并恢复默认连接序号。
    public boolean setConnectionName(ConnectionKey key, String displayName) {
        if (key == null || findGroup(key.groupKey()) == null) return false;
        String normalized;
        try {
            normalized = GroupConstraints.normalizeConnectionName(displayName);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        if (normalized.isEmpty()) {
            if (connectionNames.remove(key) != null) setDirty();
            return true;
        }
        if (!connectionNames.containsKey(key)
            && connectionNames.size() >= MAX_STORED_CONNECTION_NAMES) {
            return false;
        }
        if (normalized.equals(connectionNames.put(key, normalized))) return true;
        setDirty();
        return true;
    }

    public void removeConnectionMetadata(Collection<ConnectionKey> connections) {
        if (connections == null || connections.isEmpty()) return;
        boolean changed = connectionNames.keySet().removeAll(connections);
        Set<String> assignmentIds = new LinkedHashSet<>();
        connections.stream().map(GroupHierarchy::connectionId).forEach(assignmentIds::add);
        for (String owner : hierarchies.getAllKeys()) {
            changed |= GroupHierarchy.removeAssignments(
                hierarchies.getCompound(owner), assignmentIds);
        }
        if (changed) setDirty();
    }

    /// 启动收敛时只保留仍对应现有分组或连接的目录归属。
    public int reconcileConnectionMetadata(Collection<ConnectionKey> connections) {
        Set<ConnectionKey> existingConnections = connections == null
            ? Set.of() : Set.copyOf(connections);
        int removed = connectionNames.size();
        connectionNames.keySet().retainAll(existingConnections);
        removed -= connectionNames.size();
        Set<String> valid = new LinkedHashSet<>();
        playerGroups.forEach((owner, groups) -> groups.keySet().forEach(internalId ->
            valid.add(GroupHierarchy.groupId(new GroupKey(owner, internalId)))));
        existingConnections.stream().map(GroupHierarchy::connectionId).forEach(valid::add);
        for (String owner : hierarchies.getAllKeys()) {
            CompoundTag hierarchy = hierarchies.getCompound(owner);
            CompoundTag links = hierarchy.getCompound("links");
            for (String id : links.getAllKeys().toArray(String[]::new)) {
                if (valid.contains(id)) continue;
                links.remove(id);
                removed++;
            }
            hierarchy.put("links", links);
        }
        if (removed > 0) setDirty();
        return removed;
    }

    /// 连接身份变化时，显示名和所有玩家的目录归属必须同时迁移。
    public void remapConnectionMetadata(Map<ConnectionKey, ConnectionKey> connections,
                                        Map<GroupKey, GroupKey> groups) {
        if ((connections == null || connections.isEmpty()) && (groups == null || groups.isEmpty())) return;
        Map<GroupKey, GroupKey> groupMoves = groups == null ? Map.of() : groups;
        Map<ConnectionKey, ConnectionKey> connectionMoves = expandConnectionMoves(
            connections == null ? Map.of() : connections, groupMoves);

        Map<ConnectionKey, String> remappedNames = new LinkedHashMap<>();
        connectionNames.forEach((key, name) -> {
            if (!connectionMoves.containsKey(key)) remappedNames.put(key, name);
        });
        connectionNames.forEach((key, name) -> {
            if (connectionMoves.containsKey(key)) {
                remappedNames.putIfAbsent(connectionMoves.get(key), name);
            }
        });

        Map<String, String> assignmentMoves = new LinkedHashMap<>();
        connectionMoves.forEach((before, after) -> assignmentMoves.put(
            GroupHierarchy.connectionId(before), GroupHierarchy.connectionId(after)));
        groupMoves.forEach((before, after) -> assignmentMoves.put(
            GroupHierarchy.groupId(before), GroupHierarchy.groupId(after)));
        CompoundTag remappedHierarchies = hierarchies.copy();
        for (String owner : remappedHierarchies.getAllKeys()) {
            GroupHierarchy.remapAssignments(
                remappedHierarchies.getCompound(owner), assignmentMoves);
        }
        connectionNames.clear();
        connectionNames.putAll(remappedNames);
        hierarchies = remappedHierarchies;
        setDirty();
    }

    public ConnectionMetadataSnapshot captureConnectionMetadata(
        Map<ConnectionKey, ConnectionKey> connections,
        Map<GroupKey, GroupKey> groups
    ) {
        Map<ConnectionKey, ConnectionKey> connectionMoves = expandConnectionMoves(connections, groups);
        Set<ConnectionKey> keys = new LinkedHashSet<>();
        connectionMoves.forEach((before, after) -> {
            keys.add(before);
            keys.add(after);
        });
        Map<ConnectionKey, String> names = new LinkedHashMap<>();
        keys.forEach(key -> {
            if (connectionNames.containsKey(key)) names.put(key, connectionNames.get(key));
        });
        Set<String> assignmentIds = new LinkedHashSet<>();
        connectionMoves.forEach((before, after) -> {
            assignmentIds.add(GroupHierarchy.connectionId(before));
            assignmentIds.add(GroupHierarchy.connectionId(after));
        });
        groups.forEach((before, after) -> {
            assignmentIds.add(GroupHierarchy.groupId(before));
            assignmentIds.add(GroupHierarchy.groupId(after));
        });
        CompoundTag assignments = new CompoundTag();
        for (String owner : hierarchies.getAllKeys()) {
            CompoundTag links = hierarchies.getCompound(owner).getCompound("links");
            CompoundTag captured = new CompoundTag();
            for (String id : assignmentIds) {
                if (links.contains(id, Tag.TAG_STRING)) captured.putString(id, links.getString(id));
            }
            if (!captured.isEmpty()) assignments.put(owner, captured);
        }
        return new ConnectionMetadataSnapshot(keys, names, assignmentIds, assignments);
    }

    private Map<ConnectionKey, ConnectionKey> expandConnectionMoves(
        Map<ConnectionKey, ConnectionKey> connections,
        Map<GroupKey, GroupKey> groups
    ) {
        Map<ConnectionKey, ConnectionKey> expanded = new LinkedHashMap<>(connections);
        connectionNames.keySet().forEach(key -> {
            GroupKey target = groups.get(key.groupKey());
            if (target != null) expanded.putIfAbsent(key, new ConnectionKey(
                target, key.first(), key.second()));
        });
        return expanded;
    }

    public void restoreConnectionMetadata(ConnectionMetadataSnapshot snapshot) {
        snapshot.keys().forEach(connectionNames::remove);
        connectionNames.putAll(snapshot.names());
        for (String owner : hierarchies.getAllKeys()) {
            GroupHierarchy.removeAssignments(
                hierarchies.getCompound(owner), snapshot.assignmentIds());
        }
        for (String owner : snapshot.assignments().getAllKeys()) {
            CompoundTag hierarchy = hierarchies.getCompound(owner);
            CompoundTag links = hierarchy.getCompound("links");
            CompoundTag captured = snapshot.assignments().getCompound(owner);
            for (String id : captured.getAllKeys()) links.putString(id, captured.getString(id));
            hierarchy.put("links", links);
        }
        setDirty();
    }

    public GroupDirectorySnapshot captureGroups(Collection<GroupKey> keys) {
        Set<GroupKey> capturedKeys = keys == null ? Set.of() : Set.copyOf(keys);
        Map<GroupKey, String> existing = new LinkedHashMap<>();
        for (GroupKey key : capturedKeys) {
            GroupRef group = findGroup(key);
            if (group != null) existing.put(key, group.displayName());
        }
        return new GroupDirectorySnapshot(capturedKeys, existing);
    }

    public void restoreGroups(GroupDirectorySnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("Group snapshot is required");
        for (GroupKey key : snapshot.keys()) {
            LinkedHashMap<UUID, String> groups = playerGroups.get(key.ownerId());
            if (groups == null) continue;
            groups.remove(key.internalId());
            if (groups.isEmpty()) playerGroups.remove(key.ownerId());
        }
        snapshot.existing().forEach((key, name) -> playerGroups
            .computeIfAbsent(key.ownerId(), ignored -> new LinkedHashMap<>())
            .put(key.internalId(), name));
        setDirty();
    }

    private void removeHierarchyAssignments(Collection<String> assignmentIds) {
        if (assignmentIds.isEmpty()) return;
        for (String owner : hierarchies.getAllKeys()) {
            GroupHierarchy.removeAssignments(hierarchies.getCompound(owner), assignmentIds);
        }
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

    /// 仅校验重命名，不修改分组目录。
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
        return collision == null || collision.key().equals(key);
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

    /// 校验旧版无所有者分组能否原样归入玩家，校验阶段不修改存档。
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

    /// 在全部节点完成认领后提交分组目录。
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


    /// 将一组稳定分组身份迁移到新所有者；冲突时不修改任何状态。
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

    public record ConnectionMetadataSnapshot(
        Set<ConnectionKey> keys,
        Map<ConnectionKey, String> names,
        Set<String> assignmentIds,
        CompoundTag assignments
    ) {
        public ConnectionMetadataSnapshot {
            keys = Set.copyOf(keys);
            names = Collections.unmodifiableMap(new LinkedHashMap<>(names));
            assignmentIds = Set.copyOf(assignmentIds);
            assignments = assignments.copy();
        }
    }

    public record GroupDirectorySnapshot(
        Set<GroupKey> keys,
        Map<GroupKey, String> existing
    ) {
        public GroupDirectorySnapshot {
            keys = Set.copyOf(keys);
            existing = Collections.unmodifiableMap(new LinkedHashMap<>(existing));
        }
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

    @Override
    public CompoundTag save(CompoundTag tag) {
        requireLoaded();
        tag.putInt("schema_version", SCHEMA_VERSION);
        tag.put("hierarchies", hierarchies.copy());
        CompoundTag grantsTag = new CompoundTag();
        playerGrants.forEach((owner, grants) -> {
            CompoundTag entries = new CompoundTag();
            grants.forEach((actor, permission) -> entries.putInt(actor.toString(), permission));
            grantsTag.put(owner.toString(), entries);
        });
        tag.put("player_grants", grantsTag);
        CompoundTag disabledTeams = new CompoundTag();
        disabledTeamAccess.forEach(owner -> disabledTeams.putBoolean(owner.toString(), true));
        tag.put("disabled_team_access", disabledTeams);

        /// 保存玩家分组
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

        ListTag namesTag = new ListTag();
        connectionNames.forEach((key, displayName) -> {
            CompoundTag entry = new CompoundTag();
            DataResult<Tag> encoded = ConnectionKey.CODEC.encodeStart(NbtOps.INSTANCE, key);
            encoded.resultOrPartial(message ->
                LOGGER.error("Failed to encode connection name key: {}", message)).ifPresent(value -> {
                entry.put("connection", value);
                entry.putString("name", displayName);
                namesTag.add(entry);
            });
        });
        tag.put("connection_names", namesTag);
        return tag;
    }

    void load(CompoundTag tag, HolderLookup.Provider provider) {
        if (!tag.contains("schema_version", Tag.TAG_INT) || tag.getInt("schema_version") != SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported player group schema version: " + tag.getInt("schema_version"));
        }
        CompoundTag storedHierarchies = tag.getCompound("hierarchies");
        CompoundTag disabledTeams = tag.getCompound("disabled_team_access");
        for (String key : disabledTeams.getAllKeys()) {
            try {
                if (disabledTeams.getBoolean(key)) disabledTeamAccess.add(UUID.fromString(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
        CompoundTag grantsTag = tag.getCompound("player_grants");
        for (String ownerKey : grantsTag.getAllKeys()) {
            try {
                UUID owner = UUID.fromString(ownerKey);
                CompoundTag entries = grantsTag.getCompound(ownerKey);
                Map<UUID, Integer> grants = new HashMap<>();
                for (String actorKey : entries.getAllKeys()) {
                    if (grants.size() >= 1024) break;
                    try {
                        UUID actor = UUID.fromString(actorKey);
                        int permission = entries.getInt(actorKey);
                        if (!owner.equals(actor) && permission >= 1 && permission <= 2)
                            grants.put(actor, permission);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (!grants.isEmpty()) playerGrants.put(owner, grants);
            } catch (IllegalArgumentException ignored) {
            }
        }
        hierarchies = new CompoundTag();
        int hierarchyOwners = 0;
        for (String owner : storedHierarchies.getAllKeys()) {
            if (hierarchyOwners >= MAX_STORED_OWNERS) break;
            try {
                UUID.fromString(owner);
            } catch (IllegalArgumentException exception) {
                LOGGER.warn("Skipping invalid hierarchy owner: {}", owner);
                continue;
            }
            if (!storedHierarchies.contains(owner, Tag.TAG_COMPOUND)) continue;
            hierarchies.put(owner, GroupHierarchy.sanitize(storedHierarchies.getCompound(owner)));
            hierarchyOwners++;
        }
        CompoundTag migrated = tag;
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

        if (migrated.contains("connection_names", Tag.TAG_LIST)) {
            ListTag namesTag = migrated.getList("connection_names", Tag.TAG_COMPOUND);
            int count = Math.min(namesTag.size(), MAX_STORED_CONNECTION_NAMES);
            for (int index = 0; index < count; index++) {
                CompoundTag entry = namesTag.getCompound(index);
                if (!entry.contains("connection") || !entry.contains("name", Tag.TAG_STRING)) continue;
                ConnectionKey.CODEC.parse(NbtOps.INSTANCE, entry.get("connection"))
                    .resultOrPartial(message ->
                        LOGGER.warn("Skipping invalid connection name key: {}", message))
                    .ifPresent(key -> {
                        try {
                            String name = GroupConstraints.normalizeConnectionName(
                                entry.getString("name"));
                            if (!name.isEmpty() && findGroup(key.groupKey()) != null) {
                                connectionNames.put(key, name);
                            }
                        } catch (IllegalArgumentException exception) {
                            LOGGER.warn("Skipping invalid connection name");
                        }
                    });
            }
        }
    }

}
