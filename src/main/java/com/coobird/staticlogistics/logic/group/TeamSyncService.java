package com.coobird.staticlogistics.logic.group;

import com.coobird.staticlogistics.integration.ftb.FTBTeamService;
import com.coobird.staticlogistics.network.SLNetwork;
import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import net.minecraft.server.level.ServerPlayer;
import org.mesdag.portlib.network.IPortPacket;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * 团队数据同步服务——根据 FTB Teams 模块是否存在，将数据包发送给团队成员或全体玩家。
 */
public class TeamSyncService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final FTBTeamService ftbTeamService;

    public TeamSyncService(FTBTeamService ftbTeamService) {
        this.ftbTeamService = ftbTeamService;
    }

    // 如果 FTB 加载则发给团队成员，否则发给维度内玩家；兜底广播全体
    public void syncToTeamMembers(ServerPlayer player, IPortPacket payload) {
        if (ftbTeamService.isFtbLoaded()) {
            try {
                var manager = FTBTeamsAPI.api().getManager();
                if (manager != null) {
                    manager.getTeamForPlayerID(player.getUUID()).ifPresentOrElse(team -> {
                        for (UUID mid : team.getMembers()) {
                            ServerPlayer m = player.server.getPlayerList().getPlayer(mid);
                            if (m != null) SLNetwork.HANDLER.sendToPlayer(m, payload);
                        }
                    }, () -> SLNetwork.HANDLER.sendToPlayersInDimension(player.serverLevel().dimension(), payload));
                    return;
                }
            } catch (Exception e) {
                LOGGER.warn("FTB team sync failed, falling back to broadcast", e);
            }
        }
        SLNetwork.HANDLER.sendToAllPlayers(payload);
    }
}