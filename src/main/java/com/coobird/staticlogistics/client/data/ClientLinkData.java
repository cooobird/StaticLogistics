package com.coobird.staticlogistics.client.data;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 客户端物流数据存储 —— 接收服务端同步的面配置，供 GUI 和世界渲染使用。
 *
 * <p>职责：
 * <ul>
 *   <li>按维度存储所有面配置（由服务端通过 S2C 包同步）</li>
 *   <li>维护已知组 ID、所有者名称、所有者 Profile 的缓存</li>
 *   <li>维护服务端同步的空分组列表</li>
 *   <li>提供查询接口供 GUI 和 {@link com.coobird.staticlogistics.client.render.LinkWorldRenderer} 使用</li>
 * </ul>
 *
 * <p>线程安全：所有 Map 使用 ConcurrentHashMap，数据变更通过 {@code dataVersion} 计数器通知 GUI 刷新。
 */
@OnlyIn(Dist.CLIENT)
public enum ClientLinkData {
    INSTANCE;

    private final Map<ResourceKey<Level>, Map<Long, FaceConfigComposite>> dimensionConfigs = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> knownGroupIds = new ConcurrentHashMap<>();
    private final Map<UUID, String> knownOwnerNames = new ConcurrentHashMap<>();
    private final Map<UUID, CompoundTag> knownOwnerProfiles = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> serverEmptyGroups = new ConcurrentHashMap<>();
    private final AtomicInteger dataVersion = new AtomicInteger();

    public int getDataVersion() {
        return dataVersion.get();
    }

    private void incrementDataVersion() {
        dataVersion.incrementAndGet();
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
     * 更新面配置。服务端是唯一数据源，按序到达，客户端无条件接受。
     */
    public void setFaceConfig(GlobalPos pos, Direction face, FaceConfigComposite config, long version) {
        long key = posToKey(pos.pos(), face);
        Map<Long, FaceConfigComposite> dimMap = getOrCreateDimMap(pos.dimension());
        if (config.isDefault()) {
            FaceConfigComposite removed = dimMap.remove(key);
            if (removed != null) {
                incrementDataVersion();
                cleanupStaleKnownGroups(removed);
            }
            return;
        }
        dimMap.put(key, config);
        incrementDataVersion();

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
        if (dimMap != null) {
            FaceConfigComposite removed = dimMap.remove(key);
            if (removed != null) {
                incrementDataVersion();
                cleanupStaleKnownGroups(removed);
            }
        }
    }

    /**
     * 当面配置被移除时，清理 knownGroupIds 中不再被任何面配置引用的组
     */
    private void cleanupStaleKnownGroups(FaceConfigComposite removed) {
        if (removed == null || !removed.faceConfig.hasGroup()) return;
        UUID owner = removed.faceConfig.getOwner();
        if (owner == null) return;
        for (String gid : removed.faceConfig.getGroupIds()) {
            if (!isGroupInDimensionConfigs(gid)) {
                Set<String> known = knownGroupIds.get(owner);
                if (known != null) {
                    known.remove(gid);
                    if (known.isEmpty()) knownGroupIds.remove(owner);
                }
            }
        }
    }

    public void invalidate() {
        dimensionConfigs.clear();
        knownGroupIds.clear();
        knownOwnerNames.clear();
        knownOwnerProfiles.clear();
        serverEmptyGroups.clear();
        incrementDataVersion();
    }

    /**
     * 从服务端同步空分组数据
     */
    public void setEmptyGroups(UUID playerId, Set<String> emptyGroups) {
        if (emptyGroups.isEmpty()) {
            serverEmptyGroups.remove(playerId);
        } else {
            serverEmptyGroups.put(playerId, new HashSet<>(emptyGroups));
        }
        incrementDataVersion();
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
        // 从已同步的面配置中收集组（权威数据源）
        for (Map<Long, FaceConfigComposite> dimMap : dimensionConfigs.values()) {
            for (FaceConfigComposite cfg : dimMap.values()) {
                if (owners.contains(cfg.faceConfig.getOwner()) && cfg.faceConfig.hasGroup()) {
                    groups.addAll(cfg.faceConfig.getGroupIds());
                }
            }
        }
        // 补充 knownGroupIds（包含刚创建还没链接的组）
        // 过期条目已由 cleanupStaleKnownGroups 在面配置删除时清理
        for (UUID owner : owners) {
            Set<String> known = knownGroupIds.get(owner);
            if (known != null) groups.addAll(known);
        }
        // 补充服务端同步的空分组（持久化的）
        for (UUID owner : owners) {
            Set<String> empty = serverEmptyGroups.get(owner);
            if (empty != null) groups.addAll(empty);
        }
        return new ArrayList<>(groups);
    }

    /**
     * 检查某个组 ID 是否还存在于任何已同步的面配置中（不检查 knownGroupIds）
     */
    private boolean isGroupInDimensionConfigs(String groupId) {
        for (Map<Long, FaceConfigComposite> dimMap : dimensionConfigs.values()) {
            for (FaceConfigComposite cfg : dimMap.values()) {
                if (cfg.faceConfig.hasGroup() && cfg.faceConfig.getGroupIds().contains(groupId)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void addKnownGroup(UUID owner, String ownerName, String groupId) {
        if (groupId == null || groupId.isEmpty()) return;
        knownGroupIds.computeIfAbsent(owner, k -> ConcurrentHashMap.newKeySet()).add(groupId);
        if (ownerName != null && !ownerName.isEmpty()) {
            knownOwnerNames.putIfAbsent(owner, ownerName);
        }
        incrementDataVersion();
    }

    public void removeKnownGroup(UUID owner, String groupId) {
        if (groupId == null || groupId.isEmpty()) return;
        Set<String> set = knownGroupIds.get(owner);
        if (set != null && set.remove(groupId)) incrementDataVersion();
    }

    /**
     * 从服务端同步的空分组中移除指定分组
     */
    public void removeServerEmptyGroup(UUID owner, String groupId) {
        if (groupId == null || groupId.isEmpty()) return;
        Set<String> set = serverEmptyGroups.get(owner);
        if (set != null && set.remove(groupId)) {
            if (set.isEmpty()) serverEmptyGroups.remove(owner);
            incrementDataVersion();
        }
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
        // 检查服务端同步的空分组
        for (var entry : serverEmptyGroups.entrySet()) {
            if (entry.getValue().contains(groupId)) return entry.getKey();
        }
        return null;
    }
}
