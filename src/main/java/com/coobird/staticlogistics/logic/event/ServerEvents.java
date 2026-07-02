package com.coobird.staticlogistics.logic.event;

import PortLib.extensions.net.minecraft.world.item.ItemStack.PortItemStackExtension;
import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.event.LogisticsNodeEvent;
import com.coobird.staticlogistics.integration.ModCompat;
import com.coobird.staticlogistics.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.logic.GlobalLogisticsManager;
import com.coobird.staticlogistics.logic.ToolMode;
import com.coobird.staticlogistics.registry.SLCommands;
import com.coobird.staticlogistics.registry.SLDataComponents;
import com.coobird.staticlogistics.storage.link.LinkManager;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = StaticLogistics.MODID)
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
        List<LogisticsNode> storedNodes = PortItemStackExtension.getData(stack, SLDataComponents.STORED_NODES.get());
        if (storedNodes == null || storedNodes.isEmpty()) return;

        List<LogisticsNode> updatedNodes = storedNodes.stream()
            .filter(node -> !removedNodes.contains(node))
            .collect(Collectors.toList());

        if (updatedNodes.size() == storedNodes.size()) return;

        PortItemStackExtension.setData(stack, SLDataComponents.STORED_NODES.get(), updatedNodes);
        if (updatedNodes.isEmpty())
            PortItemStackExtension.removeData(stack, SLDataComponents.STORED_MODE.get());

        player.displayClientMessage(
            Component.translatable("msg.staticlogistics.tool_nodes_cleaned",
                storedNodes.size() - updatedNodes.size()).withStyle(ChatFormatting.GRAY),
            true
        );
    }

    /**
     * 处理mek及其扩展的扳手模式
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof LinkConfiguratorItem item) {
            if (item.getSettings(stack).mode() != ToolMode.WRENCH) {
                Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
                if (ModCompat.isMekanismLoaded() && (
                    BuiltInRegistries.BLOCK.getKey(block).getNamespace().startsWith("mekanism") ||
                    BuiltInRegistries.BLOCK.getKey(block).getNamespace().endsWith("mekanism") ||
                    BuiltInRegistries.BLOCK.getKey(block).getNamespace().startsWith("mek"))) {
                    if (event.isCanceled()) event.setCanceled(false);
                    event.setUseBlock(Event.Result.DENY);
                    event.setUseItem(Event.Result.ALLOW);
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
        if (!PortItemStackExtension.hasData(stack, SLDataComponents.STORED_BE_NBT.get())) {
            stack = player.getOffhandItem();
        }
        if (!PortItemStackExtension.hasData(stack, SLDataComponents.STORED_BE_NBT.get()))
            return;

        CompoundTag customData = PortItemStackExtension.getData(stack, SLDataComponents.STORED_BE_NBT.get());
        if (customData == null) return;

        CompoundTag savedBeTag = customData != null ? customData.copy() : new CompoundTag();
        BlockPos pos = event.getPos();
        BlockEntity newBe = level.getBlockEntity(pos);

        if (newBe != null) {
            newBe.load(savedBeTag);
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
                PortItemStackExtension.removeData(stack, SLDataComponents.STORED_BE_NBT.get());
            }
        }
    }
}