package com.coobird.staticlogistics.intergration.ftb;

import com.coobird.staticlogistics.server.event.game.entity.PlayerEvents;
import com.coobird.staticlogistics.util.CapabilityCache;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Collection;
import java.util.UUID;

public class FTBEventHandlers {
    public static void init() {
        TeamEvent.DELETED.register(event -> refreshPlayers(event.getTeam().getMembers()));
        TeamEvent.PLAYER_LEFT_PARTY.register(event -> {
            refreshPlayers(event.getTeam().getMembers());
            refreshPlayers(java.util.List.of(event.getPlayerId()));
        });
        TeamEvent.PLAYER_JOINED_PARTY.register(event -> refreshPlayers(event.getTeam().getMembers()));
        TeamEvent.OWNERSHIP_TRANSFERRED.register(event -> refreshPlayers(event.getTeam().getMembers()));
    }

    private static void refreshPlayers(Collection<UUID> memberIds) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || memberIds.isEmpty()) return;
        CapabilityCache.clearCache();
        for (UUID memberId : memberIds) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player != null) {
                PlayerEvents.syncAllDimensionsToPlayer(player);
            }
        }
    }
}