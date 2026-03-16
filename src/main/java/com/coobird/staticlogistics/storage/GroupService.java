package com.coobird.staticlogistics.storage;

import com.coobird.staticlogistics.compat.ModIds;
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
        if (link.owner().equals(actor.getUUID())) return true;
        return isFtbLoaded() && checkFTBTeamAlliance(link.owner(), actor.getUUID());
    }

    public static boolean canModify(StaticLink link, Player actor) {
        if (link.owner().equals(actor.getUUID())) return true;
        return isFtbLoaded() && isTeamAdminOf(link.owner(), actor.getUUID());
    }

    private static boolean isFtbLoaded() {
        return ModList.get().isLoaded(ModIds.FTB_TEAMS);
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
            return manager.getTeamForPlayerID(owner)
                .map(team -> team.getRankForPlayer(actor).getPower() >= TeamRank.OFFICER.getPower())
                .orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    public static Set<String> getGroupsForPlayer(Level level, Player actor) {
        LinkManager manager = LinkManager.get(level);
        Set<String> allGroups = new HashSet<>(manager.getGroupsByOwner(actor.getUUID()));

        if (isFtbLoaded()) {
            try {
                var ftbManager = FTBTeamsAPI.api().getManager();
                if (ftbManager != null) {
                    ftbManager.getTeamForPlayerID(actor.getUUID()).ifPresent(team -> {
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
        LinkManager manager = LinkManager.get(sl);

        List<StaticLink> toRemove = manager.getLinksList().stream()
            .filter(l -> l.groupId().equals(groupId) && canModify(l, actor))
            .toList();

        if (!toRemove.isEmpty()) {
            manager.removeLinksBulk(toRemove, sl, actor);
        }
    }

    public static void renameGroup(Level level, Player actor, String oldId, String newId) {
        if (!(level instanceof ServerLevel sl) || oldId.equals(newId) || newId.isBlank()) return;
        LinkManager manager = LinkManager.get(sl);

        List<StaticLink> toRemove = new ArrayList<>();
        List<StaticLink> toAdd = new ArrayList<>();

        for (StaticLink l : manager.getLinksList()) {
            if (l.groupId().equals(oldId) && canModify(l, actor)) {
                toRemove.add(l);
                toAdd.add(createNewLinkWithNewGroup(l, newId));
            }
        }

        if (!toRemove.isEmpty()) {
            manager.removeLinksBulk(toRemove, sl, actor);
            manager.addLinksBulk(toAdd, sl, actor);
        }
    }

    private static StaticLink createNewLinkWithNewGroup(StaticLink l, String newId) {
        return new StaticLink(
            l.linkId(), l.sourcePos(), l.sourceFace(), l.sourceDimension(),
            l.destPos(), l.destFace(), l.destDimension(),
            l.transferFlags(), l.priority(),
            l.owner(), l.ownerName(), newId,
            l.tier(), l.allowCrossDim()
        );
    }

    public static String getNextGroupId(String currentId, Set<String> existing) {
        if (!existing.contains(currentId)) {
            return currentId;
        }

        try {
            int val = Integer.parseInt(currentId);
            String next = String.valueOf(val + 1);
            return getNextGroupId(next, existing);
        } catch (NumberFormatException e) {
            String next = currentId + "_copy";
            return existing.contains(next) ? next : next + "_" + existing.size();
        }
    }
}