package com.coobird.staticlogistics.common.init;

import com.coobird.staticlogistics.core.StaticLink;
import com.coobird.staticlogistics.storage.LinkManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;

public class SLCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sl")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("info").executes(SLCommands::queryInfo))
            .then(Commands.literal("transfer")
                .then(Commands.argument("from", StringArgumentType.string())
                    .then(Commands.argument("to", EntityArgument.player())
                        .executes(SLCommands::transferOwnership))))
            .then(Commands.literal("cleanup")
                .then(Commands.argument("target", StringArgumentType.string())
                    .executes(SLCommands::cleanupLinks)))
        );
    }

    private static int queryInfo(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        HitResult hit = player.pick(5.0D, 0.0F, false);
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult) hit).getBlockPos();
            LinkManager manager = LinkManager.get(source.getLevel());

            source.sendSuccess(() -> Component.literal("--- Logistics Info at " + pos.toShortString() + " ---").withStyle(ChatFormatting.GOLD), false);

            boolean found = false;
            for (StaticLink link : manager.getLinksList()) {
                if (link.sourcePos().equals(pos) || link.destPos().equals(pos)) {
                    found = true;
                    String role = link.sourcePos().equals(pos) ? "[Source]" : "[Dest]";
                    source.sendSuccess(() -> Component.literal(role + " Owner: " + link.ownerName() + " | Group: " + link.groupId()).withStyle(ChatFormatting.GRAY), false);
                }
            }
            if (!found) source.sendFailure(Component.literal("No links found at this position."));
        }
        return 1;
    }

    private static int transferOwnership(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String fromName = StringArgumentType.getString(context, "from");
        ServerPlayer toPlayer = EntityArgument.getPlayer(context, "to");
        ServerLevel level = context.getSource().getLevel();
        LinkManager manager = LinkManager.get(level);

        int count = 0;
        for (StaticLink link : new ArrayList<>(manager.getLinksList())) {
            if (link.ownerName().equalsIgnoreCase(fromName)) {
                manager.updateLinkOwner(link, toPlayer.getUUID(), toPlayer.getGameProfile().getName(), level);
                count++;
            }
        }

        int finalCount = count;
        context.getSource().sendSuccess(() -> Component.literal("Transferred " + finalCount + " links from " + fromName + " to " + toPlayer.getScoreboardName()), true);
        return count;
    }

    private static int cleanupLinks(CommandContext<CommandSourceStack> context) {
        String targetName = StringArgumentType.getString(context, "target");
        ServerLevel level = context.getSource().getLevel();
        LinkManager manager = LinkManager.get(level);

        List<StaticLink> toRemove = manager.getLinksList().stream()
            .filter(l -> l.ownerName().equalsIgnoreCase(targetName)).toList();

        int removed = manager.removeLinksBulk(toRemove, level, context.getSource().getPlayer());
        context.getSource().sendSuccess(() -> Component.literal("Successfully removed " + removed + " links owned by " + targetName), true);
        return removed;
    }
}