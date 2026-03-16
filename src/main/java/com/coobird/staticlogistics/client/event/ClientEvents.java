package com.coobird.staticlogistics.client.event;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.client.ClientLinkCache;
import com.coobird.staticlogistics.common.init.SLDataComponents;
import com.coobird.staticlogistics.common.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.core.StaticLink;
import com.coobird.staticlogistics.network.c2s.C2SUpdateToolSettingsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = Staticlogistics.MODID, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ClientLinkCache.invalidate();
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientLinkCache.invalidate();
    }

    @SubscribeEvent
    public static void onClientTick(PlayerTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide) return;

        long time = event.getEntity().level().getGameTime();
        if (time % 40 != 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        List<UUID> toRemove = new ArrayList<>();
        ResourceKey<Level> currentDim = mc.level.dimension();

        for (StaticLink link : ClientLinkCache.getAllLinks()) {
            if (link.sourceDimension().equals(currentDim)) {
                if (mc.level.isLoaded(link.sourcePos()) && mc.level.getBlockState(link.sourcePos()).isAir()) {
                    toRemove.add(link.linkId());
                    continue;
                }
            }

            if (link.destDimension().equals(currentDim)) {
                if (mc.level.isLoaded(link.destPos()) && mc.level.getBlockState(link.destPos()).isAir()) {
                    toRemove.add(link.linkId());
                }
            }
        }

        if (!toRemove.isEmpty()) {
            toRemove.forEach(ClientLinkCache::removeLinkById);
        }
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !mc.player.isSecondaryUseActive()) return;

        ItemStack stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof LinkConfiguratorItem linker)) return;

        double scrollY = event.getScrollDeltaY();
        if (scrollY == 0) return;

        event.setCanceled(true);

        LinkConfiguratorItem.ToolSettings settings = linker.getSettings(stack);
        LinkConfiguratorItem.ToolMode nextMode = (scrollY > 0) ? settings.mode().next() : settings.mode().previous();

        PacketDistributor.sendToServer(new C2SUpdateToolSettingsPayload(
            stack.getOrDefault(SLDataComponents.PRIORITY.get(), 0),
            settings.group(),
            nextMode.ordinal(),
            settings.type()
        ));
    }
}