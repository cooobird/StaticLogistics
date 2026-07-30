package com.coobird.staticlogistics.logistics.blueprint;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.api.type.ExtractionMode;
import com.coobird.staticlogistics.logistics.group.GlobalLogisticsManager;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.logistics.node.*;
import com.coobird.staticlogistics.logistics.node.persistence.ConfigKeys;
import com.coobird.staticlogistics.transfer.DistributionStrategyRegistry;
import com.coobird.staticlogistics.transfer.TransferTypeSelection;
import com.coobird.staticlogistics.transfer.TransferUtils;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 蓝图粘贴与撤销的统一事务协调器。
 */
public final class BlueprintPasteService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private BlueprintPasteService() {
    }

    public static void paste(ServerLevel level, Player player, BlueprintData data,
                             BlockPos newAnchor, int rotation) {
        if (data.isEmpty()) return;
        if (!BlueprintDataValidator.isValid(data, level.registryAccess())) {
            player.displayClientMessage(Component.translatable("msg.staticlogistics.no_permission")
                .withStyle(ChatFormatting.RED), true);
            return;
        }

        GlobalLogisticsManager globalMgr = GlobalLogisticsManager.get(level.getServer());
        LinkManager mgr = LinkManager.get(level);
        int count = 0;
        int skipped = 0;

        for (BlueprintData.BlockEntry entry : data.blocks()) {
            BlockPos absPos = BlueprintGeometry.rotateToAbsolute(entry.relativePos(), newAnchor, rotation);
            if (!level.getChunkSource().hasChunk(absPos.getX() >> 4, absPos.getZ() >> 4)
                || level.getBlockEntity(absPos) == null
                || !NodeInteractionRules.isWithinReach(player.getX(), player.getY(), player.getZ(), absPos)
                || !canModifyPosition(mgr, absPos, player)) {
                player.displayClientMessage(
                    Component.translatable("msg.staticlogistics.blueprint.missing_block", absPos.toShortString())
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            for (Direction face : entry.faces().keySet()) {
                Direction rotatedFace = BlueprintGeometry.rotateDirection(face, rotation);
                if (!TransferUtils.hasLogisticsCapability(level, absPos, rotatedFace)) {
                    player.displayClientMessage(Component.translatable("msg.staticlogistics.no_capability")
                        .withStyle(ChatFormatting.RED), true);
                    return;
                }
            }
        }

        Map<String, Integer> needed = Map.of();
        if (!player.isCreative()) {
            Map<String, Integer> desired = BlueprintUpgradeInventory.tally(data);
            Map<String, Integer> existing = tallyOverwrittenUpgrades(
                mgr, data, newAnchor, rotation);
            if (!BlueprintUpgradeInventory.isCoveredByDesired(existing, desired)) {
                player.displayClientMessage(
                    Component.translatable("msg.staticlogistics.blueprint.paste_failed")
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            Map<String, Integer> calculatedNeeded =
                BlueprintUpgradeInventory.requiredDelta(desired, existing);
            needed = calculatedNeeded;
            if (!calculatedNeeded.isEmpty()) {
                skipped = calculatedNeeded.entrySet().stream()
                    .mapToInt(entry -> Math.max(0,
                        entry.getValue() - BlueprintUpgradeInventory.count(player, entry.getKey())))
                    .sum();
                if (skipped > 0) {
                    player.displayClientMessage(
                        Component.translatable("msg.staticlogistics.blueprint.missing_upgrades", skipped)
                            .withStyle(ChatFormatting.YELLOW), true);
                    return;
                }
            }
        }

        for (BlueprintData.BlockEntry entry : data.blocks()) {
            BlockPos absPos = BlueprintGeometry.rotateToAbsolute(entry.relativePos(), newAnchor, rotation);
            for (Direction face : entry.faces().keySet()) {
                Direction rotatedFace = BlueprintGeometry.rotateDirection(face, rotation);
                FaceConfigComposite existing = mgr.getFaceConfig(FaceAddress.of(absPos, rotatedFace));
                if (existing != null && existing.faceConfig.getOwner() != null
                    && !existing.faceConfig.getOwner().equals(player.getUUID())) {
                    player.displayClientMessage(Component.translatable("msg.staticlogistics.no_permission")
                        .withStyle(ChatFormatting.RED), true);
                    return;
                }
            }
        }
        boolean targetGroupExisted = globalMgr.findGroup(player.getUUID(), data.groupId()) != null;

        // 撤销快照：记录粘贴前的完整状态
        List<BlueprintUndoData.FaceSnapshot> faceSnapshots = new ArrayList<>();
        List<BlueprintUndoData.ContainerSnapshot> containerSnapshots = new ArrayList<>();
        List<BlueprintUndoData.LinkSnapshot> linkSnapshots = new ArrayList<>();
        List<BlueprintUndoData.GroupSnapshot> groupSnapshots = new ArrayList<>();

        Set<BlockPos> pastedPositions = data.blocks().stream()
            .map(entry -> BlueprintGeometry.rotateToAbsolute(entry.relativePos(), newAnchor, rotation))
            .collect(Collectors.toSet());
        Map<BlockPos, BlueprintData.BlockEntry> entriesByRelativePos = data.blocks().stream()
            .collect(Collectors.toMap(
                BlueprintData.BlockEntry::relativePos, entry -> entry, (first, ignored) -> first));

        // 在打开事务前完整冻结回滚基线，后续任何写入都必须已有对应快照。
        for (BlueprintData.BlockEntry entry : data.blocks()) {
            BlockPos absPos = BlueprintGeometry.rotateToAbsolute(entry.relativePos(), newAnchor, rotation);
            ContainerConfig existingCc = mgr.getContainerConfig(absPos);
            containerSnapshots.add(new BlueprintUndoData.ContainerSnapshot(
                absPos, existingCc != null,
                existingCc != null ? existingCc.getUpgrades().serializeNBT(level.registryAccess()) : null
            ));
            for (var faceEntry : entry.faces().entrySet()) {
                Direction rotatedFace = BlueprintGeometry.rotateDirection(faceEntry.getKey(), rotation);
                FaceAddress faceKey = FaceAddress.of(absPos, rotatedFace);
                FaceConfigComposite existingCfg = mgr.getFaceConfig(faceKey);
                if (existingCfg != null) {
                    faceSnapshots.add(new BlueprintUndoData.FaceSnapshot(
                        absPos, rotatedFace, true,
                        existingCfg.serializeNBT(level.registryAccess()),
                        new HashSet<>(existingCfg.getLinkedNodes())
                    ));
                } else {
                    faceSnapshots.add(new BlueprintUndoData.FaceSnapshot(
                        absPos, rotatedFace, false, null, null
                    ));
                }
            }
        }

        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(level.getServer())) {
            GroupRef targetGroup;
            try {
                targetGroup = globalMgr.resolveOrCreateGroup(player.getUUID(), data.groupId());
            } catch (IllegalArgumentException | IllegalStateException exception) {
                player.displayClientMessage(Component.translatable("msg.staticlogistics.no_permission")
                    .withStyle(ChatFormatting.RED), true);
                return;
            }
            if (!targetGroupExisted) {
                transaction.onRollback(() -> {
                    if (!PlayerGroupStore.get(level.getServer()).removeGroup(targetGroup.key())) {
                        throw new IllegalStateException("Created blueprint group rollback failed");
                    }
                    globalMgr.retireGroupIdentity(targetGroup.key());
                });
            }
            if (!player.isCreative() && !needed.isEmpty()) {
                List<ItemStack> inventorySnapshot = new ArrayList<>();
                for (int index = 0; index < player.getInventory().getContainerSize(); index++) {
                    inventorySnapshot.add(player.getInventory().getItem(index).copy());
                }
                transaction.onRollback(() -> {
                    for (int index = 0; index < inventorySnapshot.size(); index++) {
                        player.getInventory().setItem(index, inventorySnapshot.get(index).copy());
                    }
                });
            }
            for (BlueprintUndoData.ContainerSnapshot snapshot : containerSnapshots) {
                transaction.onRollback(() -> mgr.restoreContainerSnapshot(
                    snapshot.pos(), snapshot.existed(), snapshot.upgradesNbt()));
            }
            for (BlueprintUndoData.FaceSnapshot snapshot : faceSnapshots) {
                transaction.captureState(new LogisticsNode(
                    GlobalPos.of(level.dimension(), snapshot.pos()), snapshot.face()));
            }

            for (BlueprintData.BlockEntry entry : data.blocks()) {
                BlockPos rel = entry.relativePos();
                BlockPos absPos = BlueprintGeometry.rotateToAbsolute(rel, newAnchor, rotation);
                ContainerConfig cc = mgr.getOrCreateContainerConfig(absPos);

                if (!entry.containerUpgrades().isEmpty()) {
                    cc.getUpgrades().deserializeNBT(level.registryAccess(), entry.containerUpgrades());
                    cc.markDirty();
                }

                for (var faceEntry : entry.faces().entrySet()) {
                    Direction originalFace = faceEntry.getKey();
                    Direction rotatedFace = BlueprintGeometry.rotateDirection(originalFace, rotation);
                    BlueprintData.FaceEntry fe = faceEntry.getValue();
                    FaceConfigComposite cfg = mgr.getOrCreateFaceConfig(absPos, rotatedFace);

                    try (var ignored = cfg.beginBulkEdit()) {
                        CompoundTag ft = fe.faceConfig();
                        String stratName = ft.getString(ConfigKeys.STRATEGY);
                        if (!stratName.isEmpty()) {
                            cfg.setDistributionStrategy(
                                DistributionStrategyRegistry.byName(stratName));
                        }
                        String extName = ft.getString(ConfigKeys.EXTRACTION_MODE);
                        if (!extName.isEmpty()) {
                            try {
                                cfg.setExtractionMode(
                                    ExtractionMode.valueOf(extName));
                            } catch (Exception e) {
                                LOGGER.warn("Failed to parse extraction mode", e);
                            }
                        }
                        cfg.setPriority(ft.getInt(ConfigKeys.PRIORITY));
                        cfg.setGlobalInputEnabled(ft.getBoolean(ConfigKeys.GLOBAL_INPUT));
                        cfg.setGlobalOutputEnabled(ft.getBoolean(ConfigKeys.GLOBAL_OUTPUT));
                        if (ft.contains(ConfigKeys.SELECTED_TYPES)) {
                            cfg.setSelectedTypeIds(TransferTypeSelection.readIds(ft, ConfigKeys.SELECTED_TYPES));
                            if (ft.contains(ConfigKeys.SELECTED_TYPES_MASK)) {
                                cfg.loadUnresolvedLegacySelectedTypesMask(
                                    ft.getInt(ConfigKeys.SELECTED_TYPES_MASK));
                            }
                        } else {
                            cfg.loadLegacySelectedTypesMask(ft.getInt(ConfigKeys.SELECTED_TYPES_MASK));
                        }

                        if (!fe.filterUpgrades().isEmpty()) {
                            cfg.filterConfig.getUpgrades().deserializeNBT(level.registryAccess(), fe.filterUpgrades());
                        }

                        if (cfg.faceConfig.getOwner() == null) {
                            LogisticsNode node = new LogisticsNode(
                                GlobalPos.of(level.dimension(), absPos), rotatedFace);
                            mgr.claimOwner(node, player.getGameProfile());
                        }
                    }

                    count++;
                }
            }

            for (BlueprintData.BlockEntry entry : data.blocks()) {
                BlockPos absPos = BlueprintGeometry.rotateToAbsolute(entry.relativePos(), newAnchor, rotation);
                for (Direction face : entry.faces().keySet()) {
                    Direction rotatedFace = BlueprintGeometry.rotateDirection(face, rotation);
                    FaceConfigComposite cfg = mgr.getFaceConfig(FaceAddress.of(absPos, rotatedFace));
                    if (cfg != null) {
                        // 记录新增的分组（用于撤销）
                        if (!cfg.faceConfig.getGroupKeys().contains(targetGroup.key())) {
                            groupSnapshots.add(new BlueprintUndoData.GroupSnapshot(absPos, rotatedFace, targetGroup.key()));
                        }
                        LogisticsNode node = new LogisticsNode(
                            GlobalPos.of(level.dimension(), absPos), rotatedFace);
                        mgr.addNodeToGroup(node, targetGroup);
                    }
                }
            }

            for (BlueprintData.BlockEntry entry : data.blocks()) {
                BlockPos absPos = BlueprintGeometry.rotateToAbsolute(entry.relativePos(), newAnchor, rotation);
                for (var faceEntry : entry.faces().entrySet()) {
                    Direction rotatedFace = BlueprintGeometry.rotateDirection(faceEntry.getKey(), rotation);
                    FaceConfigComposite srcCfg = mgr.getFaceConfig(FaceAddress.of(absPos, rotatedFace));
                    if (srcCfg == null) continue;

                    List<BlueprintData.LinkEntry> exactLinks = BlueprintGeometry.resolveLinks(
                        entry, faceEntry.getValue(), entriesByRelativePos);
                    for (BlueprintData.LinkEntry exactLink : exactLinks) {
                        BlockPos absLinkPos = BlueprintGeometry.rotateToAbsolute(
                            exactLink.relativePos(), newAnchor, rotation);
                        if (!pastedPositions.contains(absLinkPos)) continue;
                        Direction dstFace = BlueprintGeometry.rotateDirection(exactLink.face(), rotation);
                        FaceAddress dstKey = FaceAddress.of(absLinkPos, dstFace);
                        FaceConfigComposite dstCfg = mgr.getFaceConfig(dstKey);
                        if (dstCfg != null && !dstCfg.isDefault()
                            && dstCfg.canPlayerModify(player)
                            && dstCfg.faceConfig.getGroupKeys().contains(targetGroup.key())
                            && TransferUtils.hasLogisticsCapability(level, absLinkPos, dstFace)) {
                            LogisticsNode srcNode = new LogisticsNode(
                                GlobalPos.of(level.dimension(), absPos), rotatedFace);
                            LogisticsNode dstNode = new LogisticsNode(
                                GlobalPos.of(level.dimension(), absLinkPos), dstFace);
                            // 记录新增的链接（用于撤销）
                            if (!srcCfg.getLinkedNodes(targetGroup.key()).contains(dstNode)) {
                                linkSnapshots.add(new BlueprintUndoData.LinkSnapshot(
                                    srcNode, dstNode, targetGroup.key()));
                            }
                            mgr.addLink(targetGroup.key(), srcNode, dstNode);
                        }
                    }
                }
            }

            if (!player.isCreative() && !needed.isEmpty()
                && BlueprintUpgradeInventory.consume(player, needed) != 0) {
                throw new IllegalStateException("Blueprint upgrade inventory changed during paste");
            }

            // 在提交前按实际将要记录的键校验撤销快照大小，避免提交后才发现快照无法保存。
            Set<FaceAddress> projectedPostVersionKeys = new LinkedHashSet<>();
            for (BlueprintUndoData.FaceSnapshot snapshot : faceSnapshots) {
                projectedPostVersionKeys.add(FaceAddress.of(snapshot.pos(), snapshot.face()));
            }
            for (BlueprintUndoData.LinkSnapshot snapshot : linkSnapshots) {
                projectedPostVersionKeys.add(FaceAddress.of(snapshot.src()));
                projectedPostVersionKeys.add(FaceAddress.of(snapshot.dst()));
            }
            BlueprintUndoData.validateWorkItemCount(
                faceSnapshots.size(), containerSnapshots.size(), linkSnapshots.size(), groupSnapshots.size(),
                projectedPostVersionKeys.size(), containerSnapshots.size());
            transaction.commit();
        } catch (RuntimeException exception) {
            LOGGER.error("Blueprint paste transaction failed", exception);
            player.displayClientMessage(Component.translatable("msg.staticlogistics.blueprint.paste_failed")
                .withStyle(ChatFormatting.RED), true);
            return;
        }

        // 提交阶段已经完成版本递增与合并回调，此时记录的版本可立即用于撤销校验。
        Map<FaceAddress, Long> postVersions = new LinkedHashMap<>();
        for (BlueprintUndoData.FaceSnapshot snapshot : faceSnapshots) {
            FaceAddress key = FaceAddress.of(snapshot.pos(), snapshot.face());
            FaceConfigComposite current = mgr.getFaceConfig(key);
            if (current != null) postVersions.put(key, current.getVersion());
        }
        for (BlueprintUndoData.LinkSnapshot snapshot : linkSnapshots) {
            FaceAddress sourceKey = FaceAddress.of(snapshot.src());
            FaceAddress targetKey = FaceAddress.of(snapshot.dst());
            FaceConfigComposite source = mgr.getFaceConfig(sourceKey);
            FaceConfigComposite target = mgr.getFaceConfig(targetKey);
            if (source != null) postVersions.put(sourceKey, source.getVersion());
            if (target != null) postVersions.put(targetKey, target.getVersion());
        }
        Map<Long, CompoundTag> postContainerUpgrades = new LinkedHashMap<>();
        for (BlueprintUndoData.ContainerSnapshot snapshot : containerSnapshots) {
            ContainerConfig current = mgr.getContainerConfig(snapshot.pos());
            if (current != null) {
                postContainerUpgrades.put(snapshot.pos().asLong(),
                    current.getUpgrades().serializeNBT(level.registryAccess()));
            }
        }
        BlueprintUndoManager.get(level.getServer()).store(player.getUUID(),
            new BlueprintUndoData(level.dimension(), faceSnapshots, containerSnapshots, linkSnapshots,
                groupSnapshots, Map.copyOf(postVersions), Map.copyOf(postContainerUpgrades)));

        player.displayClientMessage(Component.translatable("msg.staticlogistics.blueprint.pasted", count, newAnchor.toShortString()).withStyle(ChatFormatting.GREEN), true);
        level.playSound(null, newAnchor, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    /**
     * 只统计后续确实会被非空蓝图 NBT 替换的升级槽。
     */
    private static Map<String, Integer> tallyOverwrittenUpgrades(
        LinkManager manager, BlueprintData data, BlockPos anchor, int rotation
    ) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (BlueprintData.BlockEntry entry : data.blocks()) {
            BlockPos pos = BlueprintGeometry.rotateToAbsolute(entry.relativePos(), anchor, rotation);
            if (!entry.containerUpgrades().isEmpty()) {
                ContainerConfig container = manager.getContainerConfig(pos);
                if (container != null) {
                    BlueprintUpgradeInventory.tallyInventory(result, container.getUpgrades());
                }
            }
            for (var faceEntry : entry.faces().entrySet()) {
                if (faceEntry.getValue().filterUpgrades().isEmpty()) continue;
                Direction face = BlueprintGeometry.rotateDirection(faceEntry.getKey(), rotation);
                FaceConfigComposite config = manager.getFaceConfig(FaceAddress.of(pos, face));
                if (config != null) {
                    BlueprintUpgradeInventory.tallyInventory(
                        result, config.filterConfig.getUpgrades());
                }
            }
        }
        return Map.copyOf(result);
    }

    /**
     * 撤销最近一次蓝图粘贴
     */
    public static void undoPaste(ServerLevel level, Player player) {
        BlueprintUndoData undo = BlueprintUndoManager.get(level.getServer()).peek(player.getUUID());
        if (undo == null) {
            player.displayClientMessage(
                Component.translatable("msg.staticlogistics.blueprint.no_undo").withStyle(ChatFormatting.RED), true);
            return;
        }

        LinkManager mgr = LinkManager.get(level);
        if (!level.dimension().equals(undo.dimension())) {
            player.displayClientMessage(Component.translatable("msg.staticlogistics.no_permission")
                .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        BlueprintUndoManager undoManager = BlueprintUndoManager.get(level.getServer());

        // 版本或升级快照不一致表示粘贴结果已被后续修改，撤销永远不再可安全执行。
        boolean stateUnchanged = undo.postVersions().entrySet().stream().allMatch(entry -> {
            FaceConfigComposite current = mgr.getFaceConfig(entry.getKey());
            return current != null && current.getVersion() == entry.getValue();
        })
            && undo.postContainerUpgrades().entrySet().stream().allMatch(entry -> {
            ContainerConfig current = mgr.getContainerConfig(BlockPos.of(entry.getKey()));
            return current != null
                && current.getUpgrades().serializeNBT(level.registryAccess()).equals(entry.getValue());
        });
        if (!stateUnchanged) {
            undoManager.clear(player.getUUID());
            player.displayClientMessage(Component.translatable("msg.staticlogistics.blueprint.undo_failed")
                .withStyle(ChatFormatting.RED), true);
            return;
        }

        boolean authorized = undo.faces().stream().allMatch(snapshot ->
            isUndoFaceValid(level, mgr, serverPlayer, snapshot.pos(), snapshot.face()))
            && undo.containers().stream().allMatch(snapshot ->
            isUndoContainerValid(level, mgr, serverPlayer, snapshot.pos()))
            && undo.links().stream().allMatch(snapshot ->
            snapshot.src().gPos().dimension().equals(level.dimension())
                && snapshot.dst().gPos().dimension().equals(level.dimension())
                && isUndoFaceValid(level, mgr, serverPlayer,
                snapshot.src().gPos().pos(), snapshot.src().face())
                && isUndoFaceValid(level, mgr, serverPlayer,
                snapshot.dst().gPos().pos(), snapshot.dst().face()));
        if (!authorized) {
            player.displayClientMessage(Component.translatable("msg.staticlogistics.no_permission")
                .withStyle(ChatFormatting.RED), true);
            return;
        }
        int restored = 0;

        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(level.getServer())) {
            for (BlueprintUndoData.ContainerSnapshot snapshot : undo.containers()) {
                ContainerConfig current = mgr.getContainerConfig(snapshot.pos());
                boolean existed = current != null;
                CompoundTag upgrades = current == null ? null
                    : current.getUpgrades().serializeNBT(level.registryAccess()).copy();
                transaction.onRollback(() -> mgr.restoreContainerSnapshot(
                    snapshot.pos(), existed, upgrades));
            }
            for (BlueprintUndoData.FaceSnapshot snapshot : undo.faces()) {
                transaction.captureState(new LogisticsNode(
                    GlobalPos.of(level.dimension(), snapshot.pos()), snapshot.face()));
            }

            // 移除粘贴新增的链接
            for (BlueprintUndoData.LinkSnapshot link : undo.links()) {
                mgr.removeLink(link.groupKey(), link.src(), link.dst());
            }

            // 移除粘贴新增的分组
            for (BlueprintUndoData.GroupSnapshot gs : undo.groups()) {
                LogisticsNode node = new LogisticsNode(GlobalPos.of(level.dimension(), gs.pos()), gs.face());
                mgr.removeNodeFromGroup(gs.groupKey(), node);
            }

            // 恢复面配置
            for (BlueprintUndoData.FaceSnapshot fs : undo.faces()) {
                if (fs.existed()) {
                    // 恢复原有配置
                    FaceConfigComposite cfg = mgr.getOrCreateFaceConfig(fs.pos(), fs.face());
                    LogisticsNode node = new LogisticsNode(
                        GlobalPos.of(level.dimension(), fs.pos()), fs.face());
                    mgr.restoreFaceSnapshot(node, fs.nbt());
                } else {
                    // 粘贴前不存在 → 删除
                    mgr.restoreFaceAbsence(new LogisticsNode(
                        GlobalPos.of(level.dimension(), fs.pos()), fs.face()));
                }
                restored++;
            }

            // 恢复容器配置
            for (BlueprintUndoData.ContainerSnapshot cs : undo.containers()) {
                if (cs.existed()) {
                    ContainerConfig cc = mgr.getOrCreateContainerConfig(cs.pos());
                    cc.getUpgrades().deserializeNBT(level.registryAccess(), cs.upgradesNbt());
                    cc.markDirty();
                } else {
                    mgr.restoreContainerSnapshot(cs.pos(), false, null);
                }
            }

            transaction.commit();
        } catch (RuntimeException exception) {
            LOGGER.error("Blueprint undo transaction failed", exception);
            player.displayClientMessage(Component.translatable("msg.staticlogistics.blueprint.undo_failed")
                .withStyle(ChatFormatting.RED), true);
            return;
        }

        undoManager.consume(player.getUUID());
        player.displayClientMessage(Component.translatable("msg.staticlogistics.blueprint.undone", restored).withStyle(ChatFormatting.YELLOW), true);
        level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.0f);
    }

    private static boolean canModifyPosition(LinkManager manager, BlockPos pos, Player player) {
        for (Direction face : Direction.values()) {
            FaceConfigComposite config = manager.getFaceConfig(FaceAddress.of(pos, face));
            if (config != null && !config.canPlayerModify(player)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isUndoFaceValid(ServerLevel level, LinkManager manager, ServerPlayer player,
                                           BlockPos pos, Direction face) {
        return isUndoContainerValid(level, manager, player, pos)
            && TransferUtils.hasLogisticsCapability(level, pos, face);
    }

    private static boolean isUndoContainerValid(ServerLevel level, LinkManager manager, ServerPlayer player,
                                                BlockPos pos) {
        return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
            && level.getBlockEntity(pos) != null
            && NodeInteractionRules.isWithinReach(player.getX(), player.getY(), player.getZ(), pos)
            && canModifyPosition(manager, pos, player);
    }

}
