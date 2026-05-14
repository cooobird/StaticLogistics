package com.coobird.staticlogistics.server.event;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.event.LogisticsNodeEvent;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import com.coobird.staticlogistics.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.network.c2s.*;
import com.coobird.staticlogistics.network.s2c.S2CSyncBulkFaceConfigPacket;
import com.coobird.staticlogistics.network.s2c.S2CSyncFaceConfigPacket;
import com.coobird.staticlogistics.registry.SLCommands;
import com.coobird.staticlogistics.registry.SLDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@EventBusSubscriber(modid = Staticlogistics.MODID)
public class SLEvents {

    @SubscribeEvent
    public static void onServerStarting(ServerAboutToStartEvent event) {
        GlobalLogisticsManager.get(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        GlobalLogisticsManager.release(event.getServer());
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(S2CSyncFaceConfigPacket.TYPE, S2CSyncFaceConfigPacket.STREAM_CODEC, S2CSyncFaceConfigPacket::handle);
        registrar.playToClient(S2CSyncBulkFaceConfigPacket.TYPE, S2CSyncBulkFaceConfigPacket.STREAM_CODEC, S2CSyncBulkFaceConfigPacket::handle);

        registrar.playToServer(C2SRemoveLinkPayload.TYPE, C2SRemoveLinkPayload.STREAM_CODEC, C2SRemoveLinkPayload::handle);
        registrar.playToServer(C2SConfigureFacePayload.TYPE, C2SConfigureFacePayload.STREAM_CODEC, C2SConfigureFacePayload::handle);
        registrar.playToServer(C2SUpdateToolSettingsPayload.TYPE, C2SUpdateToolSettingsPayload.STREAM_CODEC, C2SUpdateToolSettingsPayload::handle);
        registrar.playToServer(C2SGroupRenamePayload.TYPE, C2SGroupRenamePayload.STREAM_CODEC, C2SGroupRenamePayload::handle);
        registrar.playToServer(C2SUpdateFilterOnItemPayload.TYPE, C2SUpdateFilterOnItemPayload.STREAM_CODEC, C2SUpdateFilterOnItemPayload::handle);
        registrar.playToServer(C2SUpdateFilterOnHandPayload.TYPE, C2SUpdateFilterOnHandPayload.STREAM_CODEC, C2SUpdateFilterOnHandPayload::handle);
        registrar.playToServer(C2SOpenHandFilterPayload.TYPE, C2SOpenHandFilterPayload.STREAM_CODEC, C2SOpenHandFilterPayload::handle);
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
}