package com.coobird.staticlogistics.client.data;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public enum ClientLinkData {
    INSTANCE;

    private final Map<ResourceKey<Level>, Map<Long, FaceConfigComposite>> dimensionConfigs = new ConcurrentHashMap<>();
    private final Map<Long, Integer> configVersions = new ConcurrentHashMap<>();
    private int dataVersion = 0;

    public int getDataVersion() {
        return dataVersion;
    }

    private Map<Long, FaceConfigComposite> getOrCreateDimMap(ResourceKey<Level> dim) {
        return dimensionConfigs.computeIfAbsent(dim, k -> new ConcurrentHashMap<>());
    }

    private long posToKey(BlockPos pos, Direction face) {
        return (pos.asLong() << 3) | (long) (face.get3DDataValue() & 0x7);
    }

    private BlockPos keyToPos(long key) {
        return BlockPos.of(key >> 3);
    }

    private Direction keyToFace(long key) {
        return Direction.from3DDataValue((int) (key & 0x7));
    }

    /**
     * 更新面配置，带版本号控制
     *
     * @param pos     全局位置
     * @param face    方向
     * @param config  配置对象
     * @param version 配置的版本号（服务端自增）
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
        if (currentVersion != null && version <= currentVersion) {
            return;
        }
        dimMap.put(key, config);
        dataVersion++;
        configVersions.put(key, version);
    }

    public FaceConfigComposite getFaceConfig(LogisticsNode node) {
        return getFaceConfig(node.gPos(), node.face());
    }

    public FaceConfigComposite getFaceConfig(GlobalPos pos, Direction face) {
        Map<Long, FaceConfigComposite> dimMap = dimensionConfigs.get(pos.dimension());
        return dimMap == null ? null : dimMap.get(posToKey(pos.pos(), face));
    }

    public FaceConfigComposite getFaceConfig(BlockPos pos, Direction face, Level level) {
        return getFaceConfig(GlobalPos.of(level.dimension(), pos), face);
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
        dataVersion++;
    }

    public List<LogisticsNode> getAllNodes() {
        List<LogisticsNode> nodes = new ArrayList<>();
        for (Map.Entry<ResourceKey<Level>, Map<Long, FaceConfigComposite>> dimEntry : dimensionConfigs.entrySet()) {
            ResourceKey<Level> dim = dimEntry.getKey();
            for (Map.Entry<Long, FaceConfigComposite> entry : dimEntry.getValue().entrySet()) {
                long key = entry.getKey();
                nodes.add(new LogisticsNode(GlobalPos.of(dim, keyToPos(key)), keyToFace(key)));
            }
        }
        return nodes;
    }

    public void remove(LogisticsNode node) {
        removeFaceConfig(node.gPos(), node.face());
    }

    public List<String> getGroupsByOwner(UUID owner) {
        Set<String> groups = new HashSet<>();
        for (Map<Long, FaceConfigComposite> dimMap : dimensionConfigs.values()) {
            for (FaceConfigComposite cfg : dimMap.values()) {
                if (owner.equals(cfg.faceConfig.getOwner()) && cfg.faceConfig.hasGroup()) {
                    groups.add(cfg.faceConfig.getGroupId());
                }
            }
        }
        return new ArrayList<>(groups);
    }

    public List<LogisticsNode> getNodesInGroup(String groupId) {
        List<LogisticsNode> nodes = new ArrayList<>();
        dimensionConfigs.forEach((dim, dimMap) -> {
            dimMap.forEach((key, config) -> {
                if (groupId.equals(config.faceConfig.getGroupId())) {
                    nodes.add(new LogisticsNode(GlobalPos.of(dim, keyToPos(key)), keyToFace(key)));
                }
            });
        });
        return nodes;
    }

    public List<BlockPos> getPositionsForGroup(String groupId) {
        List<BlockPos> positions = new ArrayList<>();
        dimensionConfigs.values().forEach(dimMap -> {
            dimMap.forEach((key, config) -> {
                if (groupId.equals(config.faceConfig.getGroupId())) {
                    positions.add(keyToPos(key));
                }
            });
        });
        return positions;
    }
}