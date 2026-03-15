package com.coobird.staticlogistics.client.event;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.client.ClientLinkCache;
import com.coobird.staticlogistics.core.StaticLink;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

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
    public static void onClientTick(PlayerTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide || event.getEntity().level().getGameTime() % 20 != 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        List<UUID> toRemove = new ArrayList<>();
        for (StaticLink link : ClientLinkCache.getAllLinks()) {
            ResourceKey<Level> dim = mc.level.dimension();

            boolean sourceInvalid = link.sourceDimension().equals(dim) &&
                mc.level.isLoaded(link.sourcePos()) &&
                mc.level.getBlockState(link.sourcePos()).isAir();

            boolean destInvalid = link.destDimension().equals(dim) &&
                mc.level.isLoaded(link.destPos()) &&
                mc.level.getBlockState(link.destPos()).isAir();

            if (sourceInvalid || destInvalid) {
                toRemove.add(link.linkId());
            }
        }

        if (!toRemove.isEmpty()) {
            toRemove.forEach(ClientLinkCache::removeLinkById);
        }
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
//        event.register(SLMenuTypes.FACE_CONFIG_MENU.get(), FaceConfigScreen::new);
    }
}