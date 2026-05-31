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
            refreshPlayerProfile(sp);
            syncAllDimensionsToPlayer(sp);
            if (startupValidationDone.compareAndSet(false, true)) {
                for (ServerLevel level : sp.server.getAllLevels()) {
                    LinkManager.get(level).markOrphanScanNeeded();
                }
            }
        }
    }

    private static void refreshPlayerProfile(ServerPlayer player) {
        var profile = player.getGameProfile();
        var uuid = player.getUUID();
        for (ServerLevel level : player.server.getAllLevels()) {
            LinkManager mgr = LinkManager.get(level);
            for (long key : mgr.getAllConfigKeys()) {
                var cfg = mgr.getFaceConfig(key);
                if (cfg != null && uuid.equals(cfg.faceConfig.getOwner())) {
                    cfg.faceConfig.setOwner(uuid, profile.getName(), profile);
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
