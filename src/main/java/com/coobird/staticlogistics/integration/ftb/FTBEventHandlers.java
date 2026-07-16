package com.coobird.staticlogistics.integration.ftb;

import com.coobird.staticlogistics.content.event.PlayerEvents;
import com.coobird.staticlogistics.logistics.group.PermissionService;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class FTBEventHandlers {
    private static final Set<UUID> PENDING_REFRESHES = new LinkedHashSet<>();
    private static boolean pendingGlobalRefresh;

    public static void init() {
        FTBTeamService teamService = new FTBTeamService();
        PermissionService.getInstance().installTeamAccessPolicy(teamService);
        PermissionService.getInstance().installTeamMemberProvider(teamService);
        NeoForge.EVENT_BUS.addListener(FTBEventHandlers::onServerTick);
        // 删除回调中的团队成员集合可能已被 FTB Teams 清空，必须从服务器在线玩家表重算权限。
        TeamEvent.DELETED.register(FTBEventHandlers::queueDeletedTeamRefresh);
        TeamEvent.PLAYER_CHANGED.register(event -> {
            queuePlayers(event.getTeam().getMembers());
            event.getPreviousTeam().ifPresent(team -> queuePlayers(team.getMembers()));
            queuePlayers(java.util.List.of(event.getPlayerId()));
        });
        TeamEvent.PLAYER_LEFT_PARTY.register(event -> {
            queuePlayers(event.getTeam().getMembers());
            queuePlayers(java.util.List.of(event.getPlayerId()));
        });
        TeamEvent.PLAYER_JOINED_PARTY.register(event -> queuePlayers(event.getTeam().getMembers()));
        TeamEvent.OWNERSHIP_TRANSFERRED.register(event -> queuePlayers(event.getTeam().getMembers()));
        TeamEvent.PROPERTIES_CHANGED.register(event -> queuePlayers(event.getTeam().getMembers()));
        TeamEvent.ADD_ALLY.register(event -> refreshAlliance(event));
        TeamEvent.REMOVE_ALLY.register(event -> refreshAlliance(event));
    }

    private static void refreshAlliance(dev.ftb.mods.ftbteams.api.event.TeamAllyEvent event) {
        java.util.LinkedHashSet<UUID> affected = new java.util.LinkedHashSet<>(event.getTeam().getMembers());
        event.getPlayers().forEach(profile -> affected.add(profile.getId()));
        queuePlayers(affected);
    }

    private static void queueDeletedTeamRefresh(TeamEvent event) {
        // FTB Teams 会在删除事件前清空成员映射；稀有事件直接重算全部在线玩家才能可靠撤权。
        synchronized (PENDING_REFRESHES) {
            pendingGlobalRefresh = true;
        }
    }

    private static void queuePlayers(Collection<UUID> memberIds) {
        if (memberIds == null) return;
        synchronized (PENDING_REFRESHES) {
            PENDING_REFRESHES.addAll(memberIds);
        }
    }

    /** 同一服务器 tick 内的重复团队事件只为每名玩家重建一次权威快照。 */
    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        LinkedHashSet<UUID> affected;
        boolean refreshAll;
        synchronized (PENDING_REFRESHES) {
            if (PENDING_REFRESHES.isEmpty() && !pendingGlobalRefresh) return;
            affected = new LinkedHashSet<>(PENDING_REFRESHES);
            PENDING_REFRESHES.clear();
            refreshAll = pendingGlobalRefresh;
            pendingGlobalRefresh = false;
        }
        if (refreshAll) {
            server.getPlayerList().getPlayers().forEach(PlayerEvents::refreshClientState);
            return;
        }
        for (UUID memberId : affected) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player != null) {
                PlayerEvents.refreshClientState(player);
            }
        }
    }

    /** 服务端停止时清除尚未消费的跨 tick 刷新请求，避免下一实例继承旧状态。 */
    public static void clearPending() {
        synchronized (PENDING_REFRESHES) {
            PENDING_REFRESHES.clear();
            pendingGlobalRefresh = false;
        }
    }
}
