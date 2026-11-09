package com.coobird.staticlogistics.client.event;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.client.data.ConnectionDirectoryModel;
import com.coobird.staticlogistics.client.data.SelectedWorldLinks;
import com.coobird.staticlogistics.client.data.ToolModeSession;
import com.coobird.staticlogistics.client.gui.component.ConfiguratorHud;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = StaticLogistics.MODID, value = Dist.CLIENT)
public class ClientEvents {
    @SubscribeEvent
    public static void onToolHud(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || minecraft.options.hideGui) return;
        ItemStack stack = minecraft.player.getMainHandItem();
        if (!(stack.getItem() instanceof LinkConfiguratorItem)) stack = minecraft.player.getOffhandItem();
        if (!(stack.getItem() instanceof LinkConfiguratorItem item)) return;
        ConfiguratorHud.render(event.getGuiGraphics(), minecraft.font, item.getSettings(stack), stack);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ClientLinkData.INSTANCE.invalidate();
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ConnectionDirectoryModel.clearScope();
        ToolModeSession.clear();
        SelectedWorldLinks.clear();
        LinkConfiguratorClientHooks.clearSelections();
        ClientLinkData.INSTANCE.invalidate();
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        LinkConfiguratorClientHooks.installBulkSelectionKey(() -> SLKeyMappings.isKeyDown(SLKeyMappings.BULK_NODE_SELECTION));
        LinkConfiguratorClientHooks.installRetainedSelectionIds(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return Set.of();
            Set<UUID> ids = new HashSet<>();
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                var id = player.getInventory().getItem(slot).get(SLDataComponents.STORED_SELECTION_ID.get());
                if (id != null) ids.add(id);
            }
            return ids;
        });
        event.register(SLKeyMappings.BLUEPRINT_PREVIEW_MOVE);
        event.register(SLKeyMappings.BLUEPRINT_PREVIEW_ROTATE);
        event.register(SLKeyMappings.BLUEPRINT_PREVIEW_MOVE_Y);
        event.register(SLKeyMappings.BULK_NODE_SELECTION);
        event.register(SLKeyMappings.BLUEPRINT_UNDO);
        event.register(SLKeyMappings.BLUEPRINT_PASTE);
        event.register(SLKeyMappings.CLEAR_STORED_NODES);
        event.register(SLKeyMappings.PRIORITY_X10);
        event.register(SLKeyMappings.PRIORITY_X5);
        event.register(SLKeyMappings.QUICK_FILTER_MARK);
        event.register(SLKeyMappings.GROUP_DETAILS_AND_EXPORT);
        BlueprintClientHooks.install(
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
        if (BlueprintModeHandler.isActive(event.getItemStack())) return;
        ToolMode mode = ToolModeSession.currentMode(event.getItemStack());
        if (!mode.isLinkMode()) return;
        submitBulkSelection(event.getItemStack(), event.getPos(), event.getFace(), mode);
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static void submitBulkSelection(ItemStack stack, BlockPos pos, Direction face, ToolMode mode) {
        Minecraft mc = Minecraft.getInstance();
        var level = mc.level;
        int slot = ToolModeSession.heldSlot(stack);
        var toolId = stack.get(SLDataComponents.TOOL_SESSION.get());
        if (toolId == null) return;
        BlockPos target = pos.immutable();
        ToolModeSession.afterConfirmation(stack, () -> {
            if (mc.player == null || mc.level != level || mc.screen != null
                || (slot != mc.player.getInventory().selected && slot != 40)) return;
            ItemStack current = mc.player.getInventory().getItem(slot);
            if (!(current.getItem() instanceof LinkConfiguratorItem item)
                || !toolId.equals(current.get(SLDataComponents.TOOL_SESSION.get()))
                || item.getSettings(current).mode() != mode) return;
            PacketDistributor.sendToServer(new C2SBulkSelectNodesPayload(target, face, mode, slot, toolId));
        });
    }

    /// 模式未确认时禁止按旧模式操作；批量选择保留点击目标，确认后再提交。
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPendingModeInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        ItemStack stack = event.isUseItem() ? mc.player.getItemInHand(event.getHand())
            : mc.player.getMainHandItem();
        if (!ToolModeSession.awaiting(stack)) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        ToolMode mode = ToolModeSession.currentMode(stack);
        if (event.isUseItem() && mode.isLinkMode()
            && SLKeyMappings.isKeyDown(SLKeyMappings.BULK_NODE_SELECTION)
            && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            submitBulkSelection(stack, hit.getBlockPos(), hit.getDirection(), mode);
        } else {
            mc.player.displayClientMessage(Component.translatable("msg.staticlogistics.mode_pending"), true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.getOverlay() != null || !mc.isWindowActive()) return;

        double scrollY = event.getScrollDeltaY();
        if (scrollY == 0) return;

        /// 有效蓝图预览优先使用已有操作键，蹲下滚轮不抢占预览平移。
        if (scrollBlueprintPreview(mc, scrollY)) {
            event.setCanceled(true);
            return;
        }
        if (!mc.options.keyShift.isDown()) return;
        ItemStack stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof LinkConfiguratorItem)) stack = mc.player.getOffhandItem();
        if (!(stack.getItem() instanceof LinkConfiguratorItem)) return;
        ToolMode current = ToolModeSession.currentMode(stack);
        int index = current == ToolMode.BLUEPRINT ? (scrollY > 0 ? ToolMode.ORDER.size() - 1 : 0)
            : Math.floorMod(ToolMode.ORDER.indexOf(current) + (scrollY > 0 ? -1 : 1), ToolMode.ORDER.size());
        ToolModeSession.select(stack, ToolMode.ORDER.get(index));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.4f));
        event.setCanceled(true);
    }

    private static boolean scrollBlueprintPreview(Minecraft mc, double scrollY) {
        ItemStack stack = BlueprintModeHandler.held(mc.player);
        if (!BlueprintModeHandler.isActive(stack)) return false;
        String previewStr = stack.getOrDefault(SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get(), "");
        if (previewStr.isEmpty() || !mc.player.level().dimension().location().toString().equals(
            stack.getOrDefault(SLDataComponents.BLUEPRINT_PREVIEW_DIMENSION.get(), ""))) return false;

        boolean moveDown = SLKeyMappings.isKeyDown(SLKeyMappings.BLUEPRINT_PREVIEW_MOVE);
        boolean rotateDown = SLKeyMappings.isKeyDown(SLKeyMappings.BLUEPRINT_PREVIEW_ROTATE);
        boolean moveYDown = SLKeyMappings.isKeyDown(SLKeyMappings.BLUEPRINT_PREVIEW_MOVE_Y);
        if (!moveDown && !rotateDown && !moveYDown) return false;

        BlockPos previewAnchor = posFromString(previewStr);
        if (previewAnchor == null) return false;
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
        return true;
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (event.getAction() != 1) return;
        if (SLKeyMappings.BLUEPRINT_PASTE.consumeClick()) {
            PacketDistributor.sendToServer(new C2SBeginBlueprintPlacementPayload());
        }

        if (SLKeyMappings.CLEAR_STORED_NODES.consumeClick()) {
            ItemStack stack = mc.player.getMainHandItem();
            if (!(stack.getItem() instanceof LinkConfiguratorItem))
                stack = mc.player.getOffhandItem();
            if (stack.getItem() instanceof LinkConfiguratorItem && !BlueprintModeHandler.isActive(stack)) {
                PacketDistributor.sendToServer(new C2SClearStoredNodesPayload());
            }
        }

        if (SLKeyMappings.BLUEPRINT_UNDO.consumeClick()) {
            if (mc.player.getMainHandItem().getItem() instanceof LinkConfiguratorItem
                || mc.player.getOffhandItem().getItem() instanceof LinkConfiguratorItem) {
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
