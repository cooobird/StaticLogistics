package com.coobird.staticlogistics.client.event;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.gui.screen.ContainerConfiguratorScreen;
import com.coobird.staticlogistics.gui.screen.FaceConfiguratorScreen;
import com.coobird.staticlogistics.gui.screen.FilterConfiguratorScreen;
import com.coobird.staticlogistics.gui.screen.HandFilterScreen;
import com.coobird.staticlogistics.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.item.util.ToolMode;
import com.coobird.staticlogistics.network.c2s.C2SUpdateToolSettingsPayload;
import com.coobird.staticlogistics.registry.SLDataComponents;
import com.coobird.staticlogistics.registry.SLMenuTypes;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
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

    private static long lastScrollTime = 0;

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !mc.player.isSecondaryUseActive()) return;

        ItemStack stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof LinkConfiguratorItem)) return;

        long now = Util.getMillis();
        if (now - lastScrollTime < 100) return;
        lastScrollTime = now;

        double scrollY = event.getScrollDeltaY();
        if (scrollY == 0) return;

        event.setCanceled(true);

        String currentGroup = stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "");
        int currentMode = stack.getOrDefault(SLDataComponents.TOOL_MODE.get(), 0);
        int typeMask = stack.getOrDefault(SLDataComponents.SELECTED_TYPES_MASK.get(), TransferRegistries.ITEM.getFlag());

        int modeCount = ToolMode.values().length;
        int nextMode = (currentMode + (scrollY < 0 ? 1 : modeCount - 1)) % modeCount;

        stack.set(SLDataComponents.TOOL_MODE.get(), nextMode);

        PacketDistributor.sendToServer(new C2SUpdateToolSettingsPayload(
            currentGroup,
            nextMode,
            typeMask
        ));

        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.4f));
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(SLMenuTypes.FACE_CONFIGURATOR_MENU.get(), FaceConfiguratorScreen::new);
        event.register(SLMenuTypes.CONTAINER_CONFIGURATOR_MENU.get(), ContainerConfiguratorScreen::new);
        event.register(SLMenuTypes.FILTER_CONFIG.get(), FilterConfiguratorScreen::new);
        event.register(SLMenuTypes.HAND_FILTER.get(), HandFilterScreen::new);
    }
}