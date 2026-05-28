package com.coobird.staticlogistics.server.event;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.storage.LinkManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.concurrent.atomic.AtomicBoolean;

@EventBusSubscriber(modid = Staticlogistics.MODID)
public class PlayerEvents {

    private static final AtomicBoolean startupValidationDone = new AtomicBoolean(false);

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            syncAllDimensionsToPlayer(sp);
            if (startupValidationDone.compareAndSet(false, true)) {
                for (ServerLevel level : sp.server.getAllLevels()) {
                    LinkManager.get(level).markOrphanScanNeeded();
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            syncAllDimensionsToPlayer(sp);
        }
    }

    public static void syncAllDimensionsToPlayer(ServerPlayer player) {
        for (ServerLevel level : player.server.getAllLevels()) {
            LinkManager mgr = LinkManager.get(level);
            if (mgr != null) {
                mgr.syncToPlayer(player);
            }
        }
    }
}
