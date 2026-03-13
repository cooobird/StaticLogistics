package com.coobird.staticlogistics.storage;

import com.coobird.staticlogistics.core.StaticLink;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

import java.util.*;

public class GroupService {

    public static boolean canAccess(UUID owner, UUID actor) {
        if (owner.equals(actor)) return true;
        if (ModList.get().isLoaded(FTBTeamsAPI.MOD_ID)) {
            return checkFTBTeamAlliance(owner, actor);
        }
        return false;
    }

    private static boolean checkFTBTeamAlliance(UUID owner, UUID actor) {
        try {
            if (!FTBTeamsAPI.api().isManagerLoaded()) return false;
            return FTBTeamsAPI.api().getManager().arePlayersInSameTeam(owner, actor);
        } catch (Exception e) {
            return false;
        }
    }

    public static int getNextAvailableGroupId(Level level, UUID playerUuid) {
        Set<String> groups = getGroupsForPlayer(level, playerUuid);
        int maxId = 0;
        for (String g : groups) {
            try {
                maxId = Math.max(maxId, Integer.parseInt(g));
            } catch (NumberFormatException ignored) {
            }
        }
        return maxId + 1;
    }

    public static Set<String> getGroupsForPlayer(Level level, UUID playerUuid) {
        Set<String> groups = new HashSet<>();
        for (StaticLink link : LinkManager.get(level).getLinksList()) {
            if (canAccess(link.owner(), playerUuid)) {
                groups.add(link.groupId());
            }
        }
        return groups;
    }

    public static boolean smartRemove(Level level, BlockPos pos, Direction face, UUID actorUuid) {
        if (!(level instanceof ServerLevel sl)) return false;
        LinkManager manager = LinkManager.get(level);
        long key = manager.posToKey(pos, face);
        List<StaticLink> existing = manager.getLinksByKey(key);

        if (existing.isEmpty()) return false;

        List<StaticLink> toRemove = new ArrayList<>();
        for (StaticLink link : existing) {
            if (canAccess(link.owner(), actorUuid)) {
                toRemove.add(link);
            }
        }

        if (!toRemove.isEmpty()) {
            manager.removeLinksBulk(toRemove, sl);
            return true;
        }
        return false;
    }

    public static void deleteGroup(Level level, UUID actorUuid, String groupId) {
        if (!(level instanceof ServerLevel sl)) return;
        LinkManager manager = LinkManager.get(level);

        List<StaticLink> toRemove = new ArrayList<>();
        for (StaticLink link : manager.getLinksList()) {
            if (link.owner().equals(actorUuid) && link.groupId().equals(groupId)) {
                toRemove.add(link);
            }
        }

        manager.removeLinksBulk(toRemove, sl);
    }

    public static void renameGroup(Level level, UUID actorUuid, String oldId, String newId) {
        if (!(level instanceof ServerLevel sl)) return;
        LinkManager manager = LinkManager.get(level);

        List<StaticLink> toRemove = new ArrayList<>();
        List<StaticLink> toAdd = new ArrayList<>();

        for (StaticLink l : manager.getLinksList()) {
            if (l.owner().equals(actorUuid) && l.groupId().equals(oldId)) {
                toRemove.add(l);
                toAdd.add(new StaticLink(
                    l.sourcePos(), l.sourceFace(), l.destPos(), l.destFace(),
                    l.destDimension(), l.transferFlags(), l.priority(),
                    l.owner(), newId, l.maxRange(), l.allowCrossDim()
                ));
            }
        }

        if (!toRemove.isEmpty()) {
            manager.removeLinksBulk(toRemove, sl);
            manager.addLinksBulk(toAdd, sl);
        }
    }
}