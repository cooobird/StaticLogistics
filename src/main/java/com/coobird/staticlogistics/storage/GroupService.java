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

    public static boolean canAccess(StaticLink link, Player actor) {
        if (link == null || actor == null) return false;
        if (link.owner().equals(actor.getUUID())) return true;

        if (ModList.get().isLoaded("ftbteams")) {
            return checkFTBTeamAlliance(link.owner(), actor.getUUID());
        }
        return false;
    }

    public static boolean canModify(StaticLink link, Player actor) {
        if (link == null || actor == null) return false;
        if (link.owner().equals(actor.getUUID())) return true;

        if (ModList.get().isLoaded("ftbteams")) {
            return isTeamAdminOf(link.owner(), actor.getUUID());
        }
        return false;
    }

    private static boolean checkFTBTeamAlliance(UUID owner, UUID actor) {
        try {
            var api = FTBTeamsAPI.api();
            if (!api.isManagerLoaded()) return false;
            return api.getManager().arePlayersInSameTeam(owner, actor);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isTeamAdminOf(UUID owner, UUID actor) {
        try {
            var api = FTBTeamsAPI.api();
            if (!api.isManagerLoaded()) return false;
            return api.getManager().getTeamForPlayerID(owner).map(team -> {
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
        Set<String> groups = new HashSet<>();
        LinkManager manager = LinkManager.get(level);
        if (manager == null) return groups;

        for (StaticLink link : manager.getLinksList()) {
            if (canAccess(link, actor)) {
                groups.add(link.groupId());
            }
        }
        return groups;
    }

    public static void deleteGroup(Level level, Player actor, String groupId) {
        if (!(level instanceof ServerLevel sl)) return;
        LinkManager manager = LinkManager.get(level);

        List<StaticLink> toRemove = manager.getLinksList().stream()
            .filter(l -> l.groupId().equals(groupId) && canModify(l, actor))
            .toList();

        manager.removeLinksBulk(toRemove, sl, actor);
    }

    public static void renameGroup(Level level, Player actor, String oldId, String newId) {
        if (!(level instanceof ServerLevel sl)) return;
        LinkManager manager = LinkManager.get(level);

        List<StaticLink> toRemove = new ArrayList<>();
        List<StaticLink> toAdd = new ArrayList<>();

        for (StaticLink l : manager.getLinksList()) {
            if (l.groupId().equals(oldId) && canModify(l, actor)) {
                toRemove.add(l);
                toAdd.add(new StaticLink(
                    l.sourcePos(), l.sourceFace(), l.sourceDimension(),
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