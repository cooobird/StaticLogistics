package com.coobird.staticlogistics.common.init;

import com.coobird.staticlogistics.core.StaticLink;
import com.coobird.staticlogistics.storage.LinkManager;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class SLCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sl")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("info")
                .executes(SLCommands::queryInfo)
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(SLCommands::queryInfoWithPos)))
            .then(Commands.literal("transfer")
                .then(Commands.argument("from", GameProfileArgument.gameProfile())
                    .then(Commands.argument("to", EntityArgument.player())
                        .executes(SLCommands::transferOwnership))
                    .then(Commands.literal("group")
                        .then(Commands.argument("groupId", StringArgumentType.string())
                            .then(Commands.argument("to", EntityArgument.player())
                                .executes(SLCommands::transferGroupOwnership))))))
            .then(Commands.literal("rename")
                .then(Commands.argument("owner", GameProfileArgument.gameProfile())
                    .then(Commands.argument("oldGroup", StringArgumentType.string())
                        .then(Commands.argument("newGroup", StringArgumentType.string())
                            .executes(SLCommands::renameGroup)))))
            .then(Commands.literal("cleanup")
                .then(Commands.argument("owner", GameProfileArgument.gameProfile())
                    .executes(SLCommands::cleanupLinks)))
        );
    }

    private static int queryInfo(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;

        HitResult hit = player.pick(5.0D, 0.0F, false);
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult) hit).getBlockPos();
            return handleInfo(context.getSource(), pos);
        }
        return 0;
    }

    private static int queryInfoWithPos(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        return handleInfo(context.getSource(), pos);
    }

    private static int handleInfo(CommandSourceStack source, BlockPos pos) {
        LinkManager manager = LinkManager.get(source.getLevel());

        source.sendSuccess(() -> Component.translatable("commands.staticlogistics.info.header", pos.toShortString())
            .withStyle(ChatFormatting.GOLD), false);

        boolean found = false;
        for (Direction dir : Direction.values()) {
            long key = manager.posToKey(pos, dir);
            List<StaticLink> outLinks = manager.getLinksByKey(key);

            if (!outLinks.isEmpty()) {
                found = true;
                Map<String, List<StaticLink>> grouped = outLinks.stream().collect(Collectors.groupingBy(StaticLink::groupId));

                for (var entry : grouped.entrySet()) {
                    String groupId = entry.getKey();
                    int count = entry.getValue().size();
                    String ownerName = entry.getValue().getFirst().ownerName();

                    source.sendSuccess(() -> Component.translatable("commands.staticlogistics.info.line_format",
                        dir.name(),
                        Component.translatable("msg.staticlogistics.group_display", groupId).withStyle(ChatFormatting.AQUA),
                        Component.translatable("msg.staticlogistics.target_count", count).withStyle(ChatFormatting.WHITE),
                        Component.translatable("msg.staticlogistics.owner_display", ownerName).withStyle(ChatFormatting.YELLOW)
                    ), false);
                }
            }
        }
        if (!found) source.sendFailure(Component.translatable("commands.staticlogistics.info.no_links"));
        return 1;
    }

    private static int transferOwnership(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "from");
        GameProfile fromProfile = profiles.iterator().next();
        ServerPlayer toPlayer = EntityArgument.getPlayer(context, "to");
        ServerLevel level = context.getSource().getLevel();
        LinkManager manager = LinkManager.get(level);

        List<StaticLink> targets = manager.getLinksList().stream()
            .filter(l -> l.owner().equals(fromProfile.getId())).toList();

        for (StaticLink link : targets) {
            manager.updateLinkOwner(link, toPlayer.getUUID(), toPlayer.getGameProfile().getName(), level);
        }

        context.getSource().sendSuccess(() -> Component.translatable("commands.staticlogistics.transfer.success", targets.size(), fromProfile.getName(), toPlayer.getScoreboardName()), true);
        return targets.size();
    }

    private static int transferGroupOwnership(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<GameProfile> fromProfiles = GameProfileArgument.getGameProfiles(context, "from");
        GameProfile fromProfile = fromProfiles.iterator().next();
        String groupId = StringArgumentType.getString(context, "groupId");
        ServerPlayer toPlayer = EntityArgument.getPlayer(context, "to");

        ServerLevel level = context.getSource().getLevel();
        LinkManager manager = LinkManager.get(level);

        List<StaticLink> targets = manager.getLinksList().stream()
            .filter(l -> l.owner().equals(fromProfile.getId()) && l.groupId().equals(groupId))
            .toList();

        if (targets.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.staticlogistics.transfer.group_not_found"));
            return 0;
        }

        for (StaticLink link : targets) {
            manager.updateLinkOwner(link, toPlayer.getUUID(), toPlayer.getGameProfile().getName(), level);
        }

        context.getSource().sendSuccess(() -> Component.translatable("commands.staticlogistics.transfer.group_success",
            fromProfile.getName(), groupId, targets.size(), toPlayer.getScoreboardName()), true);

        return targets.size();
    }

    private static int renameGroup(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "owner");
        GameProfile profile = profiles.iterator().next();
        String oldGroup = StringArgumentType.getString(context, "oldGroup");
        String newGroup = StringArgumentType.getString(context, "newGroup");
        ServerLevel level = context.getSource().getLevel();
        LinkManager manager = LinkManager.get(level);

        List<StaticLink> toChange = manager.getLinksList().stream()
            .filter(l -> l.owner().equals(profile.getId()) && l.groupId().equals(oldGroup))
            .toList();

        if (toChange.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.staticlogistics.rename.not_found"));
            return 0;
        }

        List<StaticLink> newLinks = toChange.stream().map(l -> new StaticLink(
            UUID.randomUUID(), l.sourcePos(), l.sourceFace(), l.sourceDimension(),
            l.destPos(), l.destFace(), l.destDimension(),
            l.transferFlags(), l.priority(), l.owner(), l.ownerName(), newGroup,
            l.tier(), l.allowCrossDim()
        )).toList();

        manager.removeLinksBulk(toChange, level, null);
        manager.addLinksBulk(newLinks, level, null);

        context.getSource().sendSuccess(() -> Component.translatable("commands.staticlogistics.rename.success", oldGroup, newGroup, profile.getName()), true);
        return newLinks.size();
    }

    private static int cleanupLinks(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "owner");
        GameProfile profile = profiles.iterator().next();
        ServerLevel level = context.getSource().getLevel();
        LinkManager manager = LinkManager.get(level);

        List<StaticLink> toRemove = manager.getLinksList().stream()
            .filter(l -> l.owner().equals(profile.getId())).toList();

        int removed = toRemove.size();
        manager.removeLinksBulk(toRemove, level, null);
        context.getSource().sendSuccess(() -> Component.translatable("commands.staticlogistics.cleanup.success", removed, profile.getName()), true);
        return removed;
    }
}