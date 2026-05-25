package com.coobird.staticlogistics.client.event;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.api.type.ToolMode;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.client.key.SLKeyMappings;
import com.coobird.staticlogistics.gui.screen.ContainerConfiguratorScreen;
import com.coobird.staticlogistics.gui.screen.FaceConfiguratorScreen;
import com.coobird.staticlogistics.gui.screen.FilterConfiguratorScreen;
import com.coobird.staticlogistics.gui.screen.HandFilterScreen;
import com.coobird.staticlogistics.item.BlueprintItem;
import com.coobird.staticlogistics.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.network.c2s.C2SClearStoredNodesPayload;
import com.coobird.staticlogistics.network.c2s.C2SUpdateBlueprintPreviewPayload;
import com.coobird.staticlogistics.network.c2s.C2SUpdateToolSettingsPayload;
import com.coobird.staticlogistics.registry.SLDataComponents;
import com.coobird.staticlogistics.registry.SLMenuTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
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
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Staticlogistics.MODID, value = Dist.CLIENT)
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
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(SLKeyMappings.BLUEPRINT_PREVIEW_MOVE);
        event.register(SLKeyMappings.BLUEPRINT_PREVIEW_ROTATE);
        event.register(SLKeyMappings.BLUEPRINT_PREVIEW_MOVE_Y);
        event.register(SLKeyMappings.CLEAR_STORED_NODES);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        double scrollY = event.getScrollDeltaY();
        if (scrollY == 0) return;

        ItemStack stack = mc.player.getMainHandItem();

        if (stack.getItem() instanceof LinkConfiguratorItem) {
            if (!mc.player.isShiftKeyDown()) return;
            event.setCanceled(true);
            String currentGroup = stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "");
            int currentMode = stack.getOrDefault(SLDataComponents.TOOL_MODE.get(), 0);
            int typeMask = stack.getOrDefault(SLDataComponents.SELECTED_TYPES_MASK.get(), 0);

            ToolMode mode = ToolMode.fromId(currentMode);
            ToolMode newMode = scrollY < 0 ? mode.next() : mode.previous();
            int nextMode = newMode.getId();

            stack.set(SLDataComponents.TOOL_MODE.get(), nextMode);
            PacketDistributor.sendToServer(new C2SUpdateToolSettingsPayload(currentGroup, nextMode, typeMask));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.4f));
            return;
        }

        if (stack.getItem() instanceof BlueprintItem) {
            String previewStr = stack.getOrDefault(SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get(), "");
            if (previewStr.isEmpty()) return;

            boolean moveDown = SLKeyMappings.BLUEPRINT_PREVIEW_MOVE.isDown();
            boolean rotateDown = SLKeyMappings.BLUEPRINT_PREVIEW_ROTATE.isDown();
            boolean moveYDown = SLKeyMappings.BLUEPRINT_PREVIEW_MOVE_Y.isDown();
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

        if (SLKeyMappings.CLEAR_STORED_NODES.consumeClick() && mc.player.isShiftKeyDown()) {
            ItemStack stack = mc.player.getMainHandItem();
            if (!(stack.getItem() instanceof LinkConfiguratorItem))
                stack = mc.player.getOffhandItem();
            if (stack.getItem() instanceof LinkConfiguratorItem) {
                PacketDistributor.sendToServer(new C2SClearStoredNodesPayload());
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
        event.register(SLMenuTypes.FACE_CONFIGURATOR_MENU.get(), FaceConfiguratorScreen::new);
        event.register(SLMenuTypes.CONTAINER_CONFIGURATOR_MENU.get(), ContainerConfiguratorScreen::new);
        event.register(SLMenuTypes.FILTER_CONFIG.get(), FilterConfiguratorScreen::new);
        event.register(SLMenuTypes.HAND_FILTER.get(), HandFilterScreen::new);
    }
}