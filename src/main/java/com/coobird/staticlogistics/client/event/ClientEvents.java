package com.coobird.staticlogistics.client.event;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.client.data.NetworkPreviewLayoutStore;
import com.coobird.staticlogistics.client.gui.component.ToolModeFeedback;
import com.coobird.staticlogistics.client.gui.screen.BlueprintGroupScreen;
import com.coobird.staticlogistics.client.gui.screen.FilterConfiguratorScreen;
import com.coobird.staticlogistics.client.gui.screen.HandFilterScreen;
import com.coobird.staticlogistics.client.gui.screen.LinkConfiguratorScreen;
import com.coobird.staticlogistics.client.key.SLKeyMappings;
import com.coobird.staticlogistics.content.item.*;
import com.coobird.staticlogistics.content.registry.SLMenuTypes;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.network.c2s.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = StaticLogistics.MODID, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ClientLinkData.INSTANCE.invalidate();
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientLinkData.INSTANCE.invalidate();
        NetworkPreviewLayoutStore.INSTANCE.closeSession();
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        LinkConfiguratorClientHooks.installBulkSelectionKey(() -> SLKeyMappings.isKeyDown(SLKeyMappings.BULK_NODE_SELECTION));
        event.register(SLKeyMappings.BLUEPRINT_PREVIEW_MOVE);
        event.register(SLKeyMappings.BLUEPRINT_PREVIEW_ROTATE);
        event.register(SLKeyMappings.BLUEPRINT_PREVIEW_MOVE_Y);
        event.register(SLKeyMappings.TOOL_MODE_SCROLL);
        event.register(SLKeyMappings.BULK_NODE_SELECTION);
        event.register(SLKeyMappings.NETWORK_PREVIEW_MULTI_SELECT);
        event.register(SLKeyMappings.BLUEPRINT_UNDO);
        event.register(SLKeyMappings.CLEAR_STORED_NODES);
        event.register(SLKeyMappings.QUICK_FILTER_MARK);
        event.register(SLKeyMappings.PRIORITY_X10);
        event.register(SLKeyMappings.PRIORITY_X5);
        event.register(SLKeyMappings.GROUP_DETAILS_AND_EXPORT);
        BlueprintClientHooks.install(
            stack -> Minecraft.getInstance().setScreen(new BlueprintGroupScreen(stack)),
            (stack, tooltip) -> {
                tooltip.add(Component.translatable(
                    "tooltip.staticlogistics.blueprint.use",
                    Component.keybind("key.sneak")
                ).withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable(
                    "tooltip.staticlogistics.blueprint.scroll",
                    SLKeyMappings.BLUEPRINT_PREVIEW_MOVE.getTranslatedKeyMessage(),
                    SLKeyMappings.BLUEPRINT_PREVIEW_ROTATE.getTranslatedKeyMessage(),
                    SLKeyMappings.BLUEPRINT_PREVIEW_MOVE_Y.getTranslatedKeyMessage()
                ).withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable(
                    "tooltip.staticlogistics.blueprint.undo",
                    SLKeyMappings.BLUEPRINT_UNDO.getTranslatedKeyMessage()
                ).withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable(
                    "tooltip.staticlogistics.blueprint.clear",
                    Component.keybind("key.sneak")
                ).withStyle(ChatFormatting.GRAY));
            },
            itemId -> {
                Player player = Minecraft.getInstance().player;
                if (player == null) return 0;
                int count = 0;
                for (int index = 0; index < player.getInventory().getContainerSize(); index++) {
                    ItemStack slot = player.getInventory().getItem(index);
                    if (!slot.isEmpty() && BuiltInRegistries.ITEM
                        .getKey(slot.getItem()).toString().equals(itemId)) {
                        count += slot.getCount();
                    }
                }
                return count;
            }
        );
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBulkNodeSelection(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()
            || !(event.getItemStack().getItem() instanceof LinkConfiguratorItem item)
            || !SLKeyMappings.isKeyDown(SLKeyMappings.BULK_NODE_SELECTION)) return;
        ToolMode mode = item.getSettings(event.getItemStack()).mode();
        if (!mode.isLinkMode()) return;
        PacketDistributor.sendToServer(new C2SBulkSelectNodesPayload(event.getPos(), event.getFace(), mode));
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        double scrollY = event.getScrollDeltaY();
        if (scrollY == 0) return;

        ItemStack stack = mc.player.getMainHandItem();

        if (stack.getItem() instanceof LinkConfiguratorItem) {
            if (!SLKeyMappings.isKeyDown(SLKeyMappings.TOOL_MODE_SCROLL)) return;
            event.setCanceled(true);
            int currentMode = stack.getOrDefault(SLDataComponents.TOOL_MODE.get(), 0);

            ToolMode mode = ToolMode.fromId(currentMode);
            ToolMode newMode = scrollY < 0 ? mode.next() : mode.previous();
            int nextMode = newMode.getId();

            stack.set(SLDataComponents.TOOL_MODE.get(), nextMode);
            PacketDistributor.sendToServer(new C2SUpdateToolModePayload(nextMode));
            ToolModeFeedback.show(mc.player, stack, newMode);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.4f));
            return;
        }

        if (stack.getItem() instanceof BlueprintItem) {
            String previewStr = stack.getOrDefault(SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get(), "");
            if (previewStr.isEmpty()) return;

            boolean moveDown = SLKeyMappings.isKeyDown(SLKeyMappings.BLUEPRINT_PREVIEW_MOVE);
            boolean rotateDown = SLKeyMappings.isKeyDown(SLKeyMappings.BLUEPRINT_PREVIEW_ROTATE);
            boolean moveYDown = SLKeyMappings.isKeyDown(SLKeyMappings.BLUEPRINT_PREVIEW_MOVE_Y);
            if (!moveDown && !rotateDown && !moveYDown) return;

            event.setCanceled(true);
            BlockPos previewAnchor = posFromString(previewStr);
            if (previewAnchor == null) return;

            int rotation = stack.getOrDefault(SLDataComponents.BLUEPRINT_PREVIEW_ROTATION.get(), 0);

            if (rotateDown) {
                rotation = scrollY < 0 ? (rotation + 1) & 3 : (rotation - 1) & 3;
            } else if (moveYDown) {
                int step = scrollY < 0 ? 1 : -1;
                previewAnchor = previewAnchor.above(step);
            } else {
                Direction dir = getLookMoveDirection(mc.player);
                int step = scrollY < 0 ? 1 : -1;
                previewAnchor = previewAnchor.relative(dir, step);
            }

            stack.set(SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get(), previewAnchor.toShortString());
            stack.set(SLDataComponents.BLUEPRINT_PREVIEW_ROTATION.get(), rotation);
            PacketDistributor.sendToServer(new C2SUpdateBlueprintPreviewPayload(previewAnchor, rotation));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.4f));
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (event.getAction() != 1) return;

        if (SLKeyMappings.CLEAR_STORED_NODES.consumeClick()) {
            ItemStack stack = mc.player.getMainHandItem();
            if (!(stack.getItem() instanceof LinkConfiguratorItem))
                stack = mc.player.getOffhandItem();
            if (stack.getItem() instanceof LinkConfiguratorItem) {
                PacketDistributor.sendToServer(new C2SClearStoredNodesPayload());
            }
        }

        if (SLKeyMappings.BLUEPRINT_UNDO.consumeClick()) {
            ItemStack stack = mc.player.getMainHandItem();
            if (stack.getItem() instanceof BlueprintItem) {
                PacketDistributor.sendToServer(new C2SBlueprintUndoPayload());
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 0.7f, 0.5f));
            }
        }
    }

    private static Direction getLookMoveDirection(Player player) {
        return player.getDirection();
    }

    private static BlockPos posFromString(String s) {
        if (s == null || s.isEmpty()) return null;
        String[] parts = s.split(", ");
        if (parts.length != 3) return null;
        try {
            return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(SLMenuTypes.LINK_CONFIGURATOR_MENU.get(), LinkConfiguratorScreen::new);
        event.register(SLMenuTypes.FILTER_CONFIG.get(), FilterConfiguratorScreen::new);
        event.register(SLMenuTypes.HAND_FILTER.get(), HandFilterScreen::new);
    }
}
