package com.coobird.staticlogistics.client.event;

import PortLib.extensions.net.minecraft.world.item.ItemStack.PortItemStackExtension;
import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.client.gui.screen.*;
import com.coobird.staticlogistics.client.key.SLKeyMappings;
import com.coobird.staticlogistics.content.item.*;
import com.coobird.staticlogistics.content.registry.SLMenuTypes;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.network.SLNetwork;
import com.coobird.staticlogistics.network.c2s.C2SBlueprintUndoPayload;
import com.coobird.staticlogistics.network.c2s.C2SClearStoredNodesPayload;
import com.coobird.staticlogistics.network.c2s.C2SUpdateBlueprintPreviewPayload;
import com.coobird.staticlogistics.network.c2s.C2SUpdateToolSettingsPayload;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import com.coobird.staticlogistics.transfer.TransferTypeSelection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.mesdag.portlib.event.client.PortRegisterMenuScreensEvent;

import java.util.List;

@Mod.EventBusSubscriber(modid = StaticLogistics.MODID, value = Dist.CLIENT)
public class ClientEvents {

    public static void registerModBus(IEventBus modEventBus) {
        installClientHooks();
        modEventBus.addListener(ClientEvents::registerKeyMappings);
        modEventBus.addListener(ClientEvents::registerMenuScreens);
    }

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

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(SLKeyMappings.BLUEPRINT_PREVIEW_MOVE);
        event.register(SLKeyMappings.BLUEPRINT_PREVIEW_ROTATE);
        event.register(SLKeyMappings.BLUEPRINT_PREVIEW_MOVE_Y);
        event.register(SLKeyMappings.TOOL_MODE_SCROLL);
        event.register(SLKeyMappings.BLUEPRINT_UNDO);
        event.register(SLKeyMappings.CLEAR_STORED_NODES);
        event.register(SLKeyMappings.QUICK_FILTER_MARK);
        event.register(SLKeyMappings.PRIORITY_X10);
        event.register(SLKeyMappings.PRIORITY_X5);
        event.register(SLKeyMappings.GROUP_DETAILS_AND_EXPORT);
    }

    private static void installClientHooks() {
        LinkConfiguratorClientHooks.install(
            stack -> Minecraft.getInstance().setScreen(new LinkConfiguratorScreen(stack)));
        BlueprintClientHooks.install(
            stack -> Minecraft.getInstance().setScreen(new BlueprintGroupScreen(stack)),
            (stack, tooltip) -> {
                tooltip.add(net.minecraft.network.chat.Component.translatable(
                    "tooltip.staticlogistics.blueprint.use",
                    net.minecraft.network.chat.Component.keybind("key.sneak")
                ).withStyle(net.minecraft.ChatFormatting.GRAY));
                tooltip.add(net.minecraft.network.chat.Component.translatable(
                    "tooltip.staticlogistics.blueprint.scroll",
                    SLKeyMappings.BLUEPRINT_PREVIEW_MOVE.getTranslatedKeyMessage(),
                    SLKeyMappings.BLUEPRINT_PREVIEW_ROTATE.getTranslatedKeyMessage(),
                    SLKeyMappings.BLUEPRINT_PREVIEW_MOVE_Y.getTranslatedKeyMessage()
                ).withStyle(net.minecraft.ChatFormatting.GRAY));
                tooltip.add(net.minecraft.network.chat.Component.translatable(
                    "tooltip.staticlogistics.blueprint.undo",
                    SLKeyMappings.BLUEPRINT_UNDO.getTranslatedKeyMessage()
                ).withStyle(net.minecraft.ChatFormatting.GRAY));
                tooltip.add(net.minecraft.network.chat.Component.translatable(
                    "tooltip.staticlogistics.blueprint.clear",
                    net.minecraft.network.chat.Component.keybind("key.sneak")
                ).withStyle(net.minecraft.ChatFormatting.GRAY));
            },
            itemId -> {
                Player player = Minecraft.getInstance().player;
                if (player == null) return 0;
                int count = 0;
                for (int index = 0; index < player.getInventory().getContainerSize(); index++) {
                    ItemStack slot = player.getInventory().getItem(index);
                    if (!slot.isEmpty() && net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getKey(slot.getItem()).toString().equals(itemId)) {
                        count += slot.getCount();
                    }
                }
                return count;
            }
        );
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        double scrollY = event.getScrollDelta();
        if (scrollY == 0) return;

        ItemStack stack = mc.player.getMainHandItem();

        if (stack.getItem() instanceof LinkConfiguratorItem) {
            if (!SLKeyMappings.TOOL_MODE_SCROLL.isDown()) return;
            event.setCanceled(true);
            String currentGroup = PortItemStackExtension.getDataOrDefault(stack, SLDataComponents.SELECTED_GROUP, "");
            var currentGroupKey = PortItemStackExtension.getData(
                stack, SLDataComponents.SELECTED_GROUP_KEY.get());
            int currentMode = PortItemStackExtension.getDataOrDefault(stack, SLDataComponents.TOOL_MODE, 0);
            List<ResourceLocation> selectedTypeIds = PortItemStackExtension.getData(stack, SLDataComponents.SELECTED_TYPES.get());
            int legacyMask = PortItemStackExtension.getDataOrDefault(
                stack, SLDataComponents.SELECTED_TYPES_MASK.get(), 0);
            selectedTypeIds = TransferTypeSelection.mergeIdsWithMask(
                selectedTypeIds == null ? List.of() : selectedTypeIds,
                legacyMask, TransferRegistries.getAllActive());

            ToolMode mode = ToolMode.fromId(currentMode);
            ToolMode newMode = scrollY < 0 ? mode.next() : mode.previous();
            int nextMode = newMode.getId();

            PortItemStackExtension.setData(stack, SLDataComponents.TOOL_MODE, nextMode);
            SLNetwork.HANDLER.sendToServer(new C2SUpdateToolSettingsPayload(
                currentGroup, currentGroupKey, nextMode, selectedTypeIds, legacyMask));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.2f, 0.4f));
            return;
        }

        if (stack.getItem() instanceof BlueprintItem) {
            String previewStr = PortItemStackExtension.getDataOrDefault(stack, SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR, "");
            if (previewStr.isEmpty()) return;

            boolean moveDown = SLKeyMappings.BLUEPRINT_PREVIEW_MOVE.isDown();
            boolean rotateDown = SLKeyMappings.BLUEPRINT_PREVIEW_ROTATE.isDown();
            boolean moveYDown = SLKeyMappings.BLUEPRINT_PREVIEW_MOVE_Y.isDown();
            if (!moveDown && !rotateDown && !moveYDown) return;

            event.setCanceled(true);
            BlockPos previewAnchor = posFromString(previewStr);
            if (previewAnchor == null) return;

            int rotation = PortItemStackExtension.getDataOrDefault(stack, SLDataComponents.BLUEPRINT_PREVIEW_ROTATION, 0);

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

            PortItemStackExtension.setData(stack, SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR, previewAnchor.toShortString());
            PortItemStackExtension.setData(stack, SLDataComponents.BLUEPRINT_PREVIEW_ROTATION, rotation);
            SLNetwork.HANDLER.sendToServer(new C2SUpdateBlueprintPreviewPayload(previewAnchor, rotation));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.2f, 0.4f));
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
                SLNetwork.HANDLER.sendToServer(new C2SClearStoredNodesPayload());
            }
        }

        if (SLKeyMappings.BLUEPRINT_UNDO.consumeClick()) {
            ItemStack stack = mc.player.getMainHandItem();
            if (stack.getItem() instanceof BlueprintItem) {
                SLNetwork.HANDLER.sendToServer(new C2SBlueprintUndoPayload());
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 0.5f));
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

    public static void registerMenuScreens(PortRegisterMenuScreensEvent event) {
        event.register(SLMenuTypes.NODE_CONFIGURATOR_MENU.get(), NodeConfiguratorScreen::new);
        event.register(SLMenuTypes.FILTER_CONFIG.get(), FilterConfiguratorScreen::new);
        event.register(SLMenuTypes.HAND_FILTER.get(), HandFilterScreen::new);
    }

    /**
     * 拦截 GregTech 的方块高亮渲染。
     * 当 LinkConfigurator 不在扳手模式时，取消整个高亮事件，
     * 防止 GregTech 的 IToolGridHighlight 九宫格显示。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockHighlight(RenderHighlightEvent.Block event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack held = mc.player.getMainHandItem();
        if (held.getItem() instanceof LinkConfiguratorItem item) {
            if (item.getSettings(held).mode() != ToolMode.WRENCH) {
                event.setCanceled(true);
            }
        }
    }
}
