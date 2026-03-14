package com.coobird.staticlogistics.storage;

import com.coobird.staticlogistics.core.StaticLink;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.TeamRank;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

import java.util.*;

public class GroupService {

    private static final String FTB_TEAMS_MODID = "ftbteams";

    public static boolean canAccess(StaticLink link, Player actor) {
        if (link == null || actor == null) return false;
        if (link.owner().equals(actor.getUUID())) return true;

        if (!ModList.get().isLoaded(FTB_TEAMS_MODID)) return false;

        return checkFTBTeamAlliance(link.owner(), actor.getUUID());
    }

    public static boolean canModify(StaticLink link, Player actor) {
        if (link == null || actor == null) return false;
        if (link.owner().equals(actor.getUUID())) return true;

        if (!ModList.get().isLoaded(FTB_TEAMS_MODID)) return false;

        return isTeamAdminOf(link.owner(), actor.getUUID());
    }

    private static boolean checkFTBTeamAlliance(UUID owner, UUID actor) {
        try {
            var manager = FTBTeamsAPI.api().getManager();
            return manager != null && manager.arePlayersInSameTeam(owner, actor);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isTeamAdminOf(UUID owner, UUID actor) {
        try {
            var manager = FTBTeamsAPI.api().getManager();
            if (manager == null) return false;

            return manager.getTeamForPlayerID(owner).map(team -> {
                if (team.getMembers().contains(actor)) {
                    TeamRank rank = team.getRankForPlayer(actor);
                    return rank.getPower() >= TeamRank.OFFICER.getPower();
                }
                return false;
            }).orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    public static Set<String> getGroupsForPlayer(Level level, Player actor) {
        LinkManager manager = LinkManager.get(level);
        if (manager == null) return Collections.emptySet();

        Set<String> allGroups = new HashSet<>();

        allGroups.addAll(manager.getGroupsByOwner(actor.getUUID()));

        if (ModList.get().isLoaded(FTB_TEAMS_MODID)) {
            try {
                var teamManager = FTBTeamsAPI.api().getManager();
                if (teamManager != null) {
                    teamManager.getTeamForPlayerID(actor.getUUID()).ifPresent(team -> {
                        for (UUID memberId : team.getMembers()) {
                            if (!memberId.equals(actor.getUUID())) {
                                allGroups.addAll(manager.getGroupsByOwner(memberId));
                            }
                        }
                    });
                }
            } catch (Exception ignored) {
            }
        }

        return allGroups;
    }

    public static void deleteGroup(Level level, Player actor, String groupId) {
        if (!(level instanceof ServerLevel sl)) return;
        LinkManager manager = LinkManager.get(level);
        if (manager == null) return;

        List<StaticLink> toRemove = manager.getLinksList().stream()
            .filter(l -> l.groupId().equals(groupId) && canModify(l, actor))
            .toList();

        if (!toRemove.isEmpty()) {
            manager.removeLinksBulk(toRemove, sl, actor);
        }
    }

    public static void renameGroup(Level level, Player actor, String oldId, String newId) {
        if (!(level instanceof ServerLevel sl) || oldId.equals(newId)) return;
        LinkManager manager = LinkManager.get(level);
        if (manager == null) return;

        List<StaticLink> toRemove = new ArrayList<>();
        List<StaticLink> toAdd = new ArrayList<>();

        for (StaticLink l : manager.getLinksList()) {
            if (l.groupId().equals(oldId) && canModify(l, actor)) {
                toRemove.add(l);
                toAdd.add(new StaticLink(
                    l.linkId(), l.sourcePos(), l.sourceFace(), l.sourceDimension(),
                    l.destPos(), l.destFace(), l.destDimension(),
                    l.transferFlags(), l.priority(),
                    l.owner(), l.ownerName(), newId,
                    l.maxRange(), l.allowCrossDim()
                ));
            }
        }

        if (!toRemove.isEmpty()) {
            manager.removeLinksBulk(toRemove, sl, actor);
            manager.addLinksBulk(toAdd, sl, actor);
        }
    }
}