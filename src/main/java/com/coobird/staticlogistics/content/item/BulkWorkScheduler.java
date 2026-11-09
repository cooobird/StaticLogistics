package com.coobird.staticlogistics.content.item;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/// 玩家大操作的可继续任务。限制每 tick 工作量，不限制操作的节点总数。
@EventBusSubscriber(modid = "staticlogistics")
public final class BulkWorkScheduler {
    @FunctionalInterface
    public interface Step {
        boolean run(@Nullable ServerPlayer player, long deadline);
    }

    private static final class Task {
        private final Step step;
        private final Set<Object> locks;
        private final boolean retryFailures;
        private final boolean requiresOnlinePlayer;
        private int failures;
        private long retryAt;

        private Task(Step step, Set<?> locks, boolean retryFailures,
                     boolean requiresOnlinePlayer) {
            this.step = step;
            this.locks = Set.copyOf(locks);
            this.retryFailures = retryFailures;
            this.requiresOnlinePlayer = requiresOnlinePlayer;
        }

        private void failed(long gameTime) {
            failures++;
            retryAt = gameTime + Math.min(20L << Math.min(failures - 1, 5), 600L);
        }

        private void succeeded() {
            failures = 0;
            retryAt = 0;
        }

        private boolean shouldLogFailure() {
            return failures == 0 || (failures & failures - 1) == 0;
        }
    }

    private static final Map<MinecraftServer, LinkedHashMap<UUID, LinkedHashMap<Object, Task>>> TASKS = new WeakHashMap<>();

    private BulkWorkScheduler() {
    }

    public static boolean isPending(ServerPlayer player, Object key) {
        return isPending(player.server, player.getUUID(), key);
    }

    public static boolean isPending(MinecraftServer server, UUID owner, Object key) {
        var players = TASKS.get(server);
        return players != null && players.containsKey(owner)
            && players.get(owner).containsKey(key);
    }

    public static void replace(ServerPlayer player, Object key, Step step) {
        tryReplace(player, key, Set.of(), step);
    }

    public static boolean tryReplace(ServerPlayer player, Object key, Set<?> locks, Step step) {
        return tryReplace(player.server, player.getUUID(), key, locks, false, true, step);
    }

    public static boolean tryReplaceRecoveringOffline(MinecraftServer server,
                                                      UUID owner,
                                                      Object key,
                                                      Set<?> locks,
                                                      Step step) {
        return tryReplace(server, owner, key, locks, true, false, step);
    }

    private static boolean tryReplace(MinecraftServer server, UUID owner,
                                      Object key, Set<?> locks,
                                      boolean retryFailures,
                                      boolean requiresOnlinePlayer,
                                      Step step) {
        if (locks.stream().anyMatch(lock -> isLocked(server, lock))) return false;
        TASKS.computeIfAbsent(server, ignored -> new LinkedHashMap<>())
            .computeIfAbsent(owner, ignored -> new LinkedHashMap<>())
            .put(key, new Task(step, locks, retryFailures, requiresOnlinePlayer));
        return true;
    }

    public static boolean isLocked(MinecraftServer server, Object resource) {
        var players = TASKS.get(server);
        if (players == null) return false;
        return players.values().stream().flatMap(tasks -> tasks.values().stream())
            .anyMatch(task -> task.locks.contains(resource));
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var players = TASKS.get(event.getServer());
        if (players == null || players.isEmpty()) return;
        long deadline = System.nanoTime() + 2_000_000L;
        for (UUID id : List.copyOf(players.keySet())) {
            if (System.nanoTime() - deadline >= 0) break;
            var tasks = players.get(id);
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(id);
            if (tasks == null || tasks.isEmpty()) {
                players.remove(id);
                continue;
            }
            /// 跳过当前不可运行的任务，避免离线任务或退避任务堵住同一玩家的整个队列。
            long gameTime = event.getServer().overworld().getGameTime();
            var first = tasks.entrySet().stream()
                .filter(entry -> (player != null || !entry.getValue().requiresOnlinePlayer)
                    && gameTime >= entry.getValue().retryAt)
                .findFirst().orElse(null);
            if (first == null) continue;
            Task task = first.getValue();
            boolean done;
            try {
                done = task.step.run(player, deadline);
                task.succeeded();
            } catch (RuntimeException exception) {
                if (task.shouldLogFailure()) {
                    LogUtils.getLogger().error(
                        "Bulk logistics operation failed; retry count {}", task.failures + 1, exception);
                }
                if (task.failures == 0 && player != null) {
                    player.displayClientMessage(Component.translatable("msg.staticlogistics.bulk_failed"), true);
                }
                done = !task.retryFailures;
                if (!done) {
                    task.failed(gameTime);
                }
            }
            if (tasks.remove(first.getKey(), first.getValue()) && !done) tasks.put(first.getKey(), first.getValue());
            players.remove(id);
            if (!tasks.isEmpty()) players.put(id, tasks);
        }
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        TASKS.remove(event.getServer());
    }
}
