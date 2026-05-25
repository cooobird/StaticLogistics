package com.coobird.staticlogistics.registry;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.filter.MatchStrategy;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import com.coobird.staticlogistics.core.service.GroupService;
import com.coobird.staticlogistics.filter.registry.ComponentMatchStrategyRegistry;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.ContainerConfig;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.TransferLogManager;
import com.coobird.staticlogistics.transfer.TransferLogManager.NodeStats;
import com.coobird.staticlogistics.transfer.TransferLogManager.TransferEntry;
import com.coobird.staticlogistics.transfer.TransferLogManager.TypeStats;
import com.coobird.staticlogistics.util.LogisticsConstants;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 注册模组的所有命令（/sl），包括信息查询、所有权转移、重命名、清理、策略列表和统计。
 */
public class SLCommands {
    /**
     * 注册 /sl 命令及其所有子命令
     */
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
            .then(Commands.literal("stats")
                .executes(SLCommands::showStatsOverview)
                .then(Commands.literal("recent")
                    .executes(ctx -> showRecentTransfers(ctx, 20)))
                .then(Commands.literal("top")
                    .executes(ctx -> showTopNodes(ctx, 10)))
                .then(Commands.literal("reset")
                    .executes(SLCommands::resetStats)))
        );
    }

    /**
     * 为命令参数提供已有的分组名作为建议
     */
    private static CompletableFuture<Suggestions> suggestGroups(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        MinecraftServer server = context.getSource().getServer();
        return SharedSuggestionProvider.suggest(GlobalLogisticsManager.get(server).getActiveGroups(), builder);
    }

    /**
     * 刷新单个节点的本地缓存并同步到客户端
     */
    private static void refreshNode(LinkManager manager, long key, FaceConfigComposite config) {
        BlockPos pos = LogisticsNode.keyToPos(key);
        Direction face = LogisticsNode.keyToFace(key);
        manager.refreshLocalCache(key, pos, face, config);
        manager.syncConfigToClients(pos);
        manager.markDirtyBatch(() -> {
        });
    }

    /**
     * 显示指定位置的容器配置和面配置信息
     */
    private static int handleInfo(CommandSourceStack source, BlockPos pos) {
        ServerLevel level = source.getLevel();
        LinkManager manager = LinkManager.get(level);

        ContainerConfig container = manager.getContainerConfig(pos);
        if (container != null) {
            source.sendSuccess(() -> Component.translatable("commands.staticlogistics.info.container").withStyle(ChatFormatting.GOLD), false);
            source.sendSuccess(() -> Component.translatable("commands.staticlogistics.info.speed", container.getSpeedMultiplier()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
            source.sendSuccess(() -> Component.translatable("commands.staticlogistics.info.range", container.getRangeMultiplier()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
            source.sendSuccess(() -> Component.translatable("commands.staticlogistics.info.stack", container.getStackMultiplier()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
            source.sendSuccess(() -> Component.translatable("commands.staticlogistics.info.dimension", container.isDimensionEffective()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
            source.sendSuccess(() -> Component.translatable("commands.staticlogistics.info.upgrades_title").withStyle(ChatFormatting.YELLOW), false);
            for (int i = 0; i < container.getUpgrades().getSlots(); i++) {
                ItemStack stack = container.getUpgrades().getStackInSlot(i);
                if (!stack.isEmpty()) {
                    int finalI = i;
                    source.sendSuccess(() -> Component.translatable("commands.staticlogistics.info.slot_format",
                        finalI, stack.getHoverName().getString(), stack.getCount()).withStyle(ChatFormatting.GRAY), false);
                }
            }
        } else {
            source.sendSuccess(() -> Component.translatable("commands.staticlogistics.info.no_container_config").withStyle(ChatFormatting.RED), false);
        }

        source.sendSuccess(() -> Component.translatable("commands.staticlogistics.info.face_configs_title").withStyle(ChatFormatting.GOLD), false);
        boolean found = false;
        for (Direction dir : Direction.values()) {
            long key = LinkManager.posToKey(pos, dir);
            FaceConfigComposite config = manager.getFaceConfig(key);
            if (config != null && !config.isDefault()) {
                found = true;
                source.sendSuccess(() -> Component.translatable("commands.staticlogistics.info.face_direction", dir.getName()).withStyle(ChatFormatting.AQUA), false);
                source.sendSuccess(() -> Component.translatable("commands.staticlogistics.info.group",
                    config.faceConfig.getGroupId()).withStyle(ChatFormatting.WHITE), false);
                UUID ownerUuid = config.faceConfig.getOwner();
                Component ownerText = (ownerUuid == null)
                    ? Component.translatable("msg.staticlogistics.unknown_owner")
                    : Component.literal(config.faceConfig.getOwnerName());
                source.sendSuccess(() -> Component.translatable("commands.staticlogistics.info.owner", ownerText.copy()), false);

                source.sendSuccess(() -> Component.translatable("commands.staticlogistics.info.global_input",
                    config.isGlobalInputEnabled() ? Component.translatable("commands.staticlogistics.info.enabled") : Component.translatable("commands.staticlogistics.info.disabled")
                ).withStyle(ChatFormatting.GRAY), false);
                source.sendSuccess(() -> Component.translatable("commands.staticlogistics.info.global_output",
                    config.isGlobalOutputEnabled() ? Component.translatable("commands.staticlogistics.info.enabled") : Component.translatable("commands.staticlogistics.info.disabled")
                ).withStyle(ChatFormatting.GRAY), false);
                source.sendSuccess(() -> Component.translatable("commands.staticlogistics.info.input_channel", config.linkConfig.getInputChannel()).withStyle(ChatFormatting.GRAY), false);
                source.sendSuccess(() -> Component.translatable("commands.staticlogistics.info.output_channel", config.linkConfig.getOutputChannel()).withStyle(ChatFormatting.GRAY), false);
                source.sendSuccess(() -> Component.translatable("commands.staticlogistics.info.strategy", config.linkConfig.getStrategy().getDisplayName()).withStyle(ChatFormatting.GRAY), false);
                source.sendSuccess(() -> Component.translatable("commands.staticlogistics.info.priority", config.linkConfig.getPriority()).withStyle(ChatFormatting.GRAY), false);
                source.sendSuccess(() -> Component.translatable("commands.staticlogistics.info.types_mask", config.getSelectedTypesMask()).withStyle(ChatFormatting.GRAY), false);
                source.sendSuccess(() -> Component.translatable("commands.staticlogistics.info.linked_nodes", config.getLinkedNodes().size()).withStyle(ChatFormatting.DARK_GRAY), false);
            }
        }
        if (!found) {
            source.sendFailure(Component.translatable("commands.staticlogistics.info.no_links"));
        }
        return 1;
    }

    /**
     * /sl transfer <from> <to>：把某个玩家的所有节点所有权转移给另一个玩家
     */
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

    /**
     * /sl transfer <from> group <groupId> <to>：转移某个分组下的节点所有权
     */
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

    /**
     * /sl rename <owner> <oldGroup> <newGroup>：重命名某个玩家的分组
     */
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

    /**
     * /sl cleanup <owner>：删除某个玩家的所有节点配置
     */
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

    /**
     * /sl info：查询玩家正在看的那一面的物流配置详情
     */
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

    /**
     * /sl info <pos>：查询指定坐标的物流配置详情
     */
    private static int queryInfoWithPos(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        return handleInfo(context.getSource(), pos);
    }

    /**
     * /sl strategies [page]：分页列出所有注册的匹配策略
     */
    private static int listStrategies(CommandContext<CommandSourceStack> ctx, int page) {
        Map<ResourceLocation, MatchStrategy> all = ComponentMatchStrategyRegistry.getAllStrategies();
        int totalPages = (all.size() + LogisticsConstants.UI.STRATEGIES_PER_PAGE - 1) / LogisticsConstants.UI.STRATEGIES_PER_PAGE;

        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;
        final int currentPage = page;

        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.translatable(
            "commands.staticlogistics.strategies.header", currentPage, totalPages
        ).withStyle(ChatFormatting.GOLD), false);

        int start = (currentPage - 1) * LogisticsConstants.UI.STRATEGIES_PER_PAGE;
        int end = Math.min(start + LogisticsConstants.UI.STRATEGIES_PER_PAGE, all.size());
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

    /**
     * /sl stats：显示传输统计总览（总次数、总量、失败数、按类型分）
     */
    private static int showStatsOverview(CommandContext<CommandSourceStack> ctx) {
        var mgr = TransferLogManager.get();
        var source = ctx.getSource();

        source.sendSuccess(() -> Component.translatable("commands.staticlogistics.stats.header").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable("commands.staticlogistics.stats.total", mgr.getTotalTransfers()).withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.translatable("commands.staticlogistics.stats.amount", mgr.getTotalAmount()).withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.translatable("commands.staticlogistics.stats.failed", mgr.getFailedTransfers()).withStyle(ChatFormatting.RED), false);
        source.sendSuccess(() -> Component.translatable("commands.staticlogistics.stats.log_size", mgr.getLogSize()).withStyle(ChatFormatting.GRAY), false);

        source.sendSuccess(() -> Component.translatable("commands.staticlogistics.stats.by_type").withStyle(ChatFormatting.AQUA), false);
        for (var entry : mgr.getPerTypeStats().entrySet()) {
            TypeStats ts = entry.getValue();
            String typeName = entry.getKey();
            long count = ts.count;
            long amount = ts.totalAmount;
            source.sendSuccess(() -> Component.translatable("commands.staticlogistics.stats.type_line", typeName, count, amount)
                .withStyle(ChatFormatting.GRAY), false);
        }

        source.sendSuccess(() -> Component.translatable("commands.staticlogistics.stats.sub_help").withStyle(ChatFormatting.DARK_GRAY), false);
        return 1;
    }

    /**
     * /sl stats recent：显示最近N条传输记录
     */
    private static int showRecentTransfers(CommandContext<CommandSourceStack> ctx, int count) {
        var mgr = TransferLogManager.get();
        var source = ctx.getSource();
        var recent = mgr.getRecent(count);
        int size = recent.size();

        source.sendSuccess(() -> Component.translatable("commands.staticlogistics.stats.recent_header", size).withStyle(ChatFormatting.AQUA), false);
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("HH:mm:ss");
        for (TransferEntry e : recent) {
            String time = fmt.format(new java.util.Date(e.timestamp()));
            String srcStr = String.format("%d,%d,%d", e.sx(), e.sy(), e.sz());
            String dstStr = String.format("%d,%d,%d", e.tx(), e.ty(), e.tz());
            String mark = e.success() ? "\u2713" : "\u2717";
            ChatFormatting style = e.success() ? ChatFormatting.GRAY : ChatFormatting.RED;
            source.sendSuccess(() -> Component.translatable("commands.staticlogistics.stats.recent_line",
                time, e.sourceFace(), srcStr, e.targetFace(), dstStr, e.typeName(), e.amount(), mark).withStyle(style), false);
        }
        return 1;
    }

    /**
     * /sl stats top：显示发送/接收最多的前N个节点排行
     */
    private static int showTopNodes(CommandContext<CommandSourceStack> ctx, int count) {
        var mgr = TransferLogManager.get();
        var source = ctx.getSource();

        source.sendSuccess(() -> Component.translatable("commands.staticlogistics.stats.top_send").withStyle(ChatFormatting.GOLD), false);
        int[] rArr = {1};
        for (var entry : mgr.getTopNodes(count, true)) {
            NodeStats ns = entry.getValue();
            int r = rArr[0];
            String posStr = String.format("%d,%d,%d", ns.posX, ns.posY, ns.posZ);
            long sc = ns.sentCount;
            long sa = ns.sentAmount;
            source.sendSuccess(() -> Component.translatable("commands.staticlogistics.stats.top_line", r, ns.face, posStr, sc, sa)
                .withStyle(ChatFormatting.GRAY), false);
            rArr[0]++;
        }

        source.sendSuccess(() -> Component.translatable("commands.staticlogistics.stats.top_recv").withStyle(ChatFormatting.GOLD), false);
        rArr[0] = 1;
        for (var entry : mgr.getTopNodes(count, false)) {
            NodeStats ns = entry.getValue();
            int r = rArr[0];
            String posStr = String.format("%d,%d,%d", ns.posX, ns.posY, ns.posZ);
            long rc = ns.receivedCount;
            long ra = ns.receivedAmount;
            source.sendSuccess(() -> Component.translatable("commands.staticlogistics.stats.top_recv_line", r, ns.face, posStr, rc, ra)
                .withStyle(ChatFormatting.GRAY), false);
            rArr[0]++;
        }
        return 1;
    }

    /**
     * /sl stats reset：重置所有传输统计数据
     */
    private static int resetStats(CommandContext<CommandSourceStack> ctx) {
        TransferLogManager.get().reset();
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.staticlogistics.stats.reset").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}