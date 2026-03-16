package com.coobird.staticlogistics.storage;

import com.coobird.staticlogistics.compat.ModIds;
import com.coobird.staticlogistics.core.StaticLink;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.TeamRank;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

public class GroupService {

    public static boolean canAccess(StaticLink link, Player actor) {
        if (link.owner().equals(actor.getUUID())) return true;
        if (isFtbLoaded()) {
            return checkFTBTeamAlliance(link.owner(), actor.getUUID());
        }
        return false;
    }

    public static boolean canModify(StaticLink link, Player actor) {
        if (link.owner().equals(actor.getUUID())) return true;
        return isFtbLoaded() && isTeamAdminOf(link.owner(), actor.getUUID());
    }

    public static boolean isFtbLoaded() {
        return ModList.get().isLoaded(ModIds.FTB_TEAMS);
    }

    private static boolean checkFTBTeamAlliance(UUID owner, UUID actor) {
        try {
            var manager = FTBTeamsAPI.api().getManager();
            if (manager == null) return false;

            if (manager.arePlayersInSameTeam(owner, actor)) return true;

            return manager.getTeamForPlayerID(owner).map(team ->
                team.getRankForPlayer(actor).getPower() >= TeamRank.ALLY.getPower()
            ).orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isTeamAdminOf(UUID owner, UUID actor) {
        try {
            var manager = FTBTeamsAPI.api().getManager();
            if (manager == null) return false;

            return manager.getTeamForPlayerID(owner).map(team -> {
                TeamRank rank = team.getRankForPlayer(actor);
                return rank.getPower() >= TeamRank.OFFICER.getPower();
            }).orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    public static Set<String> getGroupsForPlayer(Level level, Player actor) {
        LinkManager manager = LinkManager.get(level);
        if (manager == null) return Collections.emptySet();

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
                } else if (FMLEnvironment.dist.isClient()) {
                    var client = FTBTeamsAPI.api().getClientManager();
                    if (client != null && client.isValid() && client.selfTeam() != null) {
                        for (UUID memberId : client.selfTeam().getMembers()) {
                            allGroups.addAll(manager.getGroupsByOwner(memberId));
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return allGroups;
    }

    public static void deleteGroup(Level level, Player actor, String groupId) {
        if (!(level instanceof ServerLevel sl)) return;
        LinkManager manager = LinkManager.get(sl);
        if (manager == null) return;

        List<StaticLink> toRemove = manager.getLinksList().stream()
            .filter(l -> l.groupId().equals(groupId))
            .toList();

        if (!toRemove.isEmpty()) {
            LinkManager.ActionResult result = manager.removeLinksBulk(toRemove, sl, actor);
            if (!result.success() && result.message() != null) {
                actor.displayClientMessage(result.message(), false);
            }
        }
    }

    public static void renameGroup(Level level, Player actor, String oldId, String newId) {
        if (!(level instanceof ServerLevel sl) || oldId.equals(newId) || newId.isBlank()) return;
        LinkManager manager = LinkManager.get(sl);
        if (manager == null) return;

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
            return getNextGroupId(String.valueOf(val + 1), existing);
        } catch (NumberFormatException e) {
            String next = currentId + "_copy";
            return existing.contains(next) ? next + "_" + existing.size() : next;
        }
    }

    public static void syncToTeamMembers(ServerPlayer player, CustomPacketPayload payload) {
        if (isFtbLoaded()) {
            try {
                var manager = FTBTeamsAPI.api().getManager();
                if (manager != null) {
                    manager.getTeamForPlayerID(player.getUUID()).ifPresentOrElse(team -> {
                        for (UUID memberId : team.getMembers()) {
                            ServerPlayer member = player.server.getPlayerList().getPlayer(memberId);
                            if (member != null) {
                                PacketDistributor.sendToPlayer(member, payload);
                            }
                        }
                    }, () -> {
                        PacketDistributor.sendToPlayersInDimension(player.serverLevel(), payload);
                    });
                    return;
                }
            } catch (Exception e) {
            }
        }
        PacketDistributor.sendToAllPlayers(payload);
    }
}