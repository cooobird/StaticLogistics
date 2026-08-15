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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 在服务器主线程中按全局候选数量和共享软时间预算公平驱动物流网络。
 *
 * <p>服务器中的所有维度共享同一份 Tick 预算。调度器在维度之间轮转，并为每个维度分别保存
 * “节点 × 资源类型”游标，因此预算耗尽后仍能从公平的断点继续，不会让靠后的维度或节点长期饥饿。
 */
@EventBusSubscriber(modid = StaticLogistics.MODID)
public final class LogisticsTicker {
    private static final TransferExecutor TRANSFER_EXECUTOR =
        new TransferExecutor(new StrategyBasedTargetSelector());
    private static final Map<MinecraftServer, RuntimeState> RUNTIMES = new HashMap<>();

    private static LogisticsResource<?>[] cachedTypes = new LogisticsResource<?>[0];
    private static int cachedTypesGeneration = -1;

    private static final class SchedulerCursor {
        FaceAddress nextNode;
        ResourceLocation nextType;
    }

    private static final class RuntimeState {
        final CooldownManager cooldowns = new CooldownManager();
        final Map<ResourceKey<Level>, SchedulerCursor> schedulers = new HashMap<>();
        ResourceKey<Level> nextDimension;
    }

    private static final class DimensionWork {
        final ServerLevel level;
        final ResourceKey<Level> dimension;
        final long currentTick;
        final LinkManager manager;
        final FaceAddress[] sourceKeys;
        final SchedulerCursor cursor;
        int nodeIndex;
        int typeIndex;

        DimensionWork(ServerLevel level, LinkManager manager, FaceAddress[] sourceKeys,
                      SchedulerCursor cursor, int nodeIndex, int typeIndex) {
            this.level = level;
            this.dimension = level.dimension();
            this.currentTick = level.getGameTime();
            this.manager = manager;
            this.sourceKeys = sourceKeys;
            this.cursor = cursor;
            this.nodeIndex = nodeIndex;
            this.typeIndex = typeIndex;
        }
    }

    private LogisticsTicker() {
    }

    private static RuntimeState state(MinecraftServer server) {
        return RUNTIMES.computeIfAbsent(server, ignored -> new RuntimeState());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onServerTick(ServerTickEvent.Post event) {
        tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            RuntimeState state = RUNTIMES.get(level.getServer());
            if (state == null) return;
            state.cooldowns.clearForDimension(level.dimension());
            state.schedulers.remove(level.dimension());
            if (level.dimension().equals(state.nextDimension)) state.nextDimension = null;
        }
    }

    private static void tick(MinecraftServer server) {
        long started = System.nanoTime();
        long timeBudget = Math.max(1, LogisticsConstants.Performance.getTickerTimeBudgetNanos());
        long deadline = started + timeBudget;
        RuntimeState state = state(server);
        LogisticsResource<?>[] types = getCachedTypes();
        List<DimensionWork> workByDimension = collectDimensionWork(server, state, types, deadline);
        if (workByDimension.isEmpty()) return;

        int dimensionIndex = findDimensionIndex(workByDimension, state.nextDimension);
        long requestedCandidates = (long) LogisticsConstants.Performance.getTickerBatchSize() * types.length;
        int candidateBudget = (int) Math.clamp(requestedCandidates, 1L, Integer.MAX_VALUE);
        int candidates = 0;

        while (candidates < candidateBudget && System.nanoTime() - deadline < 0L) {
            DimensionWork work = workByDimension.get(dimensionIndex);
            dimensionIndex = (dimensionIndex + 1) % workByDimension.size();
            state.nextDimension = workByDimension.get(dimensionIndex).dimension;
            int nodeIndex = work.nodeIndex;
            int typeIndex = work.typeIndex;
            advanceCursor(work, types);
            candidates++;

            FaceAddress sourceKey = work.sourceKeys[nodeIndex];
            FaceConfigComposite config = work.manager.getFaceConfig(sourceKey);
            if (config != null && !config.isDefault()) {
                LogisticsResource<?> type = types[typeIndex];
                if (config.isTypeSelected(type)) {
                    processCandidate(work.level, work.dimension, work.currentTick, state, work.manager,
                        sourceKey, config, type, deadline);
                }
            }

        }
    }

    private static List<DimensionWork> collectDimensionWork(
        MinecraftServer server, RuntimeState state, LogisticsResource<?>[] types, long deadline
    ) {
        List<DimensionWork> workByDimension = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            ResourceKey<Level> dimension = level.dimension();
            state.cooldowns.tick(dimension, level.getGameTime());
            LinkManager manager = LinkManager.get(level);
            manager.flushPendingNetworkSync();
            if (types.length == 0 || System.nanoTime() - deadline >= 0L) continue;

            FaceAddress[] sourceKeys = manager.getActiveProviderKeysArray();
            if (sourceKeys.length == 0) continue;
            SchedulerCursor cursor = state.schedulers.computeIfAbsent(dimension, ignored -> new SchedulerCursor());
            int nodeIndex = findNodeIndex(sourceKeys, cursor.nextNode);
            int typeIndex = findTypeIndex(types, cursor.nextType);
            workByDimension.add(new DimensionWork(
                level, manager, sourceKeys, cursor, nodeIndex, typeIndex));
        }
        return workByDimension;
    }

    private static int findDimensionIndex(List<DimensionWork> workByDimension, ResourceKey<Level> nextDimension) {
        if (nextDimension == null) return 0;
        for (int i = 0; i < workByDimension.size(); i++) {
            if (workByDimension.get(i).dimension.equals(nextDimension)) return i;
        }
        return 0;
    }

    private static int findNodeIndex(FaceAddress[] sourceKeys, FaceAddress nextNode) {
        if (nextNode == null) return 0;
        for (int i = 0; i < sourceKeys.length; i++) {
            if (sourceKeys[i].equals(nextNode)) return i;
        }
        return 0;
    }

    private static int findTypeIndex(LogisticsResource<?>[] types, ResourceLocation nextType) {
        if (nextType == null) return 0;
        for (int i = 0; i < types.length; i++) {
            if (types[i].typeId().equals(nextType)) return i;
        }
        return 0;
    }

    private static void advanceCursor(DimensionWork work, LogisticsResource<?>[] types) {
        work.typeIndex++;
        if (work.typeIndex >= types.length) {
            work.typeIndex = 0;
            work.nodeIndex = (work.nodeIndex + 1) % work.sourceKeys.length;
        }
        work.cursor.nextNode = work.sourceKeys[work.nodeIndex];
        work.cursor.nextType = types[work.typeIndex].typeId();
    }

    private static void processCandidate(
        ServerLevel level, ResourceKey<Level> dimension, long currentTick,
        RuntimeState state, LinkManager manager, FaceAddress sourceKey,
        FaceConfigComposite config, LogisticsResource<?> type, long deadline
    ) {
        boolean needsCooldown = type.requiresCooldown();
        if (needsCooldown && state.cooldowns.hasCooldown(
            dimension, sourceKey, type.typeId(), currentTick)) return;
        if (type.requiresValidLinks() && !config.hasLinkedNodes()) return;

        LogisticsNode sourceNode = manager.createNodeFromKey(sourceKey);
        long limit = config.getTransferLimit(type);
        TransferContext context = TransferContext.obtain(
            level, sourceNode, config, type, limit, false, currentTick, deadline, manager);
        boolean moved;
        boolean completedWithinBudget;
        try {
            moved = TRANSFER_EXECUTOR.executeTransfer(context);
            completedWithinBudget = context.hasTimeRemaining();
        } finally {
            context.recycle();
        }

        if (!needsCooldown) return;
        if (!moved && !completedWithinBudget) return;
        int cooldown = moved ? getActualInterval(config)
            : LogisticsConstants.Performance.getDefaultCooldownTicks();
        state.cooldowns.setCooldown(
            dimension, sourceKey, type.typeId(), cooldown, currentTick);
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

    /**
     * 配置热重载后清除旧冷却，使新间隔立即生效；公平调度断点保持不变。
     */
    public static void onConfigReload(MinecraftServer server) {
        RuntimeState runtime = RUNTIMES.get(server);
        if (runtime == null) return;
        runtime.cooldowns.clearAll();
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
