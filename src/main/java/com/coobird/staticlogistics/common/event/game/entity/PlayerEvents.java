package com.coobird.staticlogistics.common.event.game.entity;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.storage.LinkManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = Staticlogistics.MODID)
public class PlayerEvents {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        syncData(event);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        syncData(event);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        syncData(event);
    }

    private static void syncData(PlayerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LinkManager manager = LinkManager.get(player.level());
            if (manager != null) {
                manager.syncAllToPlayer(player);
            }
        }
    }
}