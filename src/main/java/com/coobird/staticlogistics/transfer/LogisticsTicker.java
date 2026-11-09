package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.logistics.group.GlobalLogisticsManager;
import com.coobird.staticlogistics.logistics.node.EndpointAddress;
import com.coobird.staticlogistics.logistics.node.EndpointConfig;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import com.coobird.staticlogistics.logistics.util.LogisticsConstants;
import com.coobird.staticlogistics.transfer.strategy.StrategyBasedTargetSelector;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.mesdag.portlib.event.tick.PortServerTickEvent;

import java.util.*;

/// 在共享时间预算内轮转执行各维度到期的资源传输任务。
@EventBusSubscriber(modid = StaticLogistics.MODID)
public final class LogisticsTicker {
    private static final long SYNC_BUDGET_NANOS = 2_000_000L;
    private static final Map<MinecraftServer, RuntimeState> RUNTIMES = new HashMap<>();

    private record Job(EndpointAddress source, ResourceLocation type) {
    }

    private static final class DimensionState {
        final TransferWorkQueue<Job> queue = new TransferWorkQueue<>();
        final Map<EndpointAddress, Set<Job>> jobs = new HashMap<>();
        final Set<EndpointAddress> dirty = new LinkedHashSet<>();
        List<EndpointAddress> providerSnapshot = List.of();
        List<EndpointAddress> scanningProviders;
        int previousIndex, providerIndex;
        boolean forceRebuild;
        boolean rebuild = true;
        boolean preferDirty = true;
    }

    private static final class RuntimeState {
        final Map<ResourceKey<Level>, DimensionState> dimensions = new HashMap<>();
        final StrategyBasedTargetSelector targetSelector = new StrategyBasedTargetSelector();
        final Map<ResourceLocation, LogisticsResource<?>> types = new LinkedHashMap<>();
        int generation = -1;
        ResourceKey<Level> nextDimension;
        int preparationIndex;
        int syncIndex;
    }

    private record DimensionWork(ServerLevel level, LinkManager manager, DimensionState state) {
    }

    private LogisticsTicker() {
    }
    private static RuntimeState state(MinecraftServer server) {
        return RUNTIMES.computeIfAbsent(server, ignored -> new RuntimeState());
    }

    public static void onServerTick(PortServerTickEvent.Post event) {
        tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            RuntimeState runtime = RUNTIMES.get(level.getServer());
            if (runtime != null) {
                runtime.dimensions.remove(level.dimension());
                runtime.targetSelector.clearCache();
            }
        }
    }

    private static void tick(MinecraftServer server) {
        RuntimeState runtime = state(server);
        if (runtime.generation != TransferRegistries.generation()) {
            runtime.types.clear();
            for (LogisticsResource<?> type : TransferRegistries.getAllActive()) runtime.types.put(type.typeId(), type);
            runtime.generation = TransferRegistries.generation();
            runtime.dimensions.values().forEach(d -> d.rebuild = true);
        }
        List<DimensionWork> work = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            LinkManager manager = LinkManager.get(level);
            DimensionState dimension = runtime.dimensions.computeIfAbsent(level.dimension(), ignored -> new DimensionState());
            work.add(new DimensionWork(level, manager, dimension));
        }
        if (work.isEmpty()) return;
        long syncDeadline = System.nanoTime() + SYNC_BUDGET_NANOS;
        int syncStart = Math.floorMod(runtime.syncIndex, work.size());
        runtime.syncIndex = (syncStart + 1) % work.size();
        for (int offset = 0; offset < work.size() && System.nanoTime() - syncDeadline < 0L; offset++) {
            try {
                work.get((syncStart + offset) % work.size()).manager().flushPendingNetworkSync(syncDeadline);
            } catch (RuntimeException exception) {
                LogUtils.getLogger().error("Incremental topology broadcast failed; retained for retry", exception);
            }
        }
        /// 网络发送使用独立预算，不消耗传输阶段的执行时间。
        long deadline = System.nanoTime() + Math.max(1, LogisticsConstants.Performance.getTickerTimeBudgetNanos());
        int index = 0;
        for (int i = 0; i < work.size(); i++) {
            if (work.get(i).level().dimension().equals(runtime.nextDimension)) {
                index = i;
                break;
            }
        }
        /// 准备阶段最多使用剩余时间的三分之一，给已就绪的传输留出时间。
        long preparationDeadline = System.nanoTime() + Math.max(0, (deadline - System.nanoTime()) / 3);
        int prepareStart = Math.floorMod(runtime.preparationIndex, work.size());
        runtime.preparationIndex = (prepareStart + 1) % work.size();
        for (int offset = 0; offset < work.size(); offset++) {
            DimensionWork current = work.get((prepareStart + offset) % work.size());
            long now = System.nanoTime();
            long slice = Math.max(0, preparationDeadline - now) / (work.size() - offset);
            reconcile(current.level(), current.manager(), current.state(), runtime.types, now + slice / 2);
            current.state().queue.prepare(current.level().getGameTime(), now + slice);
        }
        long maximum = Math.max(1L, (long) LogisticsConstants.Performance.getTickerBatchSize() * runtime.types.size());
        for (long processed = 0; processed < maximum && System.nanoTime() - deadline < 0L; ) {
            int selected = -1;
            for (int offset = 0; offset < work.size(); offset++) {
                int candidateIndex = (index + offset) % work.size();
                DimensionWork candidate = work.get(candidateIndex);
                if (candidate.state().queue.hasReady()) {
                    selected = candidateIndex;
                    break;
                }
            }
            if (selected < 0) break;
            DimensionWork current = work.get(selected);
            index = (selected + 1) % work.size();
            runtime.nextDimension = work.get(index).level().dimension();
            long tick = current.level().getGameTime();
            Job job = current.state().queue.poll();
            if (job == null) continue;
            processed++;
            int delay = 1;
            boolean reschedule = true;
            try {
                EndpointConfig config = current.manager().getEndpointConfig(job.source());
                LogisticsResource<?> type = runtime.types.get(job.type());
                if (config == null || type == null || !config.isGlobalOutputEnabled() || !config.linkConfig.isEnabled() || !config.isTypeSelected(type)) {
                    current.state().dirty.add(job.source());
                    reschedule = false;
                    continue;
                }
                delay = processCandidate(current, runtime, job, config, type, tick, deadline);
            } catch (RuntimeException exception) {
                /// 单个第三方适配器异常不能中断其他来源，也不能丢失当前任务。
                delay = Math.max(1, LogisticsConstants.Performance.getDefaultCooldownTicks());
                LogUtils.getLogger().error("Logistics activation failed at {} type {}", job.source(), job.type(), exception);
            } finally {
                if (reschedule) current.state().queue.schedule(job, tick + Math.max(1, delay));
            }
        }
    }

    private static void reconcile(ServerLevel level, LinkManager manager, DimensionState state,
                                  Map<ResourceLocation, LogisticsResource<?>> types, long deadline) {
        List<EndpointAddress> providers = manager.getActiveProviderKeys();
        if (state.scanningProviders == null && (state.rebuild || state.providerSnapshot != providers)) {
            state.scanningProviders = providers;
            state.previousIndex = 0;
            state.providerIndex = 0;
            state.forceRebuild = state.rebuild;
            state.rebuild = false;
        }
        for (int prepared = 0; prepared < 256 && System.nanoTime() - deadline < 0L; prepared++) {
            EndpointAddress source;
            boolean hasPreviousScan = state.scanningProviders != null
                && state.providerSnapshot != null
                && state.previousIndex < state.providerSnapshot.size();
            boolean hasCurrentScan = state.scanningProviders != null
                && state.providerIndex < state.scanningProviders.size();
            boolean hasScanWork = hasPreviousScan || hasCurrentScan;
            if (!state.dirty.isEmpty() && (state.preferDirty || !hasScanWork)) {
                var iterator = state.dirty.iterator();
                source = iterator.next();
                iterator.remove();
                state.preferDirty = false;
            } else if (hasPreviousScan) {
                source = state.providerSnapshot.get(state.previousIndex++);
                state.preferDirty = true;
                EndpointConfig config = manager.getEndpointConfig(source);
                if (isProvider(config)) continue;
            } else if (hasCurrentScan) {
                source = state.scanningProviders.get(state.providerIndex++);
                state.preferDirty = true;
                if (!state.forceRebuild && state.jobs.containsKey(source)) continue;
            } else {
                if (state.scanningProviders != null) {
                    state.providerSnapshot = state.scanningProviders;
                    state.scanningProviders = null;
                }
                break;
            }
            try {
                reconcileSource(level, manager, state, types, source);
            } catch (RuntimeException failure) {
                /// 准备失败不丢掉脏来源，也不在同一 tick 内反复重试。
                state.dirty.add(source);
                LogUtils.getLogger().error("Logistics preparation failed at {}", source, failure);
                break;
            }
        }
    }

    private static boolean isProvider(EndpointConfig config) {
        return config != null && config.isGlobalOutputEnabled()
            && config.linkConfig.isEnabled() && config.faceConfig.hasGroup();
    }

    private static void reconcileSource(ServerLevel level, LinkManager manager, DimensionState state,
                                        Map<ResourceLocation, LogisticsResource<?>> types, EndpointAddress source) {
        Set<Job> previous = state.jobs.remove(source);
        if (previous != null) previous.forEach(state.queue::remove);
        EndpointConfig config = manager.getEndpointConfig(source);
        if (!isProvider(config)) return;
        Set<Job> jobs = new LinkedHashSet<>();
        for (LogisticsResource<?> type : types.values()) {
            if (config.isTypeSelected(type)) {
                Job job = new Job(source, type.typeId());
                jobs.add(job);
                state.queue.schedule(job, level.getGameTime());
            }
        }
        state.jobs.put(source, jobs);
    }

    private static int processCandidate(DimensionWork work, RuntimeState runtime, Job job,
                                        EndpointConfig config, LogisticsResource<?> type,
                                        long tick, long deadline) {
        if (type.requiresValidLinks() && !config.hasLinkedNodes()) {
            return type.requiresCooldown() ? LogisticsConstants.Performance.getDefaultCooldownTicks() : 1;
        }
        /// 来源被红石暂停或没有额度时，不再扫描其全部目标。
        var container = config.getContainerConfig();
        if (container == null || !container.allowsRedstone(work.level(), config.linkConfig.getRedstoneMode())) {
            return type.requiresCooldown() ? LogisticsConstants.Performance.getDefaultCooldownTicks() : 1;
        }
        long limit = config.getTransferLimit(type);
        if (limit <= 0) return type.requiresCooldown() ? LogisticsConstants.Performance.getDefaultCooldownTicks() : 1;
        TransferContext context = TransferContext.obtain(work.level(), work.manager().createNodeFromKey(job.source()),
            config, type, limit, false, tick, deadline, work.manager());
        var handler = TransferRegistries.getHandler(type);
        if (handler == null) return 1;
        List<LogisticsNode> targets = runtime.targetSelector.selectTargets(context);
        if (targets == null) return 1;
        boolean moved = !targets.isEmpty() && handler.performTransfer(context, targets);
        boolean completed = !context.isYielded() && context.hasTimeRemaining();
        if (completed) runtime.targetSelector.completeSelection(context);
        if (!type.requiresCooldown() || !moved && !completed) return 1;
        return moved ? getActualInterval(config) : LogisticsConstants.Performance.getDefaultCooldownTicks();
    }

    public static void targetChanged(ServerLevel level, EndpointConfig target) {
        RuntimeState runtime = RUNTIMES.get(level.getServer());
        if (runtime != null) runtime.targetSelector.invalidateTarget(level.getServer(), target);
    }

    /// 配置变更重新登记启用类型并清除旧冷却；不缓存库存内容。
    public static void wakeup(ServerLevel level, EndpointAddress source) {
        state(level.getServer()).dimensions.computeIfAbsent(level.dimension(), ignored -> new DimensionState()).dirty.add(source);
    }
    public static void wakeupGroup(MinecraftServer server, GroupKey groupKey) {
        wakeupSenders(server, GlobalLogisticsManager.get(server).getSenders(groupKey));
    }
    private static void wakeupSenders(MinecraftServer server, List<LogisticsNode> senders) {
        for (LogisticsNode sender : senders) {
            ServerLevel level = server.getLevel(sender.gPos().dimension());
            if (level != null) wakeup(level, EndpointAddress.of(sender));
        }
    }
    public static void release(MinecraftServer server) {
        RuntimeState runtime = RUNTIMES.remove(server);
        if (runtime != null) runtime.targetSelector.clearCache();
    }
    public static void onConfigReload(MinecraftServer server) {
        RuntimeState runtime = RUNTIMES.get(server);
        if (runtime != null) {
            runtime.targetSelector.clearCache();
            runtime.dimensions.values().forEach(d -> d.rebuild = true);
        }
    }

    private static int getActualInterval(EndpointConfig config) {
        return config.getContainerConfig() == null ? SLConfig.getDefaultTickInterval()
            : config.getContainerConfig().getCachedActualInterval();
    }
}
