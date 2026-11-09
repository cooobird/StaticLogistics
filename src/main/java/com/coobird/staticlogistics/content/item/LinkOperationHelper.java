package com.coobird.staticlogistics.content.item;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.group.GlobalLogisticsManager;
import com.coobird.staticlogistics.logistics.group.GroupCommandService;
import com.coobird.staticlogistics.logistics.group.PermissionService;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.logistics.node.*;
import com.coobird.staticlogistics.logistics.selection.NodeSelectionPolicy;
import com.coobird.staticlogistics.network.TeamPacketSync;
import com.coobird.staticlogistics.network.s2c.S2CGroupDirectoryPayload;
import com.coobird.staticlogistics.network.s2c.S2CTopologyUpdatePayload;
import com.coobird.staticlogistics.transfer.TransferUtils;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.*;

public class LinkOperationHelper {
    private static final String FIRST_GROUP_NAME = "1";

    public static void validateStoredNodes(ItemStack stack, ServerLevel level) {
        List<LogisticsNode> storedNodes = stack.get(SLDataComponents.STORED_NODES.get());
        if (storedNodes == null || storedNodes.isEmpty()) return;
        List<LogisticsNode> validNodes = storedNodes.stream().filter(node -> {
            ServerLevel nodeLevel = level.getServer().getLevel(node.gPos().dimension());
            if (nodeLevel == null) return false;
            BlockPos pos = node.gPos().pos();
            if (!nodeLevel.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                /// 未加载不等于节点失效；保留存点，且不为校验强制加载区块。
                return true;
            }
            return TransferUtils.hasLogisticsCapability(nodeLevel, pos, node.face());
        }).toList();
        if (validNodes.size() != storedNodes.size()) {
            stack.set(SLDataComponents.STORED_NODES.get(), validNodes);
            if (validNodes.isEmpty()) stack.remove(SLDataComponents.STORED_MODE.get());
            String owner = stack.get(SLDataComponents.STORED_NODES_OWNER.get());
            if (owner != null) {
                try {
                    var player = level.getServer().getPlayerList().getPlayer(UUID.fromString(owner));
                    if (player != null) StoredSelectionSync.publish(player, stack);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    /// 方块被移除时，清理所有在线玩家配置器中的无效存点
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
        List<LogisticsNode> nodes = new ArrayList<>(stack.getOrDefault(SLDataComponents.STORED_NODES.get(), List.of()));
        int channel = level instanceof ServerLevel serverLevel ? LinkConfiguratorItem.channelForFace(serverLevel, gpos.pos(), face, LinkConfiguratorItem.selectedChannel(stack)) : 0;
        LogisticsNode newNode = new LogisticsNode(gpos, face, channel);
        if (nodes.contains(newNode)) {
            nodes.remove(newNode);
            player.displayClientMessage(Component.translatable("msg.staticlogistics.node_removed", nodes.size()).withStyle(ChatFormatting.RED), true);
            if (nodes.isEmpty()) {
                stack.remove(SLDataComponents.STORED_MODE.get());
            }
        } else {
            if (nodes.size() >= NodeSelectionPolicy.MAX_NODES) {
                player.displayClientMessage(Component.translatable(
                    "msg.staticlogistics.selection_limit", NodeSelectionPolicy.MAX_NODES), true);
                return;
            }
            /// 第一个节点存入时记录所属玩家
            if (nodes.isEmpty()) {
                stack.set(SLDataComponents.STORED_NODES_OWNER.get(), player.getStringUUID());
            }
            nodes.add(newNode);
            stack.set(SLDataComponents.STORED_MODE.get(), mode.getId());
            player.displayClientMessage(Component.translatable("msg.staticlogistics.node_added", nodes.size()).withStyle(ChatFormatting.GREEN), true);
        }
        stack.set(SLDataComponents.STORED_NODES.get(), List.copyOf(nodes));
        if (player instanceof ServerPlayer sp) StoredSelectionSync.publish(sp, stack);
        level.playSound(null, gpos.pos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.8f, 1.5f);
    }

    /// 批量选点只执行并集操作，避免重复使用组合键时意外取消整片区域。
    public static int addNodes(ItemStack stack, Collection<LogisticsNode> candidates, ToolMode mode, Player player, Level level) {
        if (!mode.isLinkMode() || candidates == null || candidates.isEmpty()) return 0;
        LinkedHashSet<LogisticsNode> nodes = new LinkedHashSet<>(
            stack.getOrDefault(SLDataComponents.STORED_NODES.get(), List.of()));
        int before = nodes.size();
        for (LogisticsNode node : candidates) {
            if (nodes.size() >= NodeSelectionPolicy.MAX_NODES) break;
            nodes.add(node);
        }
        if (nodes.size() == before) return 0;
        if (before == 0) stack.set(SLDataComponents.STORED_NODES_OWNER.get(), player.getStringUUID());
        stack.set(SLDataComponents.STORED_MODE.get(), mode.getId());
        stack.set(SLDataComponents.STORED_NODES.get(), List.copyOf(nodes));
        if (player instanceof ServerPlayer sp) StoredSelectionSync.publish(sp, stack);
        level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(),
            SoundSource.PLAYERS, 0.8F, 1.5F);
        return nodes.size() - before;
    }

    public static void clearNodes(ItemStack stack, Player player, Level level) {
        List<LogisticsNode> nodes = stack.get(SLDataComponents.STORED_NODES.get());
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        stack.remove(SLDataComponents.STORED_NODES.get());
        stack.remove(SLDataComponents.STORED_SELECTION_ID.get());
        stack.remove(SLDataComponents.STORED_MODE.get());
        stack.remove(SLDataComponents.STORED_NODES_OWNER.get());
        player.displayClientMessage(Component.translatable("msg.staticlogistics.selection_cleared").withStyle(ChatFormatting.YELLOW), true);
        level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 0.5f);
    }

    public static void executeBatchLink(ItemStack stack, LinkConfiguratorItem.ToolSettings settings,
                                        BlockPos pos, Direction face, ServerLevel level, Player player) {
        /// 校验存点人是否当前玩家，防止别人捡到工具冒用
        String storedOwner = stack.get(SLDataComponents.STORED_NODES_OWNER.get());
        if (storedOwner != null && !storedOwner.isEmpty() && !storedOwner.equals(player.getStringUUID())) {
            player.displayClientMessage(Component.translatable("msg.staticlogistics.no_permission").withStyle(ChatFormatting.RED), true);
            return;
        }

        /// 检查当前点击节点是否有物流能力
        if (!TransferUtils.hasLogisticsCapability(level, pos, face)) {
            player.displayClientMessage(Component.translatable("msg.staticlogistics.no_capability").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer)
            || !NodeInteractionValidator.isDirectInteractionTargetValid(
            serverPlayer, pos, face)) {
            return;
        }

        List<LogisticsNode> targets = settings.storedNodes().stream()
            .filter(n -> !(n.isAt(level.dimension(), pos, face) && n.channel() == LinkConfiguratorItem.channelForFace(level, pos, face, settings.channel()))).toList();
        if (targets.isEmpty()) {
            player.displayClientMessage(Component.translatable("msg.staticlogistics.self_link_error").withStyle(ChatFormatting.RED), true);
            return;
        }

        LogisticsNode currentNode = new LogisticsNode(GlobalPos.of(level.dimension(), pos), face, LinkConfiguratorItem.channelForFace(level, pos, face, settings.channel()));
        GroupResolution groupResolution;
        try {
            groupResolution = resolveSelectedGroup(level, player, stack, settings.groupKey());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            player.displayClientMessage(Component.translatable("msg.staticlogistics.select_group_to_link")
                .withStyle(ChatFormatting.RED), true);
            return;
        }
        GroupRef group = groupResolution.group();
        Set<LogisticsNode> linked = new LinkedHashSet<>();
        Set<LogisticsNode> changed = new LinkedHashSet<>();
        for (LogisticsNode source : targets) {
            try {
                if (performSingleLink(level, currentNode, source, group,
                    settings, serverPlayer, false, false)) {
                    linked.add(source);
                    changed.add(source);
                    changed.add(currentNode);
                }
            } catch (RuntimeException exception) {
                LogUtils.getLogger().error("Link creation failed for {}", source, exception);
            }
        }

        List<S2CTopologyUpdatePayload.FaceUpdate> updates = new ArrayList<>();
        for (LogisticsNode node : changed) {
            ServerLevel nodeLevel = level.getServer().getLevel(node.gPos().dimension());
            if (nodeLevel == null) continue;
            var config = LinkManager.get(nodeLevel).getEndpointConfig(EndpointAddress.of(node));
            if (config != null) {
                updates.add(S2CTopologyUpdatePayload.FaceUpdate.from(
                    nodeLevel, node, config));
            }
        }
        if (!updates.isEmpty()) {
            TeamPacketSync.sendTopology(
                serverPlayer, group.key().ownerId(), updates);
        }

        if (!linked.isEmpty()) {
            stack.set(SLDataComponents.SELECTED_GROUP.get(), group.displayName());
            stack.set(SLDataComponents.SELECTED_GROUP_KEY.get(), group.key());
            stack.remove(SLDataComponents.SELECTED_CONNECTION_KEY.get());
            if (SLConfig.shouldAutoCleanStoredNodes()) {
                List<LogisticsNode> remaining = stack.getOrDefault(
                        SLDataComponents.STORED_NODES.get(), List.<LogisticsNode>of())
                    .stream().filter(node -> !linked.contains(node)).toList();
                stack.set(SLDataComponents.STORED_NODES.get(), remaining);
                if (remaining.isEmpty()) {
                    stack.remove(SLDataComponents.STORED_MODE.get());
                    stack.remove(SLDataComponents.STORED_NODES_OWNER.get());
                }
                StoredSelectionSync.publish(serverPlayer, stack);
            }
            level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.0F);
        } else if (groupResolution.automaticallyCreated()) {
            rollbackAutomaticallyCreatedGroup(
                level, serverPlayer, stack, group);
        }
        serverPlayer.displayClientMessage(Component.translatable(
            "msg.staticlogistics.bulk_link_result",
            linked.size(), targets.size() - linked.size(), group.displayName()), true);
    }

    public static boolean performSingleLink(ServerLevel level, LogisticsNode current, LogisticsNode stored, GroupRef group,
                                            LinkConfiguratorItem.ToolSettings settings, Player player) {
        return performSingleLink(
            level, current, stored, group, settings, player, true, true);
    }

    private static boolean performSingleLink(ServerLevel level, LogisticsNode current, LogisticsNode stored, GroupRef group,
                                             LinkConfiguratorItem.ToolSettings settings, Player player,
                                             boolean sync, boolean requireDirectTarget) {
        /// 允许不选类型就链接（mask=0）→ 节点不会传输，方便后续插入过滤
        if (!(player instanceof ServerPlayer serverPlayer)
            || group == null
            || !NodeInteractionValidator.holdsConfigurator(serverPlayer)
            || !isCurrentTargetValid(
            level, serverPlayer, current, requireDirectTarget)
            || !PermissionService.getInstance().canModify(group.key().ownerId(), player)) {
            return false;
        }

        LinkManager currentMgr = LinkManager.get(level);
        EndpointConfig currentCfg = currentMgr.getEndpointConfig(EndpointAddress.of(current));
        if (currentCfg != null && !currentCfg.canPlayerModify(player)) return false;
        if (currentCfg != null && currentCfg.faceConfig.getOwner() != null
            && !currentCfg.faceConfig.getOwner().equals(group.key().ownerId())) return false;

        ServerLevel storedLevel = level.getServer().getLevel(stored.gPos().dimension());
        if (storedLevel == null) return false;
        BlockPos storedPos = stored.gPos().pos();
        if (!serverPlayer.mayBuild() || !storedLevel.mayInteract(serverPlayer, storedPos)) return false;
        boolean storedChunkLoaded = storedLevel.getChunkSource().hasChunk(
            storedPos.getX() >> 4, storedPos.getZ() >> 4);
        if (storedChunkLoaded && (storedLevel.getBlockEntity(storedPos) == null
            || !TransferUtils.hasLogisticsCapability(storedLevel, storedPos, stored.face()))) return false;

        LinkManager storedMgr = LinkManager.get(storedLevel);
        EndpointConfig storedCfg = storedMgr.getEndpointConfig(EndpointAddress.of(stored));
        if (storedCfg != null && !storedCfg.canPlayerModify(player)) return false;
        if (storedCfg != null && storedCfg.faceConfig.getOwner() != null
            && !storedCfg.faceConfig.getOwner().equals(group.key().ownerId())) return false;

        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(level.getServer())) {
            transaction.captureState(current);
            transaction.captureState(stored);

            if (currentCfg == null) {
                currentCfg = currentMgr.getOrCreateChannel(current);
            }
            if (storedCfg == null) {
                storedCfg = storedMgr.getOrCreateChannel(stored);
            }
            boolean currentIsNew = currentCfg.faceConfig.getGroupKeys().isEmpty();
            boolean currentHadGroup = currentCfg.faceConfig.getGroupKeys().contains(group.key());
            boolean storedIsNew = storedCfg.faceConfig.getGroupKeys().isEmpty();
            boolean storedHadGroup = storedCfg.faceConfig.getGroupKeys().contains(group.key());

            try (EndpointConfig.BulkEdit currentEdit = currentCfg.beginBulkEdit();
                 EndpointConfig.BulkEdit storedEdit = storedCfg.beginBulkEdit()) {
                if (currentCfg.faceConfig.getOwner() == null) {
                    currentMgr.claimOwner(current, resolveOwnerProfile(level, group, player));
                }
                currentMgr.addNodeToGroup(current, group);
                if (storedCfg.faceConfig.getOwner() == null) {
                    storedMgr.claimOwner(stored, resolveOwnerProfile(level, group, player));
                }
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

                if (!storedHadGroup) {
                    GlobalLogisticsManager.get(level.getServer())
                        .registerNode(group, stored, storedCfg.determineRole());
                }
                if (!currentHadGroup) {
                    GlobalLogisticsManager.get(level.getServer())
                        .registerNode(group, current, currentCfg.determineRole());
                }
            }
            transaction.commit();
        }

        if (sync) TeamPacketSync.sendTopology(serverPlayer, group.key().ownerId(), List.of(
            S2CTopologyUpdatePayload.FaceUpdate.from(level, current, currentCfg),
            S2CTopologyUpdatePayload.FaceUpdate.from(
                storedLevel, stored, storedCfg)));

        return true;
    }

    /// 玩家发起批量操作时已经完成一次精确视线验证。跨 tick 继续执行时只复核
    /// 服务端方块、能力和保护权限，不能再要求玩家持续对准原连接面，否则时间
    /// 预算切批后会随机截断剩余链接。
    private static boolean isCurrentTargetValid(
        ServerLevel level,
        ServerPlayer player,
        LogisticsNode current,
        boolean requireDirectTarget
    ) {
        BlockPos pos = current.gPos().pos();
        if (requireDirectTarget) {
            return NodeInteractionValidator.isDirectInteractionTargetValid(
                player, pos, current.face());
        }
        return player.serverLevel() == level
            && level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
            && level.getBlockEntity(pos) != null
            && NodeInteractionValidator.canMutateRemote(player, level, pos)
            && TransferUtils.hasLogisticsCapability(level, pos, current.face());
    }

    /// 只有面真正获得输出角色时，才使用配置器中的类型初始化输出选择。
    private static void enableOutput(EndpointConfig config,
                                     List<ResourceLocation> selectedTypeIds) {
        boolean wasOutputEnabled = config.isGlobalOutputEnabled();
        config.setGlobalOutputEnabled(true);
        if (!wasOutputEnabled) {
            config.setSelectedTypeIds(selectedTypeIds);
        }
    }

    private static GroupResolution resolveSelectedGroup(
        ServerLevel level, Player player, ItemStack stack, GroupKey selectedKey
    ) {
        PlayerGroupStore store = PlayerGroupStore.get(level.getServer());
        GroupRef selected = store.findGroup(selectedKey);
        if (selectedKey != null && selected != null) {
            if (!PermissionService.getInstance().canModify(selected.key().ownerId(), player)) {
                throw new IllegalArgumentException("Selected group is unavailable");
            }
            return new GroupResolution(selected, false);
        }
        if (!(player instanceof ServerPlayer serverPlayer)
            || !store.getGroupRefs(player.getUUID()).isEmpty()) {
            throw new IllegalStateException("Selected group is required");
        }
        GroupRef created = new GroupCommandService(level.getServer())
            .create(serverPlayer, FIRST_GROUP_NAME);
        stack.set(SLDataComponents.SELECTED_GROUP.get(), created.displayName());
        stack.set(SLDataComponents.SELECTED_GROUP_KEY.get(), created.key());
        stack.remove(SLDataComponents.SELECTED_CONNECTION_KEY.get());
        syncGroupDirectory(serverPlayer, store);
        return new GroupResolution(created, true);
    }

    private static void rollbackAutomaticallyCreatedGroup(
        ServerLevel level, Player player, ItemStack stack, GroupRef group
    ) {
        PlayerGroupStore store = PlayerGroupStore.get(level.getServer());
        if (!store.removeGroup(group.key())) return;
        if (group.key().equals(stack.get(SLDataComponents.SELECTED_GROUP_KEY.get()))) {
            stack.set(SLDataComponents.SELECTED_GROUP.get(), "");
            stack.remove(SLDataComponents.SELECTED_GROUP_KEY.get());
            stack.remove(SLDataComponents.SELECTED_CONNECTION_KEY.get());
        }
        if (player instanceof ServerPlayer serverPlayer) {
            syncGroupDirectory(serverPlayer, store);
        }
    }

    private static void syncGroupDirectory(ServerPlayer player, PlayerGroupStore store) {
        TeamPacketSync.send(player, player.getUUID(), new S2CGroupDirectoryPayload(
            player.getUUID(), store.getGroupRefs(player.getUUID())));
    }

    private record GroupResolution(GroupRef group, boolean automaticallyCreated) {
    }

    private static GameProfile resolveOwnerProfile(ServerLevel level, GroupRef group, Player actor) {
        if (group.key().ownerId().equals(actor.getUUID())) return actor.getGameProfile();
        return level.getServer().getProfileCache().get(group.key().ownerId())
            .orElseGet(() -> new GameProfile(group.key().ownerId(), "Unknown"));
    }
}
