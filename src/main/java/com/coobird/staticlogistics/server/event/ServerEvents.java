package com.coobird.staticlogistics.server.event;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.event.LogisticsNodeEvent;
import com.coobird.staticlogistics.api.type.ToolMode;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import com.coobird.staticlogistics.integration.ModCompat;
import com.coobird.staticlogistics.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.network.c2s.*;
import com.coobird.staticlogistics.network.s2c.S2CConfigSyncPacket;
import com.coobird.staticlogistics.network.s2c.S2CRemoveBulkFaceConfigPacket;
import com.coobird.staticlogistics.network.s2c.S2CSyncBulkFaceConfigPacket;
import com.coobird.staticlogistics.network.s2c.S2CSyncFaceConfigPacket;
import com.coobird.staticlogistics.registry.SLCommands;
import com.coobird.staticlogistics.registry.SLDataComponents;
import com.coobird.staticlogistics.storage.LinkManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@EventBusSubscriber(modid = Staticlogistics.MODID)
public class ServerEvents {

    @SubscribeEvent
    public static void onServerStarting(ServerAboutToStartEvent event) {
        GlobalLogisticsManager.get(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        GlobalLogisticsManager.release(event.getServer());
        LinkManager.shutdownSaver();
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(S2CSyncFaceConfigPacket.TYPE, S2CSyncFaceConfigPacket.STREAM_CODEC, S2CSyncFaceConfigPacket::handle);
        registrar.playToClient(S2CSyncBulkFaceConfigPacket.TYPE, S2CSyncBulkFaceConfigPacket.STREAM_CODEC, S2CSyncBulkFaceConfigPacket::handle);
        registrar.playToClient(S2CConfigSyncPacket.TYPE, S2CConfigSyncPacket.STREAM_CODEC, S2CConfigSyncPacket::handle);
        registrar.playToClient(S2CRemoveBulkFaceConfigPacket.TYPE, S2CRemoveBulkFaceConfigPacket.STREAM_CODEC, S2CRemoveBulkFaceConfigPacket::handle);

        registrar.playToServer(C2SRemoveLinkPayload.TYPE, C2SRemoveLinkPayload.STREAM_CODEC, C2SRemoveLinkPayload::handle);
        registrar.playToServer(C2SConfigureFacePayload.TYPE, C2SConfigureFacePayload.STREAM_CODEC, C2SConfigureFacePayload::handle);
        registrar.playToServer(C2SUpdateToolSettingsPayload.TYPE, C2SUpdateToolSettingsPayload.STREAM_CODEC, C2SUpdateToolSettingsPayload::handle);
        registrar.playToServer(C2SGroupRenamePayload.TYPE, C2SGroupRenamePayload.STREAM_CODEC, C2SGroupRenamePayload::handle);
        registrar.playToServer(C2SUpdateFilterOnItemPayload.TYPE, C2SUpdateFilterOnItemPayload.STREAM_CODEC, C2SUpdateFilterOnItemPayload::handle);
        registrar.playToServer(C2SUpdateFilterOnHandPayload.TYPE, C2SUpdateFilterOnHandPayload.STREAM_CODEC, C2SUpdateFilterOnHandPayload::handle);
        registrar.playToServer(C2SOpenHandFilterPayload.TYPE, C2SOpenHandFilterPayload.STREAM_CODEC, C2SOpenHandFilterPayload::handle);
        registrar.playToServer(C2SUpdateBlueprintPreviewPayload.TYPE, C2SUpdateBlueprintPreviewPayload.STREAM_CODEC, C2SUpdateBlueprintPreviewPayload::handle);
        registrar.playToServer(C2SOpenContainerConfigPayload.TYPE, C2SOpenContainerConfigPayload.STREAM_CODEC, C2SOpenContainerConfigPayload::handle);
        registrar.playToServer(C2SOpenFaceConfigPayload.TYPE, C2SOpenFaceConfigPayload.STREAM_CODEC, C2SOpenFaceConfigPayload::handle);
        registrar.playToServer(C2SClearStoredNodesPayload.TYPE, C2SClearStoredNodesPayload.STREAM_CODEC, C2SClearStoredNodesPayload::handle);
        registrar.playToServer(C2SDeleteGroupPayload.TYPE, C2SDeleteGroupPayload.STREAM_CODEC, C2SDeleteGroupPayload::handle);
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

    // 扳手模式下不删除面配置：旋转方块只需改变朝向，不应影响物流配置。
    // 方块的清理由 SLLevelEvents.onBlockBreak / WrenchModeHandler.dismantle 各自处理。

    /**
     * 处理mek及其扩展的扳手模式
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof LinkConfiguratorItem item) {
            if (item.getSettings(stack).mode() != ToolMode.WRENCH) {
                Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
                if (ModCompat.isMekanismLoaded() &&
                    BuiltInRegistries.BLOCK.getKey(block).getNamespace().startsWith("mekanism") ||
                    BuiltInRegistries.BLOCK.getKey(block).getNamespace().endsWith("mekanism") ||
                    BuiltInRegistries.BLOCK.getKey(block).getNamespace().startsWith("mek")) {
                    if (event.isCanceled()) event.setCanceled(false);
                    event.setUseBlock(TriState.FALSE);
                    event.setUseItem(TriState.DEFAULT);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onGenericBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getLevel() instanceof Level level)) return;

        ItemStack stack = player.getMainHandItem();
        if (!stack.has(SLDataComponents.STORED_BE_NBT.get())) {
            stack = player.getOffhandItem();
        }
        if (!stack.has(SLDataComponents.STORED_BE_NBT.get())) return;

        CustomData customData = stack.get(SLDataComponents.STORED_BE_NBT.get());
        if (customData == null) return;

        CompoundTag savedBeTag = customData.copyTag();
        BlockPos pos = event.getPos();
        BlockEntity newBe = level.getBlockEntity(pos);

        if (newBe != null) {
            newBe.loadWithComponents(savedBeTag, level.registryAccess());
            if (newBe instanceof Container c) {
                for (int i = 0; i < c.getContainerSize(); i++) {
                    ItemStack item = c.getItem(i);
                    if (!item.isEmpty()) {
                        c.setItem(i, item.copy());
                    }
                }
            }
            newBe.setChanged();
            level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);

            if (!player.getAbilities().instabuild) {
                stack.remove(SLDataComponents.STORED_BE_NBT.get());
            }
        }
    }
}