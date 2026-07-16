package com.coobird.staticlogistics.logistics.blueprint;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.logistics.group.GroupService;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.logistics.node.ContainerConfig;
import com.coobird.staticlogistics.logistics.node.FaceAddress;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import com.coobird.staticlogistics.logistics.node.persistence.ConfigKeys;
import com.coobird.staticlogistics.transfer.TransferTypeSelection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 蓝图区域捕获用例；物品类只负责两次点击和结果提示。
 */
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
            || !GroupService.canAccess(authoritativeGroup.key().ownerId(), player)) {
            return CaptureResult.failed(Status.NO_PERMISSION, volume);
        }

        LinkManager manager = LinkManager.get(level);
        List<BlueprintData.BlockEntry> entries = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockPos relativePos = pos.subtract(anchor);
                    Map<Direction, BlueprintData.FaceEntry> faces = new LinkedHashMap<>();
                    for (Direction face : Direction.values()) {
                        FaceConfigComposite config = manager.getFaceConfig(
                            FaceAddress.of(pos, face));
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
                                    linkedPos.subtract(anchor), linked.face()));
                            }
                        }
                        faces.put(face, new BlueprintData.FaceEntry(
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

    private static CompoundTag encodeFace(FaceConfigComposite config) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(ConfigKeys.INPUT_CHANNEL, config.linkConfig.getInputChannel());
        tag.putInt(ConfigKeys.OUTPUT_CHANNEL, config.linkConfig.getOutputChannel());
        tag.putString(ConfigKeys.STRATEGY, config.linkConfig.getStrategy().id().toString());
        tag.putString(ConfigKeys.EXTRACTION_MODE, config.linkConfig.getExtractionMode().name());
        tag.putInt(ConfigKeys.PRIORITY, config.linkConfig.getPriority());
        tag.putBoolean(ConfigKeys.GLOBAL_INPUT, config.isGlobalInputEnabled());
        tag.putBoolean(ConfigKeys.GLOBAL_OUTPUT, config.isGlobalOutputEnabled());
        TransferTypeSelection.writeIds(tag, ConfigKeys.SELECTED_TYPES, config.getSelectedTypeIds());
        tag.putInt(ConfigKeys.SELECTED_TYPES_MASK, config.getLegacySelectedTypesMask());
        return tag;
    }
}

