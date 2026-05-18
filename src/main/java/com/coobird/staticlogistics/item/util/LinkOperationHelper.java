package com.coobird.staticlogistics.item.util;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import com.coobird.staticlogistics.core.service.GroupService;
import com.coobird.staticlogistics.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.network.s2c.S2CSyncFaceConfigPacket;
import com.coobird.staticlogistics.registry.SLDataComponents;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.ContainerConfig;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.handler.TransferUtils;
import com.coobird.staticlogistics.util.LogisticsCalculator;
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

public class LinkOperationHelper {
    public static void validateStoredNodes(ItemStack stack, ServerLevel level) {
        List<LogisticsNode> storedNodes = stack.get(SLDataComponents.STORED_NODES.get());
        if (storedNodes == null || storedNodes.isEmpty()) return;
        List<LogisticsNode> validNodes = storedNodes.stream().filter(node -> {
            ServerLevel nodeLevel = level.getServer().getLevel(node.gPos().dimension());
            return nodeLevel != null && TransferUtils.hasLogisticsCapability(nodeLevel, node.gPos().pos(), node.face());
        }).toList();
        if (validNodes.size() != storedNodes.size()) {
            stack.set(SLDataComponents.STORED_NODES.get(), validNodes);
            if (validNodes.isEmpty()) stack.remove(SLDataComponents.STORED_MODE.get());
        }
    }

    /**
     * 方块被移除时，清理所有在线玩家配置器中的无效存点
     */
    public static void cleanStoredNodesForPos(ServerLevel level, BlockPos pos) {
        if (!SLConfig.shouldAutoCleanStoredNodes()) return;
        for (ServerPlayer sp : level.getServer().getPlayerList().getPlayers()) {
            ItemStack stack = sp.getMainHandItem();
            if (stack.getItem() instanceof LinkConfiguratorItem) validateStoredNodes(stack, level);
            stack = sp.getOffhandItem();
            if (stack.getItem() instanceof LinkConfiguratorItem) validateStoredNodes(stack, level);
        }
    }

    public static void addNode(ItemStack stack, GlobalPos gpos, Direction face, ToolMode mode, Player player, Level level) {
        if (!mode.isLinkMode()) return;
        if (level instanceof ServerLevel sl && !TransferUtils.hasLogisticsCapability(sl, gpos.pos(), face)) {
            player.displayClientMessage(Component.translatable("msg.staticlogistics.no_capability").withStyle(ChatFormatting.RED), true);
            return;
        }
        List<LogisticsNode> nodes = new ArrayList<>(stack.getOrDefault(SLDataComponents.STORED_NODES.get(), List.of()));
        LogisticsNode newNode = new LogisticsNode(gpos, face);
        if (nodes.contains(newNode)) {
            nodes.remove(newNode);
            player.displayClientMessage(Component.translatable("msg.staticlogistics.node_removed", nodes.size()).withStyle(ChatFormatting.RED), true);
            if (nodes.isEmpty()) {
                stack.remove(SLDataComponents.STORED_MODE.get());
            }
        } else {
            // 第一个节点存入时自动分配新组 ID，并记录存点人 UUID（防冒用）
            if (nodes.isEmpty()) {
                String newId = GroupService.getNextGroupIdForPlayer(player);
                stack.set(SLDataComponents.SELECTED_GROUP.get(), newId);
                stack.set(SLDataComponents.STORED_NODES_OWNER.get(), player.getStringUUID());
            }
            nodes.add(newNode);
            stack.set(SLDataComponents.STORED_MODE.get(), mode.getId());
            player.displayClientMessage(Component.translatable("msg.staticlogistics.node_added", nodes.size()).withStyle(ChatFormatting.GREEN), true);
        }
        stack.set(SLDataComponents.STORED_NODES.get(), nodes);
        level.playSound(null, gpos.pos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.8f, 1.5f);
    }

    public static void clearNodes(ItemStack stack, Player player, Level level) {
        List<LogisticsNode> nodes = stack.get(SLDataComponents.STORED_NODES.get());
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        stack.remove(SLDataComponents.STORED_NODES.get());
        stack.remove(SLDataComponents.STORED_MODE.get());
        stack.remove(SLDataComponents.SELECTED_GROUP.get());
        stack.remove(SLDataComponents.STORED_NODES_OWNER.get());
        player.displayClientMessage(Component.translatable("msg.staticlogistics.selection_cleared").withStyle(ChatFormatting.YELLOW), true);
        level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 0.5f);
    }

    public static void executeBatchLink(ItemStack stack, String groupId, LinkConfiguratorItem.ToolSettings settings,
                                        BlockPos pos, Direction face, ServerLevel level, Player player) {
        // 校验存点人是否当前玩家，防止别人捡到工具冒用
        String storedOwner = stack.get(SLDataComponents.STORED_NODES_OWNER.get());
        if (storedOwner != null && !storedOwner.isEmpty() && !storedOwner.equals(player.getStringUUID())) {
            player.displayClientMessage(Component.translatable("msg.staticlogistics.no_permission").withStyle(ChatFormatting.RED), true);
            return;
        }

        if (settings.typeMask() == 0) {
            player.displayClientMessage(Component.translatable("msg.staticlogistics.no_types_selected")
                .withStyle(ChatFormatting.RED), true);
            return;
        }

        List<LogisticsNode> targets = settings.storedNodes().stream()
            .filter(n -> !n.isAt(level.dimension(), pos, face)).toList();
        if (targets.isEmpty()) {
            player.displayClientMessage(Component.translatable("msg.staticlogistics.self_link_error").withStyle(ChatFormatting.RED), true);
            return;
        }

        LogisticsNode currentNode = new LogisticsNode(GlobalPos.of(level.dimension(), pos), face);
        int linkedCount = 0;

        for (LogisticsNode srcNode : targets) {
            ServerLevel srcLevel = level.getServer().getLevel(srcNode.gPos().dimension());
            if (srcLevel == null) continue;

            LinkManager srcMgr = LinkManager.get(srcLevel);
            FaceConfigComposite srcCfg = srcMgr.getFaceConfig(LinkManager.posToKey(srcNode.gPos().pos(), srcNode.face()));
            if (srcCfg != null && !srcCfg.canPlayerAccess(player)) continue;

            ContainerConfig senderContainer = srcMgr.getContainerConfig(srcNode.gPos().pos());
            if (senderContainer == null) {
                senderContainer = srcMgr.getOrCreateContainerConfig(srcNode.gPos().pos());
            }

            boolean sameDim = srcNode.isInSameDimension(currentNode);
            if (!sameDim && !LogisticsCalculator.isDimensionEffective(senderContainer)) {
                player.displayClientMessage(Component.translatable("msg.staticlogistics.no_dimension_upgrade").withStyle(ChatFormatting.RED), true);
                continue;
            }
            if (sameDim && !LogisticsCalculator.isWithinRange(srcNode.gPos().pos(), currentNode.gPos().pos(), senderContainer)) {
                double maxDist = LogisticsCalculator.getMaxTransferDistance(senderContainer);
                player.displayClientMessage(Component.translatable("msg.staticlogistics.out_of_range", (int) maxDist).withStyle(ChatFormatting.RED), true);
                continue;
            }

            if (performSingleLink(level, currentNode, srcNode, groupId, settings, player)) {
                linkedCount++;
            }
        }

        if (linkedCount > 0) {
            stack.set(SLDataComponents.SELECTED_GROUP.get(), groupId);
            player.displayClientMessage(Component.translatable("msg.staticlogistics.batch_linked_to_group", linkedCount, groupId).withStyle(ChatFormatting.AQUA), true);
            level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
    }

    public static boolean performSingleLink(ServerLevel level, LogisticsNode current, LogisticsNode stored, String groupId,
                                            LinkConfiguratorItem.ToolSettings settings, Player player) {
        if (settings.typeMask() == 0) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("msg.staticlogistics.no_types_selected")
                    .withStyle(ChatFormatting.RED), true);
            }
            return false;
        }

        LinkManager currentMgr = LinkManager.get(level);
        FaceConfigComposite currentCfg = currentMgr.getOrCreateFaceConfig(current.gPos().pos(), current.face());
        currentCfg.faceConfig.setGroupId(groupId);
        currentCfg.faceConfig.setOwner(player.getUUID(), player.getGameProfile().getName());
        currentCfg.setSelectedTypesMask(settings.typeMask());

        ServerLevel storedLevel = level.getServer().getLevel(stored.gPos().dimension());
        if (storedLevel == null) return false;

        LinkManager storedMgr = LinkManager.get(storedLevel);
        FaceConfigComposite storedCfg = storedMgr.getOrCreateFaceConfig(stored.gPos().pos(), stored.face());
        storedCfg.faceConfig.setGroupId(groupId);
        storedCfg.faceConfig.setOwner(player.getUUID(), player.getGameProfile().getName());
        storedCfg.setSelectedTypesMask(settings.typeMask());

        int defaultChannel = 1;
        if (settings.storedMode() == ToolMode.LINK_AS_INSERT) {
            currentCfg.linkConfig.setInputChannel(defaultChannel);
            storedCfg.linkConfig.setOutputChannel(defaultChannel);
        } else {
            currentCfg.linkConfig.setOutputChannel(defaultChannel);
            storedCfg.linkConfig.setInputChannel(defaultChannel);
        }

        currentCfg.addLinkedNode(stored);
        storedCfg.addLinkedNode(current);

        GlobalLogisticsManager.get(level.getServer()).addReverseLink(current.toKey(), stored.toKey());
        GlobalLogisticsManager.get(level.getServer()).addReverseLink(stored.toKey(), current.toKey());

        if (settings.storedMode() == ToolMode.LINK_AS_INSERT) {
            currentCfg.setGlobalOutputEnabled(true);
            storedCfg.setGlobalInputEnabled(true);
        } else if (settings.storedMode() == ToolMode.LINK_AS_EXTRACT) {
            storedCfg.setGlobalOutputEnabled(true);
            currentCfg.setGlobalInputEnabled(true);
        }

        GlobalLogisticsManager.get(level.getServer()).registerNode(groupId, stored, storedCfg.determineRole());
        GlobalLogisticsManager.get(level.getServer()).registerNode(groupId, current, currentCfg.determineRole());

        currentCfg.markDirty();
        storedCfg.markDirty();

        currentMgr.syncConfigToClients(current.gPos().pos());
        storedMgr.syncConfigToClients(stored.gPos().pos());

        currentMgr.activateNode(current.toKey(), current.gPos().pos(), current.face(), currentCfg);
        storedMgr.activateNode(stored.toKey(), stored.gPos().pos(), stored.face(), storedCfg);

        if (player instanceof ServerPlayer serverPlayer) {
            S2CSyncFaceConfigPacket currentPacket = new S2CSyncFaceConfigPacket(current.gPos(), current.face(), currentCfg);
            GroupService.syncToTeamMembers(serverPlayer, currentPacket);
            S2CSyncFaceConfigPacket storedPacket = new S2CSyncFaceConfigPacket(stored.gPos(), stored.face(), storedCfg);
            GroupService.syncToTeamMembers(serverPlayer, storedPacket);
        }

        return true;
    }
}
