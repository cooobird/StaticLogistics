package com.coobird.staticlogistics.compat.ftb;

import com.coobird.staticlogistics.common.util.TransferUtils;
import com.coobird.staticlogistics.storage.LinkManager;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class FTBEventHandlers {

    public static void init() {
        TeamEvent.DELETED.register(event -> {
            refreshPlayers(event.getTeam().getMembers());
        });

        TeamEvent.PLAYER_LEFT_PARTY.register(event -> {
            Set<UUID> targets = new HashSet<>(event.getTeam().getMembers());
            targets.add(event.getPlayerId());
            refreshPlayers(targets);
        });

        TeamEvent.PLAYER_JOINED_PARTY.register(event -> {
            refreshPlayers(event.getTeam().getMembers());
        });

        TeamEvent.OWNERSHIP_TRANSFERRED.register(event -> {
            refreshPlayers(event.getTeam().getMembers());
        });
    }

    private static void refreshPlayers(Collection<UUID> memberIds) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || memberIds.isEmpty()) return;

        TransferUtils.clearCache();

        for (UUID memberId : memberIds) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player != null) {
                LinkManager.syncAllDimensionsToPlayer(player);
            }
        }
    }
}