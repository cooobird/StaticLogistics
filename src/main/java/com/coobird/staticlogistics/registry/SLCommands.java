package com.coobird.staticlogistics.registry;

import com.coobird.staticlogistics.api.filter.MatchStrategy;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import com.coobird.staticlogistics.core.service.GroupService;
import com.coobird.staticlogistics.filter.registry.ComponentMatchStrategyRegistry;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SLCommands {
    private static final int STRATEGIES_PER_PAGE = 8;

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
                            .suggests(SLCommands::suggestGroups)
                            .then(Commands.argument("to", EntityArgument.player())
                                .executes(SLCommands::transferGroupOwnership))))))
            .then(Commands.literal("rename")
                .then(Commands.argument("owner", GameProfileArgument.gameProfile())
                    .then(Commands.argument("oldGroup", StringArgumentType.string())
                        .suggests(SLCommands::suggestGroups)
                        .then(Commands.argument("newGroup", StringArgumentType.string())
                            .executes(SLCommands::renameGroup)))))
            .then(Commands.literal("cleanup")
                .then(Commands.argument("owner", GameProfileArgument.gameProfile())
                    .executes(SLCommands::cleanupNodes)))
            .then(Commands.literal("strategies")
                .executes(ctx -> listStrategies(ctx, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .executes(ctx -> listStrategies(ctx, IntegerArgumentType.getInteger(ctx, "page")))))
        );
    }

    private static CompletableFuture<Suggestions> suggestGroups(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        MinecraftServer server = context.getSource().getServer();
        return SharedSuggestionProvider.suggest(GlobalLogisticsManager.get(server).getActiveGroups(), builder);
    }

    private static void refreshNode(LinkManager manager, long key, FaceConfigComposite config) {
        BlockPos pos = BlockPos.of(key >> 3);
        Direction face = Direction.from3DDataValue((int) (key & 0x7));
        manager.refreshLocalCache(key, pos, face, config);
        manager.syncConfigToClients(pos);
        manager.setDirty();
    }

    private static int handleInfo(CommandSourceStack source, BlockPos pos) {
        ServerLevel level = source.getLevel();
        LinkManager manager = LinkManager.get(level);

        source.sendSuccess(() -> Component.translatable("commands.staticlogistics.info.header", pos.toShortString()).withStyle(ChatFormatting.GOLD), false);

        boolean found = false;
        for (Direction dir : Direction.values()) {
            long key = LinkManager.posToKey(pos, dir);
            FaceConfigComposite config = manager.getFaceConfig(key);

            if (config != null && !config.isDefault()) {
                found = true;
                source.sendSuccess(() -> Component.literal("[" + dir.getName() + "]").withStyle(ChatFormatting.AQUA), false);
                source.sendSuccess(() -> Component.translatable("commands.staticlogistics.info.group",
                    Component.literal(config.faceConfig.getGroupId()).withStyle(ChatFormatting.WHITE)), false);

                UUID ownerUuid = config.faceConfig.getOwner();
                Component ownerText;
                if (ownerUuid == null) {
                    ownerText = Component.translatable("msg.staticlogistics.unknown_owner");
                } else {
                    ownerText = Component.literal(config.faceConfig.getOwnerName());
                }
                source.sendSuccess(() -> Component.translatable("commands.staticlogistics.info.owner",
                    ownerText.copy().withStyle(ChatFormatting.YELLOW)), false);

                source.sendSuccess(() -> Component.translatable("commands.staticlogistics.info.speed",
                    config.sharedContainerConfig.getSpeedMultiplier()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
            }
        }

        if (!found) source.sendFailure(Component.translatable("commands.staticlogistics.info.not_found"));
        return 1;
    }

    private static int transferOwnership(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "from");
        GameProfile fromProfile = profiles.iterator().next();
        ServerPlayer toPlayer = EntityArgument.getPlayer(context, "to");
        MinecraftServer server = context.getSource().getServer();

        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            LinkManager manager = LinkManager.get(level);
            for (long key : manager.getAllConfigKeys()) {
                FaceConfigComposite config = manager.getFaceConfig(key);
                if (config != null && fromProfile.getId().equals(config.faceConfig.getOwner())) {
                    config.faceConfig.setOwner(toPlayer.getUUID(), toPlayer.getGameProfile().getName());
                    refreshNode(manager, key, config);
                    count++;
                }
            }
        }

        int finalCount = count;
        context.getSource().sendSuccess(() -> Component.translatable("commands.staticlogistics.transfer.success",
            finalCount, fromProfile.getName(), toPlayer.getDisplayName()), true);
        return count;
    }

    private static int transferGroupOwnership(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "from");
        GameProfile fromProfile = profiles.iterator().next();
        String groupId = StringArgumentType.getString(context, "groupId");
        ServerPlayer toPlayer = EntityArgument.getPlayer(context, "to");
        MinecraftServer server = context.getSource().getServer();

        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            LinkManager manager = LinkManager.get(level);
            for (long key : manager.getAllConfigKeys()) {
                FaceConfigComposite config = manager.getFaceConfig(key);
                if (config != null && fromProfile.getId().equals(config.faceConfig.getOwner()) && groupId.equals(config.faceConfig.getGroupId())) {
                    config.faceConfig.setOwner(toPlayer.getUUID(), toPlayer.getGameProfile().getName());
                    refreshNode(manager, key, config);
                    count++;
                }
            }
        }

        int finalCount = count;
        context.getSource().sendSuccess(() -> Component.translatable("commands.staticlogistics.transfer.group_success",
            finalCount, groupId, toPlayer.getDisplayName()), true);
        return count;
    }

    private static int renameGroup(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "owner");
        GameProfile profile = profiles.iterator().next();
        String oldGroup = StringArgumentType.getString(context, "oldGroup");
        String newGroup = StringArgumentType.getString(context, "newGroup");

        ServerLevel level = context.getSource().getLevel();
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;

        GlobalLogisticsManager globalManager = GlobalLogisticsManager.get(level.getServer());
        GroupService.renameGroup(level, player, oldGroup, newGroup, globalManager);

        context.getSource().sendSuccess(() -> Component.translatable("commands.staticlogistics.rename.success",
            oldGroup, newGroup, profile.getName()), true);
        return 1;
    }

    private static int cleanupNodes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "owner");
        GameProfile profile = profiles.iterator().next();
        MinecraftServer server = context.getSource().getServer();

        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            LinkManager manager = LinkManager.get(level);
            for (long key : new ArrayList<>(manager.getAllConfigKeys())) {
                FaceConfigComposite config = manager.getFaceConfig(key);
                if (config != null && profile.getId().equals(config.faceConfig.getOwner())) {
                    manager.removeFaceConfig(key);
                    count++;
                }
            }
        }

        int finalCount = count;
        context.getSource().sendSuccess(() -> Component.translatable("commands.staticlogistics.cleanup.success",
            finalCount, profile.getName()), true);
        return count;
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

    private static int listStrategies(CommandContext<CommandSourceStack> ctx, int page) {
        Map<ResourceLocation, MatchStrategy> all = ComponentMatchStrategyRegistry.getAllStrategies();
        int totalPages = (all.size() + STRATEGIES_PER_PAGE - 1) / STRATEGIES_PER_PAGE;

        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;
        final int currentPage = page;

        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.translatable(
            "commands.staticlogistics.strategies.header", currentPage, totalPages
        ).withStyle(ChatFormatting.GOLD), false);

        int start = (currentPage - 1) * STRATEGIES_PER_PAGE;
        int end = Math.min(start + STRATEGIES_PER_PAGE, all.size());
        int i = 0;
        for (var entry : all.entrySet()) {
            if (i >= start && i < end) {
                ResourceLocation id = entry.getKey();
                MatchStrategy strategy = entry.getValue();
                source.sendSuccess(() -> Component.translatable(
                    "commands.staticlogistics.strategies.line",
                    id.toString(),
                    Component.translatable("match_strategy.staticlogistics." + strategy.name().toLowerCase())
                ).withStyle(ChatFormatting.GRAY), false);
            }
            i++;
        }

        if (currentPage < totalPages) {
            source.sendSuccess(() -> Component.translatable(
                "commands.staticlogistics.strategies.next_page", currentPage + 1
            ).withStyle(ChatFormatting.AQUA), false);
        }

        return 1;
    }
}