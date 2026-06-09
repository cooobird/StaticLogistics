package com.coobird.staticlogistics.logic.event;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.logic.GlobalLogisticsManager;
import com.coobird.staticlogistics.network.SLNetwork;
import com.coobird.staticlogistics.network.s2c.S2CSyncEmptyGroupsPayload;
import com.coobird.staticlogistics.storage.link.LinkManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Mod.EventBusSubscriber(modid = StaticLogistics.MODID)
public class PlayerEvents {

    private static final AtomicBoolean startupValidationDone = new AtomicBoolean(false);

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            refreshPlayerProfile(sp);
            syncAllDimensionsToPlayer(sp);
            syncEmptyGroupsToPlayer(sp);
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

    private static void syncEmptyGroupsToPlayer(ServerPlayer player) {
        GlobalLogisticsManager glm = GlobalLogisticsManager.get(player.server);
        Set<String> groups = glm.getGroups(player.getUUID());
        if (!groups.isEmpty()) {
            SLNetwork.HANDLER.sendToPlayer(player, new S2CSyncEmptyGroupsPayload(player.getUUID(), groups));
        }
    }
}
