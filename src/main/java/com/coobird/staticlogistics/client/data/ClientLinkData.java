package com.coobird.staticlogistics.client.data;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public enum ClientLinkData {
    INSTANCE;

    private final Map<ResourceKey<Level>, Map<Long, FaceConfigComposite>> dimensionConfigs = new ConcurrentHashMap<>();
    private final Map<Long, Integer> configVersions = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> knownGroupIds = new ConcurrentHashMap<>();
    private final Map<UUID, String> knownOwnerNames = new ConcurrentHashMap<>();
    private final Map<UUID, CompoundTag> knownOwnerProfiles = new ConcurrentHashMap<>();
    private int dataVersion = 0;

    public int getDataVersion() {
        return dataVersion;
    }

    private Map<Long, FaceConfigComposite> getOrCreateDimMap(ResourceKey<Level> dim) {
        return dimensionConfigs.computeIfAbsent(dim, k -> new ConcurrentHashMap<>());
    }

    private long posToKey(BlockPos pos, Direction face) {
        return LogisticsNode.posToKey(pos, face);
    }

    private BlockPos keyToPos(long key) {
        return LogisticsNode.keyToPos(key);
    }

    private Direction keyToFace(long key) {
        return LogisticsNode.keyToFace(key);
    }

    /**
     * 更新面配置，带版本号控制
     */
    public void setFaceConfig(GlobalPos pos, Direction face, FaceConfigComposite config, int version) {
        long key = posToKey(pos.pos(), face);
        Map<Long, FaceConfigComposite> dimMap = getOrCreateDimMap(pos.dimension());
        if (config.isDefault()) {
            if (dimMap.remove(key) != null) {
                dataVersion++;
                configVersions.remove(key);
            }
            return;
        }
        Integer currentVersion = configVersions.get(key);
        if (currentVersion != null && version <= currentVersion) return;
        dimMap.put(key, config);
        dataVersion++;
        configVersions.put(key, version);

        UUID owner = config.faceConfig.getOwner();
        if (owner != null && config.faceConfig.hasGroup()) {
            for (String gid : config.faceConfig.getGroupIds()) {
                addKnownGroup(owner, config.faceConfig.getOwnerName(), gid);
                if (!config.faceConfig.getOwnerProfileTag().isEmpty())
                    knownOwnerProfiles.put(owner, config.faceConfig.getOwnerProfileTag());
            }
        }
    }

    public void removeFaceConfig(GlobalPos pos, Direction face) {
        long key = posToKey(pos.pos(), face);
        Map<Long, FaceConfigComposite> dimMap = dimensionConfigs.get(pos.dimension());
        if (dimMap != null && dimMap.remove(key) != null) {
            dataVersion++;
            configVersions.remove(key);
        }
    }

    public void invalidate() {
        dimensionConfigs.clear();
        configVersions.clear();
        knownGroupIds.clear();
        knownOwnerNames.clear();
        knownOwnerProfiles.clear();
        dataVersion++;
    }

    public Map<LogisticsNode, FaceConfigComposite> getActiveNodesWithConfig(ResourceKey<Level> dimension) {
        Map<Long, FaceConfigComposite> dimMap = dimensionConfigs.get(dimension);
        if (dimMap == null || dimMap.isEmpty()) return Collections.emptyMap();
        Map<LogisticsNode, FaceConfigComposite> result = new HashMap<>();
        dimMap.forEach((key, config) -> {
            BlockPos pos = keyToPos(key);
            Direction face = keyToFace(key);
            result.put(new LogisticsNode(GlobalPos.of(dimension, pos), face), config);
        });
        return result;
    }

    public List<String> getGroupsByOwners(Collection<UUID> owners) {
        Set<String> groups = new HashSet<>();
        for (Map<Long, FaceConfigComposite> dimMap : dimensionConfigs.values()) {
            for (FaceConfigComposite cfg : dimMap.values()) {
                if (owners.contains(cfg.faceConfig.getOwner()) && cfg.faceConfig.hasGroup()) {
                    groups.addAll(cfg.faceConfig.getGroupIds());
                }
            }
        }
        for (UUID owner : owners) {
            Set<String> known = knownGroupIds.get(owner);
            if (known != null) groups.addAll(known);
        }
        return new ArrayList<>(groups);
    }

    public void addKnownGroup(UUID owner, String ownerName, String groupId) {
        if (groupId == null || groupId.isEmpty()) return;
        knownGroupIds.computeIfAbsent(owner, k -> ConcurrentHashMap.newKeySet()).add(groupId);
        if (ownerName != null && !ownerName.isEmpty()) {
            knownOwnerNames.putIfAbsent(owner, ownerName);
        }
        dataVersion++;
    }

    public void removeKnownGroup(UUID owner, String groupId) {
        if (groupId == null || groupId.isEmpty()) return;
        Set<String> set = knownGroupIds.get(owner);
        if (set != null && set.remove(groupId)) dataVersion++;
    }

    public List<BlockPos> getPositionsForGroup(String groupId) {
        List<BlockPos> positions = new ArrayList<>();
        dimensionConfigs.values().forEach(dimMap -> {
            dimMap.forEach((key, config) -> {
                if (config.faceConfig.getGroupIds().contains(groupId)) {
                    positions.add(keyToPos(key));
                }
            });
        });
        return positions;
    }

    @Nullable
    public CompoundTag getOwnerProfileForGroup(String groupId) {
        UUID uuid = getOwnerUUIDForGroup(groupId);
        return uuid != null ? knownOwnerProfiles.get(uuid) : null;
    }

    public String getOwnerNameForGroup(String groupId) {
        for (Map<Long, FaceConfigComposite> dimMap : dimensionConfigs.values()) {
            for (FaceConfigComposite cfg : dimMap.values()) {
                if (cfg.faceConfig.getGroupIds().contains(groupId)) {
                    String name = cfg.faceConfig.getOwnerName();
                    if (name != null && !name.isEmpty() && !"Unknown".equals(name)) return name;
                }
            }
        }
        for (var entry : knownGroupIds.entrySet()) {
            if (entry.getValue().contains(groupId)) {
                String name = knownOwnerNames.get(entry.getKey());
                if (name != null && !name.isEmpty()) return name;
                return entry.getKey().toString();
            }
        }
        return "";
    }

    @Nullable
    public UUID getOwnerUUIDForGroup(String groupId) {
        for (Map<Long, FaceConfigComposite> dimMap : dimensionConfigs.values()) {
            for (FaceConfigComposite cfg : dimMap.values()) {
                if (cfg.faceConfig.getGroupIds().contains(groupId)) {
                    UUID owner = cfg.faceConfig.getOwner();
                    if (owner != null) return owner;
                }
            }
        }
        for (var entry : knownGroupIds.entrySet()) {
            if (entry.getValue().contains(groupId)) return entry.getKey();
        }
        return null;
    }
}
