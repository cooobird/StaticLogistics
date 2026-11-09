package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.StaticLogistics;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/// 仅按权威状态重试派生数据修复，不重放传输或物品栏操作。
@EventBusSubscriber(modid = StaticLogistics.MODID)
public final class NodeReconciliationQueue {
    private static final Map<MinecraftServer, State> PENDING = new IdentityHashMap<>();

    private record Pending(Object key, Runnable reconcile, int failures, long dueTick, long sequence) {
    }

    private static final class State {
        final Map<Object, Pending> byKey = new LinkedHashMap<>();
        final TreeSet<Pending> due = new TreeSet<>(
            Comparator.comparingLong(Pending::dueTick).thenComparingLong(Pending::sequence));
        final Set<Object> running = new HashSet<>();
        long sequence;

        Pending remove(Object key) {
            Pending previous = byKey.remove(key);
            if (previous != null) due.remove(previous);
            return previous;
        }

        void schedule(Object key, Runnable reconcile, int failures, long dueTick) {
            Pending previous = remove(key);
            /// 新的权威状态可以替换任务内容，但不得把已经到期的刷新不断往后推。
            long effectiveDue = failures == 0 && previous != null
                ? Math.min(previous.dueTick(), dueTick) : dueTick;
            Pending pending = new Pending(key, reconcile, failures, effectiveDue, ++sequence);
            byKey.put(key, pending);
            due.add(pending);
        }
    }

    public static void execute(MinecraftServer server, Object key, Runnable reconcile) {
        if (!server.isSameThread()) throw new IllegalStateException("Reconciliation must run on the server thread");
        State state = PENDING.computeIfAbsent(server, ignored -> new State());
        Pending previous = state.remove(key);
        if (state.running.contains(key)) {
            /// 同键重入只更新待执行任务，避免递归；最新任务不继承旧任务的失败退避。
            state.schedule(key, reconcile, 0, server.overworld().getGameTime() + 1);
            return;
        }
        run(server, state, key, reconcile, previous == null ? 0 : previous.failures());
    }

    public static void schedule(MinecraftServer server, Object key, Runnable reconcile) {
        if (!server.isSameThread())
            throw new IllegalStateException("Reconciliation must be scheduled on the server thread");
        if (key == null || reconcile == null) {
            throw new IllegalArgumentException("Reconciliation key and action are required");
        }
        State state = PENDING.computeIfAbsent(server, ignored -> new State());
        state.schedule(key, reconcile, 0, server.overworld().getGameTime() + 1L);
    }

    private static void run(MinecraftServer server, State state, Object key, Runnable reconcile, int previousFailures) {
        state.running.add(key);
        try {
            reconcile.run();
        } catch (RuntimeException failure) {
            /// 执行期间已经出现更晚的同键请求时，旧任务不再占用重试位置。
            if (!state.byKey.containsKey(key)) {
                int failures = Math.min(30, previousFailures + 1);
                long delay = Math.min(1200L, 20L << Math.min(6, failures - 1));
                state.schedule(key, reconcile, failures, server.overworld().getGameTime() + delay);
                if (failures == 1 || failures == 6) {
                    LogUtils.getLogger().error("Node reconciliation pending for {}; retrying authoritative state, attempt {}", key, failures, failure);
                }
            }
        } finally {
            state.running.remove(key);
            /// 其他节点的嵌套更新结束时，不能移除仍有外层任务执行的服务器状态。
            if (state.byKey.isEmpty() && state.running.isEmpty()) PENDING.remove(server, state);
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        long started = System.nanoTime();
        for (int processed = 0; processed < 4; processed++) {
            State state = PENDING.get(server);
            if (state == null || state.due.isEmpty()) break;
            if (processed > 0 && System.nanoTime() - started >= 2_000_000L) break;
            Pending pending = state.due.first();
            if (pending.dueTick() > server.overworld().getGameTime()) break;
            execute(server, pending.key(), pending.reconcile());
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        PENDING.remove(event.getServer());
    }
}
