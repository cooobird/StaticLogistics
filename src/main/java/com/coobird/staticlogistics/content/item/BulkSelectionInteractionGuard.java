package com.coobird.staticlogistics.content.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 记录批量选点右击，阻止同一次原版交互继续打开容器或配置界面。
 */
public final class BulkSelectionInteractionGuard {
    private static final long MAX_AGE_TICKS = 2L;
    private static final Map<MinecraftServer, Map<UUID, Interaction>> INTERACTIONS = new WeakHashMap<>();

    private BulkSelectionInteractionGuard() {
    }

    public static void mark(ServerPlayer player, BlockPos pos, Direction face) {
        if (player == null || pos == null || face == null) return;
        INTERACTIONS.computeIfAbsent(player.server, ignored -> new HashMap<>())
            .put(player.getUUID(), new Interaction(pos.immutable(), face, player.serverLevel().getGameTime()));
    }

    public static boolean matches(ServerPlayer player, BlockPos pos, Direction face) {
        if (player == null || pos == null || face == null) return false;
        Map<UUID, Interaction> interactions = INTERACTIONS.get(player.server);
        if (interactions == null) return false;
        Interaction interaction = interactions.get(player.getUUID());
        if (interaction == null) return false;
        long age = player.serverLevel().getGameTime() - interaction.gameTime();
        if (age < 0L || age > MAX_AGE_TICKS) {
            interactions.remove(player.getUUID());
            return false;
        }
        return interaction.pos().equals(pos) && interaction.face() == face;
    }

    public static void release(MinecraftServer server) {
        if (server != null) INTERACTIONS.remove(server);
    }

    private record Interaction(BlockPos pos, Direction face, long gameTime) {
    }
}
