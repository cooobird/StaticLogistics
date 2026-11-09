package com.coobird.staticlogistics.content.event;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.event.LogisticsNodeEvent;
import com.coobird.staticlogistics.command.SLCommands;
import com.coobird.staticlogistics.content.item.BlueprintModeHandler;
import com.coobird.staticlogistics.content.item.BulkSelectionInteractionGuard;
import com.coobird.staticlogistics.content.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.content.item.ToolMode;
import com.coobird.staticlogistics.integration.ModCompat;
import com.coobird.staticlogistics.integration.ftb.FTBEventHandlers;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.blueprint.BlueprintUndoManager;
import com.coobird.staticlogistics.logistics.group.*;
import com.coobird.staticlogistics.logistics.node.EndpointAddress;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import com.coobird.staticlogistics.network.c2s.*;
import com.coobird.staticlogistics.network.s2c.*;
import com.coobird.staticlogistics.transfer.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@EventBusSubscriber(modid = StaticLogistics.MODID)
public class ServerEvents {

    @SubscribeEvent
    public static void onServerStarting(ServerAboutToStartEvent event) {
        TransferRegistries.freeze();
        DistributionStrategyRegistry.freeze();
        TransferFailureReason.freeze();
        GlobalLogisticsManager.get(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        GroupDirectoryReconciler.reconcile(event.getServer());
        LogisticsOperationStore.get(event.getServer()).directoryDeletionOwners()
            .forEach(owner -> new ConnectionCommandService(event.getServer())
                .resumePendingDirectoryDeletion(owner));
        LogisticsOperationStore.get(event.getServer()).groupDeletions()
            .forEach(group -> new ConnectionCommandService(event.getServer())
                .resumePendingGroupDeletion(group));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        if (ModList.get().isLoaded("ftbteams")) FTBEventHandlers.clearPending();
        BlueprintUndoManager.release(event.getServer());
        LinkManager.release(event.getServer());
        TransferLogManager.release(event.getServer());
        LogisticsTicker.release(event.getServer());
        ConnectionCommandService.release(event.getServer());
        GroupCommandService.release(event.getServer());
        BulkSelectionInteractionGuard.release(event.getServer());
        GlobalLogisticsManager.release(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        MinecraftServer server = event.getEntity().getServer();
        if (server != null) {
            BlueprintUndoManager.get(server).clear(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("58");
        registrar.playToServer(C2SBeginBlueprintPlacementPayload.TYPE, C2SBeginBlueprintPlacementPayload.STREAM_CODEC, C2SBeginBlueprintPlacementPayload::handle);
        registrar.playToServer(C2SObserveTransferRatesPayload.TYPE, C2SObserveTransferRatesPayload.STREAM_CODEC, C2SObserveTransferRatesPayload::handle);
        registrar.playToClient(S2CTransferRatesPayload.TYPE, S2CTransferRatesPayload.STREAM_CODEC, S2CTransferRatesPayload::handle);
        registrar.playToClient(S2CConfiguratorReadyPayload.TYPE, S2CConfiguratorReadyPayload.STREAM_CODEC, S2CConfiguratorReadyPayload::handle);
        registrar.playToServer(C2SManageConnectionsPayload.TYPE, C2SManageConnectionsPayload.STREAM_CODEC, C2SManageConnectionsPayload::handle);
        registrar.playToServer(C2SManageChannelPayload.TYPE, C2SManageChannelPayload.STREAM_CODEC, C2SManageChannelPayload::handle);
        registrar.playToServer(C2SCreateChannelPayload.TYPE, C2SCreateChannelPayload.STREAM_CODEC, C2SCreateChannelPayload::handle);
        registrar.playToClient(S2CChannelCreatedPayload.TYPE, S2CChannelCreatedPayload.STREAM_CODEC, S2CChannelCreatedPayload::handle);
        registrar.playToServer(C2SGroupHierarchyPayload.TYPE, C2SGroupHierarchyPayload.STREAM_CODEC, C2SGroupHierarchyPayload::handle);
        registrar.playToClient(S2CGroupHierarchyPayload.TYPE, S2CGroupHierarchyPayload.STREAM_CODEC, S2CGroupHierarchyPayload::handle);
        registrar.playToClient(S2CTopologyUpdatePayload.TYPE, S2CTopologyUpdatePayload.STREAM_CODEC, S2CTopologyUpdatePayload::handle);
        registrar.playToClient(S2CConfigSyncPayload.TYPE, S2CConfigSyncPayload.STREAM_CODEC, S2CConfigSyncPayload::handle);
        registrar.playToClient(S2CStoredSelectionPage.TYPE, S2CStoredSelectionPage.STREAM_CODEC, S2CStoredSelectionPage::handle);
        registrar.playToClient(S2CPresetPreviewPayload.TYPE, S2CPresetPreviewPayload.STREAM_CODEC, S2CPresetPreviewPayload::handle);
        registrar.playToClient(S2CRemoveFaceTopologyPayload.TYPE, S2CRemoveFaceTopologyPayload.STREAM_CODEC, S2CRemoveFaceTopologyPayload::handle);
        registrar.playToClient(S2CGroupDirectoryPayload.TYPE, S2CGroupDirectoryPayload.STREAM_CODEC, S2CGroupDirectoryPayload::handle);
        registrar.playToClient(S2CAccessSnapshotPayload.TYPE, S2CAccessSnapshotPayload.STREAM_CODEC, S2CAccessSnapshotPayload::handle);
        registrar.playToServer(C2SRequestAccessSnapshotPayload.TYPE, C2SRequestAccessSnapshotPayload.STREAM_CODEC, C2SRequestAccessSnapshotPayload::handle);
        registrar.playToClient(S2CSelectLinkEndpointPayload.TYPE, S2CSelectLinkEndpointPayload.STREAM_CODEC, S2CSelectLinkEndpointPayload::handle);
        registrar.playToClient(S2CClearLinkEndpointPayload.TYPE, S2CClearLinkEndpointPayload.STREAM_CODEC, S2CClearLinkEndpointPayload::handle);
        registrar.playToServer(C2SConfigureFacePayload.TYPE, C2SConfigureFacePayload.STREAM_CODEC, C2SConfigureFacePayload::handle);
        registrar.playToServer(C2SBulkSelectNodesPayload.TYPE, C2SBulkSelectNodesPayload.STREAM_CODEC, C2SBulkSelectNodesPayload::handle);
        registrar.playToServer(C2SPresetPreviewPayload.TYPE, C2SPresetPreviewPayload.STREAM_CODEC, C2SPresetPreviewPayload::handle);
        registrar.playToServer(C2SOpenNodeFilterPayload.TYPE, C2SOpenNodeFilterPayload.STREAM_CODEC, C2SOpenNodeFilterPayload::handle);
        registrar.playToServer(C2SReturnToLinkConfiguratorPayload.TYPE, C2SReturnToLinkConfiguratorPayload.STREAM_CODEC, C2SReturnToLinkConfiguratorPayload::handle);
        registrar.playToClient(S2CToolModeResultPayload.TYPE, S2CToolModeResultPayload.STREAM_CODEC, S2CToolModeResultPayload::handle);
        registrar.playToServer(C2SUpdateToolModePayload.TYPE, C2SUpdateToolModePayload.STREAM_CODEC, C2SUpdateToolModePayload::handle);
        registrar.playToServer(C2SUpdateToolTypesPayload.TYPE, C2SUpdateToolTypesPayload.STREAM_CODEC, C2SUpdateToolTypesPayload::handle);
        registrar.playToServer(C2SUpdateToolGroupPayload.TYPE, C2SUpdateToolGroupPayload.STREAM_CODEC, C2SUpdateToolGroupPayload::handle);
        registrar.playToServer(C2SUpdateToolConnectionPayload.TYPE, C2SUpdateToolConnectionPayload.STREAM_CODEC, C2SUpdateToolConnectionPayload::handle);
        registrar.playToServer(C2SGroupRenamePayload.TYPE, C2SGroupRenamePayload.STREAM_CODEC, C2SGroupRenamePayload::handle);
        registrar.playToServer(C2SUpdateFilterOnItemPayload.TYPE, C2SUpdateFilterOnItemPayload.STREAM_CODEC, C2SUpdateFilterOnItemPayload::handle);
        registrar.playToServer(C2SUpdateFilterOnHandPayload.TYPE, C2SUpdateFilterOnHandPayload.STREAM_CODEC, C2SUpdateFilterOnHandPayload::handle);
        registrar.playToServer(C2SOpenHandFilterPayload.TYPE, C2SOpenHandFilterPayload.STREAM_CODEC, C2SOpenHandFilterPayload::handle);
        registrar.playToServer(C2SUpdateBlueprintPreviewPayload.TYPE, C2SUpdateBlueprintPreviewPayload.STREAM_CODEC, C2SUpdateBlueprintPreviewPayload::handle);
        registrar.playToServer(C2SOpenLinkEndpointPayload.TYPE, C2SOpenLinkEndpointPayload.STREAM_CODEC, C2SOpenLinkEndpointPayload::handle);
        registrar.playToServer(C2SClearLinkEndpointPayload.TYPE, C2SClearLinkEndpointPayload.STREAM_CODEC, C2SClearLinkEndpointPayload::handle);
        registrar.playToServer(C2SClearStoredNodesPayload.TYPE, C2SClearStoredNodesPayload.STREAM_CODEC, C2SClearStoredNodesPayload::handle);
        registrar.playToServer(C2SDeleteGroupPayload.TYPE, C2SDeleteGroupPayload.STREAM_CODEC, C2SDeleteGroupPayload::handle);
        registrar.playToServer(C2SSelectBlueprintGroupPayload.TYPE, C2SSelectBlueprintGroupPayload.STREAM_CODEC, C2SSelectBlueprintGroupPayload::handle);
        registrar.playToServer(C2SCopyBlueprintSelectionPayload.TYPE, C2SCopyBlueprintSelectionPayload.STREAM_CODEC, C2SCopyBlueprintSelectionPayload::handle);
        registrar.playToClient(S2CBlueprintCopyResultPayload.TYPE, S2CBlueprintCopyResultPayload.STREAM_CODEC, S2CBlueprintCopyResultPayload::handle);
        registrar.playToServer(C2SBlueprintUndoPayload.TYPE, C2SBlueprintUndoPayload.STREAM_CODEC, C2SBlueprintUndoPayload::handle);
        registrar.playToServer(C2SCreateEmptyGroupPayload.TYPE, C2SCreateEmptyGroupPayload.STREAM_CODEC, C2SCreateEmptyGroupPayload::handle);
    }

    @SubscribeEvent
    public static void command(RegisterCommandsEvent event) {
        SLCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onNodeChanged(LogisticsNodeEvent event) {
        if (event.getAffectedEntries().isEmpty()) return;

        LogisticsNodeEvent.NodeEntry firstEntry = event.getAffectedEntries().iterator().next();
        ServerLevel serverLevel = event.getServer().getLevel(firstEntry.node().gPos().dimension());

        if (serverLevel != null) {
            GlobalLogisticsManager.get(event.getServer()).handleNodeEvent(event, serverLevel);
        }
    }

    @SubscribeEvent
    public static void onNodeRemovedCleanup(LogisticsNodeEvent event) {
        if (event.getType() != LogisticsNodeEvent.ChangeType.REMOVED) return;

        Set<LogisticsNode> removedNodes = event.getAffectedEntries().stream()
            .map(LogisticsNodeEvent.NodeEntry::node)
            .filter(node -> {
                ServerLevel level = event.getServer().getLevel(node.gPos().dimension());
                return level == null
                    || LinkManager.get(level).getEndpointConfig(EndpointAddress.of(node)) == null;
            })
            .collect(Collectors.toSet());

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                cleanToolStoredNodes(player.getInventory().getItem(i), removedNodes, player);
            }
        }
    }

    private static void cleanToolStoredNodes(ItemStack stack, Set<LogisticsNode> removedNodes, ServerPlayer player) {
        if (!(stack.getItem() instanceof LinkConfiguratorItem)) return;
        List<LogisticsNode> storedNodes = stack.get(SLDataComponents.STORED_NODES.get());
        if (storedNodes == null || storedNodes.isEmpty()) return;

        List<LogisticsNode> updatedNodes = storedNodes.stream()
            .filter(node -> !removedNodes.contains(node))
            .collect(Collectors.toList());

        if (updatedNodes.size() == storedNodes.size()) return;

        stack.set(SLDataComponents.STORED_NODES.get(), updatedNodes);
        if (updatedNodes.isEmpty()) stack.remove(SLDataComponents.STORED_MODE.get());

        player.displayClientMessage(
            Component.translatable("msg.staticlogistics.tool_nodes_cleaned",
                storedNodes.size() - updatedNodes.size()).withStyle(ChatFormatting.GRAY),
            true
        );
    }

    /// 处理mek及其扩展的扳手模式
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof LinkConfiguratorItem item) {
            if (BlueprintModeHandler.isActive(stack) || item.getSettings(stack).mode() != ToolMode.WRENCH) {
                /// 选点和移除模式优先使用工具，避免普通右键先打开箱子或机器界面。
                event.setUseBlock(TriState.FALSE);
                Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
                if (ModCompat.isMekanismLoaded() && (
                    BuiltInRegistries.BLOCK.getKey(block).getNamespace().startsWith("mekanism") ||
                        BuiltInRegistries.BLOCK.getKey(block).getNamespace().endsWith("mekanism") ||
                        BuiltInRegistries.BLOCK.getKey(block).getNamespace().startsWith("mek"))) {
                    if (event.isCanceled()) event.setCanceled(false);
                    event.setUseBlock(TriState.FALSE);
                    event.setUseItem(TriState.DEFAULT);
                }
            }
        }
    }


    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBulkSelectionRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
            || !(event.getItemStack().getItem() instanceof LinkConfiguratorItem)
            || BlueprintModeHandler.isActive(event.getItemStack())
            || !BulkSelectionInteractionGuard.matches(
            player, event.getPos(), event.getFace())) return;
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

}
