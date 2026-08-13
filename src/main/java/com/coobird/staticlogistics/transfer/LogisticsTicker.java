package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.logistics.group.GlobalLogisticsManager;
import com.coobird.staticlogistics.logistics.node.FaceAddress;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import com.coobird.staticlogistics.logistics.util.LogisticsConstants;
import com.coobird.staticlogistics.transfer.strategy.StrategyBasedTargetSelector;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 在服务器主线程按候选操作数和软时间预算公平驱动物流网络。
 *
 * <p>游标精确到“节点 × 资源类型”，在每次执行前先前移，因此即使单次第三方能力调用
 * 超过预算，下一刻也会从下一个候选继续，不会让后续节点长期饥饿。
 */
@EventBusSubscriber(modid = StaticLogistics.MODID)
public final class LogisticsTicker {
    private static final TransferExecutor TRANSFER_EXECUTOR =
        new TransferExecutor(new StrategyBasedTargetSelector());
    private static final Map<MinecraftServer, RuntimeState> RUNTIMES = new HashMap<>();

    private static LogisticsResource<?>[] cachedTypes = new LogisticsResource<?>[0];
    private static int cachedTypesGeneration = -1;

    private static final class SchedulerCursor {
        int nodeIndex;
        int typeIndex;
        long lastElapsedNanos;
        long peakElapsedNanos;
        double averageElapsedNanos;
        long budgetStops;
        int lastCandidates;
        int lastAttempts;

        void record(long elapsed, int candidates, int attempts, boolean budgetStopped) {
            lastElapsedNanos = elapsed;
            peakElapsedNanos = Math.max(peakElapsedNanos, elapsed);
            averageElapsedNanos = averageElapsedNanos == 0
                ? elapsed : averageElapsedNanos * 0.9 + elapsed * 0.1;
            lastCandidates = candidates;
            lastAttempts = attempts;
            if (budgetStopped) budgetStops++;
        }
    }

    private static final class RuntimeState {
        final CooldownManager cooldowns = new CooldownManager();
        final Map<ResourceKey<Level>, SchedulerCursor> schedulers = new HashMap<>();
    }

    public record SchedulerStats(long lastNanos, long peakNanos, long averageNanos,
                                 long budgetStops, int candidates, int attempts) {
    }

    private LogisticsTicker() {
    }

    private static RuntimeState state(MinecraftServer server) {
        return RUNTIMES.computeIfAbsent(server, ignored -> new RuntimeState());
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) tick(level);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            RuntimeState state = RUNTIMES.get(level.getServer());
            if (state == null) return;
            state.cooldowns.clearForDimension(level.dimension());
            state.schedulers.remove(level.dimension());
        }
    }

    private static void tick(ServerLevel level) {
        ResourceKey<Level> dimension = level.dimension();
        long currentTick = level.getGameTime();
        RuntimeState state = state(level.getServer());

        state.cooldowns.tick(dimension, currentTick);
        LinkManager manager = LinkManager.get(level);
        manager.flushPendingNetworkSync();

        FaceAddress[] sourceKeys = manager.getActiveProviderKeysArray();
        LogisticsResource<?>[] types = getCachedTypes();
        SchedulerCursor cursor = state.schedulers.computeIfAbsent(dimension, ignored -> new SchedulerCursor());
        if (sourceKeys.length == 0 || types.length == 0) {
            cursor.record(0, 0, 0, false);
            return;
        }

        cursor.nodeIndex = Math.floorMod(cursor.nodeIndex, sourceKeys.length);
        cursor.typeIndex = Math.floorMod(cursor.typeIndex, types.length);
        long requestedCandidates = (long) LogisticsConstants.Performance.getTickerBatchSize()
            * types.length;
        int candidateBudget = (int) Math.clamp(requestedCandidates, 1L, Integer.MAX_VALUE);
        long timeBudget = Math.max(1, LogisticsConstants.Performance.getTickerTimeBudgetNanos());
        long started = System.nanoTime();
        int candidates = 0;
        int attempts = 0;
        boolean budgetStopped = false;

        while (candidates < candidateBudget) {
            int nodeIndex = cursor.nodeIndex;
            int typeIndex = cursor.typeIndex;
            advanceCursor(cursor, sourceKeys.length, types.length);
            candidates++;

            FaceAddress sourceKey = sourceKeys[nodeIndex];
            FaceConfigComposite config = manager.getFaceConfig(sourceKey);
            if (config != null && !config.isDefault()) {
                LogisticsResource<?> type = types[typeIndex];
                if (config.isTypeSelected(type)) {
                    attempts++;
                    processCandidate(level, dimension, currentTick, state, manager,
                        sourceKey, config, type);
                }
            }

            if (System.nanoTime() - started >= timeBudget) {
                budgetStopped = true;
                break;
            }
        }

        cursor.record(System.nanoTime() - started, candidates, attempts, budgetStopped);
    }

    private static void advanceCursor(SchedulerCursor cursor, int nodeCount, int typeCount) {
        cursor.typeIndex++;
        if (cursor.typeIndex < typeCount) return;
        cursor.typeIndex = 0;
        cursor.nodeIndex = (cursor.nodeIndex + 1) % nodeCount;
    }

    private static void processCandidate(
        ServerLevel level, ResourceKey<Level> dimension, long currentTick,
        RuntimeState state, LinkManager manager, FaceAddress sourceKey,
        FaceConfigComposite config, LogisticsResource<?> type
    ) {
        boolean needsCooldown = type.requiresCooldown();
        if (needsCooldown && state.cooldowns.hasCooldown(
            dimension, sourceKey, type.typeId(), currentTick)) return;
        if (type.requiresValidLinks() && !config.hasLinkedNodes()) return;

        LogisticsNode sourceNode = manager.createNodeFromKey(sourceKey);
        long limit = config.getTransferLimit(type);
        TransferContext context = TransferContext.obtain(
            level, sourceNode, config, type, limit, false, currentTick, manager);
        boolean moved;
        try {
            moved = TRANSFER_EXECUTOR.executeTransfer(context);
        } finally {
            context.recycle();
        }

        if (!needsCooldown) return;
        int cooldown = moved ? getActualInterval(config)
            : LogisticsConstants.Performance.getDefaultCooldownTicks();
        state.cooldowns.setCooldown(
            dimension, sourceKey, type.typeId(), cooldown, currentTick);
    }

    public static SchedulerStats getSchedulerStats(MinecraftServer server, ResourceKey<Level> dimension) {
        RuntimeState state = RUNTIMES.get(server);
        SchedulerCursor cursor = state == null ? null : state.schedulers.get(dimension);
        if (cursor == null) return new SchedulerStats(0, 0, 0, 0, 0, 0);
        return new SchedulerStats(cursor.lastElapsedNanos, cursor.peakElapsedNanos,
            Math.round(cursor.averageElapsedNanos), cursor.budgetStops,
            cursor.lastCandidates, cursor.lastAttempts);
    }

    public static void wakeup(ServerLevel level, FaceAddress source) {
        state(level.getServer()).cooldowns.removeAllForSource(level.dimension(), source);
    }

    public static void wakeupGroup(MinecraftServer server, String groupId) {
        GlobalLogisticsManager manager = GlobalLogisticsManager.get(server);
        wakeupSenders(server, manager.getSenders(groupId));
    }

    public static void wakeupGroup(MinecraftServer server, GroupKey groupKey) {
        GlobalLogisticsManager manager = GlobalLogisticsManager.get(server);
        wakeupSenders(server, manager.getSenders(groupKey));
    }

    private static void wakeupSenders(MinecraftServer server, List<LogisticsNode> senders) {
        for (LogisticsNode sender : senders) {
            ServerLevel level = server.getLevel(sender.gPos().dimension());
            if (level != null) {
                state(server).cooldowns.removeAllForSource(
                    level.dimension(), FaceAddress.of(sender));
            }
        }
    }

    public static void release(MinecraftServer server) {
        RuntimeState removed = RUNTIMES.remove(server);
        if (removed != null) removed.cooldowns.clearAll();
    }

    private static LogisticsResource<?>[] getCachedTypes() {
        int generation = TransferRegistries.generation();
        if (generation != cachedTypesGeneration) {
            cachedTypes = TransferRegistries.getAllActive().toArray(new LogisticsResource<?>[0]);
            cachedTypesGeneration = generation;
        }
        return cachedTypes;
    }

    private static int getActualInterval(FaceConfigComposite config) {
        return config.getContainerConfig() != null
            ? config.getContainerConfig().getCachedActualInterval()
            : SLConfig.getDefaultTickInterval();
    }
}
