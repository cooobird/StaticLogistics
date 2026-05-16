package com.coobird.staticlogistics.core.service;

import com.coobird.staticlogistics.integration.ftb.FTBTeamService;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public class TeamSyncService {
    private final FTBTeamService ftbTeamService;

    public TeamSyncService(FTBTeamService ftbTeamService) {
        this.ftbTeamService = ftbTeamService;
    }

    public void syncToTeamMembers(ServerPlayer player, CustomPacketPayload payload) {
        if (ftbTeamService.isFtbLoaded()) {
            try {
                var manager = FTBTeamsAPI.api().getManager();
                if (manager != null) {
                    manager.getTeamForPlayerID(player.getUUID()).ifPresentOrElse(team -> {
                        for (UUID mid : team.getMembers()) {
                            ServerPlayer m = player.server.getPlayerList().getPlayer(mid);
                            if (m != null) PacketDistributor.sendToPlayer(m, payload);
                        }
                    }, () -> PacketDistributor.sendToPlayersInDimension(player.serverLevel(), payload));
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        PacketDistributor.sendToAllPlayers(payload);
    }
}