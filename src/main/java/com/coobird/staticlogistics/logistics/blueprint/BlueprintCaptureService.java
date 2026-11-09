package com.coobird.staticlogistics.logistics.blueprint;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.logistics.group.PermissionService;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.logistics.node.*;
import com.coobird.staticlogistics.logistics.node.persistence.ConfigKeys;
import com.coobird.staticlogistics.transfer.TransferTypeSelection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.*;

/// 蓝图区域捕获用例；物品类只负责两次点击和结果提示。
public final class BlueprintCaptureService {
    private BlueprintCaptureService() {
    }

    public enum Status {
        SUCCESS,
        TOO_LARGE,
        NO_PERMISSION,
        EMPTY
    }

    public record CaptureResult(Status status, BlueprintData data, long volume) {
        public static CaptureResult failed(Status status, long volume) {
            return new CaptureResult(status, BlueprintData.EMPTY, volume);
        }

        public static CaptureResult success(BlueprintData data, long volume) {
            return new CaptureResult(Status.SUCCESS, data, volume);
        }
    }

    public static CaptureResult capture(
        ServerLevel level,
        Player player,
        BlockPos anchor,
        BlockPos corner,
        GroupRef selectedGroup
    ) {
        int minX = Math.min(anchor.getX(), corner.getX());
        int minY = Math.min(anchor.getY(), corner.getY());
        int minZ = Math.min(anchor.getZ(), corner.getZ());
        int maxX = Math.max(anchor.getX(), corner.getX());
        int maxY = Math.max(anchor.getY(), corner.getY());
        int maxZ = Math.max(anchor.getZ(), corner.getZ());

        long sizeX = (long) maxX - minX + 1L;
        long sizeY = (long) maxY - minY + 1L;
        long sizeZ = (long) maxZ - minZ + 1L;
        long volume;
        try {
            volume = Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ);
        } catch (ArithmeticException exception) {
            volume = Long.MAX_VALUE;
        }
        if (sizeX > BlueprintDataValidator.MAX_BLUEPRINT_VOLUME
            || sizeY > BlueprintDataValidator.MAX_BLUEPRINT_VOLUME
            || sizeZ > BlueprintDataValidator.MAX_BLUEPRINT_VOLUME
            || volume > BlueprintDataValidator.MAX_BLUEPRINT_VOLUME) {
            return CaptureResult.failed(Status.TOO_LARGE, volume);
        }

        GroupRef authoritativeGroup = PlayerGroupStore.get(level.getServer())
            .findGroup(selectedGroup.key());
        if (authoritativeGroup == null
            || !authoritativeGroup.displayName().equals(selectedGroup.displayName())
            || !PermissionService.getInstance().canAccess(authoritativeGroup.key().ownerId(), player)) {
            return CaptureResult.failed(Status.NO_PERMISSION, volume);
        }

        LinkManager manager = LinkManager.get(level);
        List<BlueprintData.BlockEntry> entries = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockPos relativePos = pos.subtract(anchor);
                    Map<ChannelPort, BlueprintData.FaceEntry> faces = new LinkedHashMap<>();
                    ContainerConfig sourceContainer = manager.getContainerConfig(pos);
                    for (EndpointAddress address : sourceContainer == null ? Set.<EndpointAddress>of() : sourceContainer.getLinkedFaceKeys()) {
                        Direction face = address.face();
                        EndpointConfig config = manager.getEndpointConfig(address);
                        if (config == null || config.isDefault()
                            || !config.faceConfig.getGroupKeys().contains(selectedGroup.key())
                            || !config.canPlayerAccess(player)) continue;

                        List<BlueprintData.LinkEntry> links = new ArrayList<>();
                        for (LogisticsNode linked : config.getLinkedNodes(selectedGroup.key())) {
                            BlockPos linkedPos = linked.gPos().pos();
                            if (linked.isInSameDimension(level.dimension())
                                && linkedPos.getX() >= minX && linkedPos.getX() <= maxX
                                && linkedPos.getY() >= minY && linkedPos.getY() <= maxY
                                && linkedPos.getZ() >= minZ && linkedPos.getZ() <= maxZ) {
                                links.add(new BlueprintData.LinkEntry(
                                    linkedPos.subtract(anchor), linked.face(), linked.channel()));
                            }
                        }
                        faces.put(new ChannelPort(face, address.channel()), new BlueprintData.FaceEntry(
                            encodeFace(config),
                            new CompoundTag(),
                            config.filterConfig.getUpgrades().serializeNBT(),
                            links));
                    }

                    CompoundTag containerUpgrades = new CompoundTag();
                    ContainerConfig container = manager.getContainerConfig(pos);
                    if (!faces.isEmpty() && container != null && !container.isDefault()) {
                        containerUpgrades = container.getUpgrades().serializeNBT();
                    }
                    if (!faces.isEmpty() || !containerUpgrades.isEmpty()) {
                        entries.add(new BlueprintData.BlockEntry(
                            relativePos, faces, containerUpgrades, List.of()));
                    }
                }
            }
        }

        if (entries.isEmpty()) return CaptureResult.failed(Status.EMPTY, volume);
        return CaptureResult.success(new BlueprintData(
            anchor, corner, authoritativeGroup.displayName(), entries), volume);
    }

    /// UI 框选按精确方块集合复制，不把包围盒内未选中的容器带入蓝图。
    public static CaptureResult captureSelected(ServerLevel level, Player player, BlockPos anchor, Set<BlockPos> positions, GroupRef group) {
        return captureSelected(level, player, anchor, positions, group, null);
    }

    public static CaptureResult captureSelected(ServerLevel level, Player player, BlockPos anchor, Set<BlockPos> positions, GroupRef group,
                                                Set<ConnectionKey> connections) {
        Set<LogisticsNode> included = new HashSet<>();
        Map<LogisticsNode, Set<LogisticsNode>> selectedLinks = new HashMap<>();
        if (connections != null) {
            if (connections.isEmpty()) return CaptureResult.failed(Status.EMPTY, 0);
            var routesByConnection = ContainerConnections.routesBatch(
                level.getServer(), connections);
            for (var key : connections) {
                if (!key.groupKey().equals(group.key())
                    || !PermissionService.getInstance().canAccess(key.groupKey().ownerId(), player)) {
                    return CaptureResult.failed(Status.NO_PERMISSION, 0);
                }
                var routes = routesByConnection.getOrDefault(key, List.of());
                if (routes.isEmpty()) return CaptureResult.failed(Status.EMPTY, 0);
                routes.forEach(route -> {
                    included.add(route.first());
                    included.add(route.second());
                    selectedLinks.computeIfAbsent(route.first(), ignored -> new HashSet<>()).add(route.second());
                    selectedLinks.computeIfAbsent(route.second(), ignored -> new HashSet<>()).add(route.first());
                });
            }
        }
        if (positions.isEmpty() || !positions.contains(anchor)) return CaptureResult.failed(Status.EMPTY, 0);
        if (positions.size() > BlueprintData.MAX_BLOCKS)
            return CaptureResult.failed(Status.TOO_LARGE, positions.size());
        var authoritative = PlayerGroupStore.get(level.getServer()).findGroup(group.key());
        if (authoritative == null || !PermissionService.getInstance().canAccess(group.key().ownerId(), player))
            return CaptureResult.failed(Status.NO_PERMISSION, positions.size());
        var manager = LinkManager.get(level);
        List<BlueprintData.BlockEntry> blocks = new ArrayList<>();
        for (BlockPos pos : positions.stream().sorted().toList()) {
            if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                || level.getBlockState(pos).isAir()) return CaptureResult.failed(Status.EMPTY, positions.size());
            BlockPos relative = pos.subtract(anchor);
            if (Math.abs((long) relative.getX()) > BlueprintDataValidator.MAX_BLUEPRINT_VOLUME || Math.abs((long) relative.getY()) > BlueprintDataValidator.MAX_BLUEPRINT_VOLUME || Math.abs((long) relative.getZ()) > BlueprintDataValidator.MAX_BLUEPRINT_VOLUME)
                return CaptureResult.failed(Status.TOO_LARGE, positions.size());
            ContainerConfig container = manager.getContainerConfig(pos);
            if (container == null) return CaptureResult.failed(Status.EMPTY, positions.size());
            Map<ChannelPort, BlueprintData.FaceEntry> faces = new LinkedHashMap<>();
            for (EndpointAddress address : container.getLinkedFaceKeys()) {
                var config = manager.getEndpointConfig(address);
                if (config == null || (connections == null ? !config.faceConfig.containsGroup(group.key()) : !included.contains(address.toNode(level.dimension()))))
                    continue;
                if (!config.canPlayerAccess(player))
                    return CaptureResult.failed(Status.NO_PERMISSION, positions.size());
                LogisticsNode current = address.toNode(level.dimension());
                Set<LogisticsNode> selectedPeers = selectedLinks.getOrDefault(current, Set.of());
                var links = config.getLinkedNodes(group.key()).stream()
                    .filter(node -> connections == null || selectedPeers.contains(node))
                    .filter(node -> {
                        var peerLevel = level.getServer().getLevel(node.gPos().dimension());
                        var peer = peerLevel == null ? null : LinkManager.get(peerLevel).getEndpointConfig(EndpointAddress.of(node));
                        return peer != null && peer.getLinkedNodes(group.key()).contains(current);
                    }).distinct()
                    .filter(node -> node.isInSameDimension(level.dimension()) && positions.contains(node.gPos().pos()))
                    .map(node -> new BlueprintData.LinkEntry(node.gPos().pos().subtract(anchor), node.face(), node.channel())).toList();
                faces.put(new ChannelPort(address.face(), address.channel()), new BlueprintData.FaceEntry(encodeFace(config), new CompoundTag(),
                    config.filterConfig.getUpgrades().serializeNBT(), links));
            }
            if (faces.isEmpty()) return CaptureResult.failed(Status.EMPTY, positions.size());
            blocks.add(new BlueprintData.BlockEntry(relative, faces, container.getUpgrades().serializeNBT(), List.of()));
        }
        var data = new BlueprintData(anchor, anchor, authoritative.displayName(), blocks);
        return BlueprintDataValidator.isValid(data, level.registryAccess()) ? CaptureResult.success(data, positions.size()) : CaptureResult.failed(Status.TOO_LARGE, positions.size());
    }

    private static CompoundTag encodeFace(EndpointConfig config) {
        CompoundTag tag = new CompoundTag();
        tag.putString(ConfigKeys.STRATEGY, config.linkConfig.getStrategy().id().toString());
        tag.putString(ConfigKeys.EXTRACTION_MODE, config.linkConfig.getExtractionMode().name());
        tag.putLong(ConfigKeys.PRIORITY, config.linkConfig.getPriority());
        tag.putLong(ConfigKeys.KEEP_STOCK, config.linkConfig.getKeepStock());
        tag.putString("redstone", config.linkConfig.getRedstoneMode().name());
        tag.putString("channel_name", config.linkConfig.getChannelName());
        tag.putBoolean("channel_enabled", config.linkConfig.isEnabled());
        tag.putBoolean(ConfigKeys.GLOBAL_INPUT, config.isGlobalInputEnabled());
        tag.putBoolean(ConfigKeys.GLOBAL_OUTPUT, config.isGlobalOutputEnabled());
        TransferTypeSelection.writeIds(tag, ConfigKeys.SELECTED_TYPES, config.getSelectedTypeIds());

        return tag;
    }
}
