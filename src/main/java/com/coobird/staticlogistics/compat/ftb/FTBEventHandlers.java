package com.coobird.staticlogistics.compat.ftb;

import com.coobird.staticlogistics.common.util.TransferUtils;
import com.coobird.staticlogistics.storage.LinkManager;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.UUID;

public class FTBEventHandlers {

    public static void init() {
        TeamEvent.DELETED.register(event -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;
            for (UUID memberId : event.getTeam().getMembers()) {
                refreshPlayerLogistics(server, memberId);
            }
            TransferUtils.clearCache();
        });

        TeamEvent.PLAYER_LEFT_PARTY.register(event -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;
            refreshPlayerLogistics(server, event.getPlayerId());
            for (UUID memberId : event.getTeam().getMembers()) {
                refreshPlayerLogistics(server, memberId);
            }

            TransferUtils.clearCache();
        });
    }

    private static void refreshPlayerLogistics(MinecraftServer server, UUID playerUUID) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
        if (player != null) {
            LinkManager manager = LinkManager.get(player.level());
            manager.syncAllToPlayer(player);
        }
    }
}