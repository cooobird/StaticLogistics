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
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event.Result;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

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
                event.setUseBlock(Result.DENY);
                Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
                if (ModCompat.isMekanismLoaded() && (
                    BuiltInRegistries.BLOCK.getKey(block).getNamespace().startsWith("mekanism") ||
                        BuiltInRegistries.BLOCK.getKey(block).getNamespace().endsWith("mekanism") ||
                        BuiltInRegistries.BLOCK.getKey(block).getNamespace().startsWith("mek"))) {
                    if (event.isCanceled()) event.setCanceled(false);
                    event.setUseBlock(Result.DENY);
                    event.setUseItem(Result.DEFAULT);
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
        event.setUseBlock(Result.DENY);
        event.setUseItem(Result.DENY);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

}
