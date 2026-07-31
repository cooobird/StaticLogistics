package com.coobird.staticlogistics.content.item;

import PortLib.extensions.net.minecraft.world.item.ItemStack.PortItemStackExtension;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.group.GroupService;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.logistics.node.*;
import com.coobird.staticlogistics.network.TeamPacketSync;
import com.coobird.staticlogistics.network.s2c.S2CTopologyUpdatePayload;
import com.coobird.staticlogistics.transfer.TransferUtils;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("try")
public class LinkOperationHelper {
    public static final String DEFAULT_GROUP_NAME = "1";

    public static void validateStoredNodes(ItemStack stack, ServerLevel level) {
        List<LogisticsNode> storedNodes = PortItemStackExtension.getData(stack, SLDataComponents.STORED_NODES.get());
        if (storedNodes == null || storedNodes.isEmpty()) return;
        List<LogisticsNode> validNodes = storedNodes.stream().filter(node -> {
            ServerLevel nodeLevel = level.getServer().getLevel(node.gPos().dimension());
            return nodeLevel != null && TransferUtils.hasLogisticsCapability(nodeLevel, node.gPos().pos(), node.face());
        }).toList();
        if (validNodes.size() != storedNodes.size()) {
            PortItemStackExtension.setData(stack, SLDataComponents.STORED_NODES.get(), validNodes);
            if (validNodes.isEmpty())
                PortItemStackExtension.removeData(stack, SLDataComponents.STORED_MODE.get());
        }
    }

    /**
     * 方块被移除时，清理所有在线玩家配置器中的无效存点
     */
    public static void cleanStoredNodesForPos(ServerLevel level, BlockPos pos) {
        for (ServerPlayer sp : level.getServer().getPlayerList().getPlayers()) {
            for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
                ItemStack stack = sp.getInventory().getItem(i);
                if (stack.getItem() instanceof LinkConfiguratorItem) validateStoredNodes(stack, level);
            }
        }
    }

    public static void addNode(ItemStack stack, GlobalPos gpos, Direction face, ToolMode mode, Player player, Level level) {
        if (!mode.isLinkMode()) return;
        if (level instanceof ServerLevel sl && !TransferUtils.hasLogisticsCapability(sl, gpos.pos(), face)) {
            player.displayClientMessage(Component.translatable("msg.staticlogistics.no_capability").withStyle(ChatFormatting.RED), true);
            return;
        }
        List<LogisticsNode> nodes = new ArrayList<>(PortItemStackExtension.getDataOrDefault(stack, SLDataComponents.STORED_NODES.get(), List.of()));
        LogisticsNode newNode = new LogisticsNode(gpos, face);
        if (nodes.contains(newNode)) {
            nodes.remove(newNode);
            player.displayClientMessage(Component.translatable("msg.staticlogistics.node_removed", nodes.size()).withStyle(ChatFormatting.RED), true);
            if (nodes.isEmpty()) {
                PortItemStackExtension.removeData(stack, SLDataComponents.STORED_MODE.get());
            }
        } else {
            if (nodes.size() >= SLDataComponents.MAX_STORED_NODES) {
                player.displayClientMessage(Component.translatable(
                        "msg.staticlogistics.stored_nodes_full", SLDataComponents.MAX_STORED_NODES)
                    .withStyle(ChatFormatting.RED), true);
                return;
            }
            // 第一个节点存入时记录所属玩家
            if (nodes.isEmpty()) {
                PortItemStackExtension.setData(stack, SLDataComponents.STORED_NODES_OWNER.get(), player.getStringUUID());
            }
            nodes.add(newNode);
            PortItemStackExtension.setData(stack, SLDataComponents.STORED_MODE.get(), mode.getId());
            player.displayClientMessage(Component.translatable("msg.staticlogistics.node_added", nodes.size()).withStyle(ChatFormatting.GREEN), true);
        }
        PortItemStackExtension.setData(stack, SLDataComponents.STORED_NODES.get(), nodes);
        // 同步到客户端以触发渲染
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.inventoryMenu.broadcastChanges();
        }
        level.playSound(null, gpos.pos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.8f, 1.5f);
    }

    public static void clearNodes(ItemStack stack, Player player, Level level) {
        List<LogisticsNode> nodes = PortItemStackExtension.getData(stack, SLDataComponents.STORED_NODES.get());
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        PortItemStackExtension.removeData(stack, SLDataComponents.STORED_NODES.get());
        PortItemStackExtension.removeData(stack, SLDataComponents.STORED_MODE.get());
        PortItemStackExtension.removeData(stack, SLDataComponents.STORED_NODES_OWNER.get());
        // 同步到客户端以触发渲染
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.inventoryMenu.broadcastChanges();
        }
        player.displayClientMessage(Component.translatable("msg.staticlogistics.selection_cleared").withStyle(ChatFormatting.YELLOW), true);
        level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 0.5f);
    }

    public static void executeBatchLink(ItemStack stack, String groupId, LinkConfiguratorItem.ToolSettings settings,
                                        BlockPos pos, Direction face, ServerLevel level, Player player) {
        // 校验存点人是否当前玩家，防止别人捡到工具冒用
        String storedOwner = PortItemStackExtension.getData(stack, SLDataComponents.STORED_NODES_OWNER.get());
        if (storedOwner != null && !storedOwner.isEmpty() && !storedOwner.equals(player.getStringUUID())) {
            player.displayClientMessage(Component.translatable("msg.staticlogistics.no_permission").withStyle(ChatFormatting.RED), true);
            return;
        }

        // 检查当前点击节点是否有物流能力
        if (!TransferUtils.hasLogisticsCapability(level, pos, face)) {
            player.displayClientMessage(Component.translatable("msg.staticlogistics.no_capability").withStyle(ChatFormatting.RED), true);
            return;
        }

        List<LogisticsNode> targets = settings.storedNodes().stream()
            .filter(n -> !n.isAt(level.dimension(), pos, face)).toList();
        if (targets.isEmpty()) {
            player.displayClientMessage(Component.translatable("msg.staticlogistics.self_link_error").withStyle(ChatFormatting.RED), true);
            return;
        }

        LogisticsNode currentNode = new LogisticsNode(GlobalPos.of(level.dimension(), pos), face);
        GroupRef group;
        try {
            group = resolveSelectedGroup(player, groupId, settings.groupKey());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            player.displayClientMessage(Component.translatable("msg.staticlogistics.no_permission")
                .withStyle(ChatFormatting.RED), true);
            return;
        }
        int linkedCount = 0;

        for (LogisticsNode srcNode : targets) {
            ServerLevel srcLevel = level.getServer().getLevel(srcNode.gPos().dimension());
            if (srcLevel == null) continue;

            LinkManager srcMgr = LinkManager.get(srcLevel);
            FaceConfigComposite srcCfg = srcMgr.getFaceConfig(FaceAddress.of(srcNode));
            if (srcCfg != null && !srcCfg.canPlayerModify(player)) continue;

            // 距离和跨维度升级属于传输能力，不作为创建拓扑的前置条件。
            if (performSingleLink(level, currentNode, srcNode, group, settings, player)) {
                linkedCount++;
            }
        }

        if (linkedCount > 0) {
            LinkConfiguratorSelection.select(player, group);
            player.displayClientMessage(Component.translatable(
                    "msg.staticlogistics.batch_linked_to_group", linkedCount, group.displayName())
                .withStyle(ChatFormatting.AQUA), true);
            level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.0f);
            // 根据配置决定是否自动清空存点
            if (SLConfig.shouldAutoCleanStoredNodes()) {
                clearNodes(stack, player, level);
            }
        }
    }

    public static boolean performSingleLink(ServerLevel level, LogisticsNode current, LogisticsNode stored, GroupRef group,
                                            LinkConfiguratorItem.ToolSettings settings, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)
            || current.equals(stored)
            || !NodeInteractionValidator.holdsConfigurator(serverPlayer)
            || !NodeInteractionValidator.isDirectInteractionTargetValid(
            serverPlayer, current.gPos().pos(), current.face())) return false;

        LinkManager currentMgr = LinkManager.get(level);
        FaceConfigComposite currentCfg = currentMgr.getFaceConfig(FaceAddress.of(current));
        if (currentCfg != null && !currentCfg.canPlayerModify(player)) return false;

        ServerLevel storedLevel = level.getServer().getLevel(stored.gPos().dimension());
        if (storedLevel == null
            || !storedLevel.getChunkSource().hasChunk(
            stored.gPos().pos().getX() >> 4, stored.gPos().pos().getZ() >> 4)
            || storedLevel.getBlockEntity(stored.gPos().pos()) == null
            || !TransferUtils.hasLogisticsCapability(
            storedLevel, stored.gPos().pos(), stored.face())) return false;

        LinkManager storedMgr = LinkManager.get(storedLevel);
        FaceConfigComposite storedCfg = storedMgr.getFaceConfig(FaceAddress.of(stored));
        if (storedCfg != null && !storedCfg.canPlayerModify(player)) return false;

        if (group == null || !GroupService.canModify(group.key().ownerId(), player)) return false;
        if (currentCfg != null && currentCfg.faceConfig.getOwner() != null
            && !currentCfg.faceConfig.getOwner().equals(group.key().ownerId())) return false;
        if (storedCfg != null && storedCfg.faceConfig.getOwner() != null
            && !storedCfg.faceConfig.getOwner().equals(group.key().ownerId())) return false;

        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(level.getServer())) {
            transaction.captureState(current);
            transaction.captureState(stored);
            if (currentCfg == null) {
                currentCfg = currentMgr.getOrCreateFaceConfig(current.gPos().pos(), current.face());
            }
            if (storedCfg == null) {
                storedCfg = storedMgr.getOrCreateFaceConfig(stored.gPos().pos(), stored.face());
            }
            boolean currentIsNew = currentCfg.faceConfig.getGroupKeys().isEmpty();
            boolean storedIsNew = storedCfg.faceConfig.getGroupKeys().isEmpty();
            try (FaceConfigComposite.BulkEdit currentEdit = currentCfg.beginBulkEdit();
                 FaceConfigComposite.BulkEdit storedEdit = storedCfg.beginBulkEdit()) {
                if (currentCfg.faceConfig.getOwner() == null) {
                    currentMgr.claimOwner(current, resolveOwnerProfile(level, group, player));
                }
                if (storedCfg.faceConfig.getOwner() == null) {
                    storedMgr.claimOwner(stored, resolveOwnerProfile(level, group, player));
                }
                currentMgr.addNodeToGroup(current, group);
                storedMgr.addNodeToGroup(stored, group);
                currentMgr.addLink(group.key(), current, stored);

                if (settings.storedMode() == ToolMode.LINK_AS_INSERT) {
                    enableOutput(currentCfg, settings.selectedTypeIds());
                    storedCfg.setGlobalInputEnabled(true);
                } else if (settings.storedMode() == ToolMode.LINK_AS_EXTRACT) {
                    enableOutput(storedCfg, settings.selectedTypeIds());
                    currentCfg.setGlobalInputEnabled(true);
                } else {
                    if (storedIsNew) enableOutput(storedCfg, settings.selectedTypeIds());
                    if (currentIsNew) currentCfg.setGlobalInputEnabled(true);
                }
            }
            transaction.commit();
        }

        TeamPacketSync.sendTopology(serverPlayer, group.key().ownerId(), List.of(
            S2CTopologyUpdatePayload.FaceUpdate.from(level, current, currentCfg),
            S2CTopologyUpdatePayload.FaceUpdate.from(storedLevel, stored, storedCfg)));

        return true;
    }

    private static GroupRef resolveSelectedGroup(Player player, String displayName,
                                                 GroupKey selectedKey) {
        if (selectedKey == null) {
            return PlayerGroupStore.get(player.getServer())
                .resolveOrCreateGroup(player.getUUID(), displayName);
        }
        GroupRef selected = PlayerGroupStore.get(player.getServer()).findGroup(selectedKey);
        if (selected != null) {
            if (!GroupService.canModify(selected.key().ownerId(), player)) {
                throw new IllegalArgumentException("Selected group is unavailable");
            }
            return selected;
        }
        if (!selectedKey.ownerId().equals(player.getUUID())) {
            throw new IllegalArgumentException("Selected group is unavailable");
        }
        return PlayerGroupStore.get(player.getServer())
            .resolveOrCreateGroup(player.getUUID(), displayName);
    }

    /**
     * 只有面首次获得输出角色时，才使用配置器中的类型初始化输出选择。
     */
    private static void enableOutput(FaceConfigComposite config,
                                     List<net.minecraft.resources.ResourceLocation> selectedTypeIds) {
        boolean wasEnabled = config.isGlobalOutputEnabled();
        config.setGlobalOutputEnabled(true);
        if (!wasEnabled) config.setSelectedTypeIds(selectedTypeIds);
    }

    private static GameProfile resolveOwnerProfile(ServerLevel level, GroupRef group, Player actor) {
        if (group.key().ownerId().equals(actor.getUUID())) return actor.getGameProfile();
        return level.getServer().getProfileCache().get(group.key().ownerId())
            .orElseGet(() -> new GameProfile(group.key().ownerId(), "Unknown"));
    }
}
