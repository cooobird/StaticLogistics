package com.coobird.staticlogistics.logic.group;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 独立的玩家分组持久化存储。
 * 继承 SavedData，修改后直接 setDirty()，由 Minecraft 自动保存。
 */
public class PlayerGroupStore extends SavedData {
    private static final String DATA_NAME = "static_logistics_player_groups";

    private final Map<UUID, Set<String>> playerGroups = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerNextGroupCounter = new ConcurrentHashMap<>();

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

    public void addGroup(UUID playerId, String groupId) {
        if (playerId == null || groupId == null || groupId.isEmpty()) return;
        playerGroups.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(groupId);
        setDirty();
    }

    public void removeGroup(UUID playerId, String groupId) {
        if (playerId == null || groupId == null) return;
        Set<String> groups = playerGroups.get(playerId);
        if (groups != null) {
            groups.remove(groupId);
            if (groups.isEmpty()) playerGroups.remove(playerId);
            setDirty();
        }
    }

    public Set<String> getGroups(UUID playerId) {
        if (playerId == null) return Collections.emptySet();
        return Collections.unmodifiableSet(playerGroups.getOrDefault(playerId, Collections.emptySet()));
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
        Set<String> groups = playerGroups.get(playerId);
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
    public CompoundTag save(CompoundTag tag) {
        // 保存分组计数器
        CompoundTag counterTag = new CompoundTag();
        playerNextGroupCounter.forEach((uuid, counter) -> counterTag.putInt(uuid.toString(), counter));
        tag.put("player_group_counter", counterTag);

        // 保存玩家分组
        CompoundTag groupsTag = new CompoundTag();
        playerGroups.forEach((uuid, groups) -> {
            if (!groups.isEmpty()) {
                CompoundTag playerTag = new CompoundTag();
                int i = 0;
                for (String gid : groups) {
                    playerTag.putString(String.valueOf(i++), gid);
                }
                groupsTag.put(uuid.toString(), playerTag);
            }
        });
        tag.put("player_groups", groupsTag);
        return tag;
    }

    private void load(CompoundTag tag) {
        if (tag.contains("player_group_counter")) {
            CompoundTag counterTag = tag.getCompound("player_group_counter");
            for (String key : counterTag.getAllKeys()) {
                playerNextGroupCounter.put(UUID.fromString(key), counterTag.getInt(key));
            }
        }

        if (tag.contains("player_groups")) {
            CompoundTag groupsTag = tag.getCompound("player_groups");
            for (String uuidStr : groupsTag.getAllKeys()) {
                UUID uuid = UUID.fromString(uuidStr);
                CompoundTag playerTag = groupsTag.getCompound(uuidStr);
                Set<String> groups = ConcurrentHashMap.newKeySet();
                for (String idx : playerTag.getAllKeys()) {
                    String gid = playerTag.getString(idx);
                    if (!gid.isEmpty()) groups.add(gid);
                }
                if (!groups.isEmpty()) {
                    playerGroups.put(uuid, groups);
                }
            }
        }
    }
}
