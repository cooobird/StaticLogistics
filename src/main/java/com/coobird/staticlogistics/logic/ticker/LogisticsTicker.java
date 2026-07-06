package com.coobird.staticlogistics.logic.ticker;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.LogisticsResource;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.logic.GlobalLogisticsManager;
import com.coobird.staticlogistics.logic.type.TransferRegistries;
import com.coobird.staticlogistics.storage.link.LinkManager;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.CooldownManager;
import com.coobird.staticlogistics.transfer.TransferContext;
import com.coobird.staticlogistics.transfer.handler.TransferExecutor;
import com.coobird.staticlogistics.transfer.handler.TransferUtils;
import com.coobird.staticlogistics.transfer.strategy.StrategyBasedTargetSelector;
import com.coobird.staticlogistics.util.LogisticsConstants;
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
 * 物流定时器 —— 每个世界 tick 驱动物流网络运转的核心调度器。
 *
 * <p>通过 {@link LevelTickEvent.Post} 监听每个世界的 tick 事件。
 *
 * <p>执行流程：
 * <ol>
 *   <li>冷却清理（按配置间隔）</li>
 *   <li>获取活跃节点数组，按批次分片（避免单 tick 处理过多节点导致卡顿）</li>
 *   <li>遍历当前批次的节点 × 已启用的传输类型</li>
 *   <li>冷却检查 → 创建 {@link TransferContext} → 执行传输</li>
 *   <li>设置成功/失败冷却</li>
 *   <li>tick 结束时批量刷出待同步的网络配置</li>
 * </ol>
 *
 * <p>线程安全：所有操作在服务器主线程上执行。
 * 维度级数据使用普通 HashMap（主线程单线程访问）。
 */
@EventBusSubscriber(modid = StaticLogistics.MODID)
public class LogisticsTicker {
    private static final CooldownManager cooldownManager = new CooldownManager();
    private static final TransferExecutor transferExecutor = new TransferExecutor(new StrategyBasedTargetSelector());

    private static final Map<ResourceKey<Level>, Integer> dimensionCleanCounters = new HashMap<>();
    private static final Map<ResourceKey<Level>, Integer> dimensionBatchOffsets = new HashMap<>();

    // 缓存类型数组，避免每 tick 每节点创建迭代器
    private static LogisticsResource<?>[] cachedTypes = new LogisticsResource<?>[0];
    private static int cachedTypesGen = -1;

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) tick(level);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            cooldownManager.clearForDimension(level.dimension());
            dimensionCleanCounters.remove(level.dimension());
            TransferUtils.clearDimCache(level);
        }
    }

    private static void tick(ServerLevel level) {
        ResourceKey<Level> dim = level.dimension();
        long currentTick = level.getGameTime();

        Integer c = dimensionCleanCounters.get(dim);
        int counter = (c == null) ? 1 : c + 1;
        dimensionCleanCounters.put(dim, counter);
        if (counter >= LogisticsConstants.Performance.getCleanIntervalTicks()) {
            cooldownManager.tick(dim, currentTick);
            dimensionCleanCounters.put(dim, 0);
        }

        LinkManager manager = LinkManager.get(level);
        long[] keys = manager.getActiveProviderKeysArray();
        if (keys.length == 0) return;

        int totalBatches = (keys.length + LogisticsConstants.Performance.getTickerBatchSize() - 1) / LogisticsConstants.Performance.getTickerBatchSize();
        Integer b = dimensionBatchOffsets.get(dim);
        int batchOffset = (b == null) ? 0 : b;

        int startIdx = (batchOffset % totalBatches) * LogisticsConstants.Performance.getTickerBatchSize();
        int endIdx = Math.min(startIdx + LogisticsConstants.Performance.getTickerBatchSize(), keys.length);
        for (int i = startIdx; i < endIdx; i++) {
            long sourceKey = keys[i];
            LogisticsNode sourceNode = manager.createNodeFromKey(sourceKey);
            FaceConfigComposite config = manager.getFaceConfig(sourceKey);
            if (config == null || config.isDefault()) continue;

            // 缓存类型数组
            LogisticsResource<?>[] types = getCachedTypes();

            for (var type : types) {
                if (!config.isTypeSelected(type)) continue;

                boolean needsCooldown = type.requiresCooldown();
                long typeCooldownKey = sourceKey ^ (Long.rotateLeft(type.bitOffset(), 48));
                if (needsCooldown && cooldownManager.hasCooldown(dim, typeCooldownKey, currentTick)) {
                    continue;
                }
                if (type.requiresValidLinks() && config.getLinkedNodes().isEmpty()) {
                    continue;
                }

                long limit = config.getTransferLimit(type);
                TransferContext context = TransferContext.obtain(
                    level, sourceNode, config, type, limit, false, currentTick, manager
                );

                boolean typeMoved;
                try {
                    typeMoved = transferExecutor.executeTransfer(context);
                } finally {
                    context.recycle();
                }

                if (needsCooldown) {
                    int actualInterval = getCachedActualInterval(config);
                    if (typeMoved) {
                        cooldownManager.setCooldown(dim, typeCooldownKey, actualInterval, currentTick);
                    } else {
                        cooldownManager.setCooldown(dim, typeCooldownKey, LogisticsConstants.Performance.getDefaultCooldownTicks(), currentTick);
                    }
                }
            }
        }

        dimensionBatchOffsets.put(dim, (batchOffset + 1) % totalBatches);

        // tick 结束时批量刷出所有待同步的面配置
        manager.flushPendingNetworkSync();
    }

    public static void wakeup(ServerLevel level, long sourceKey) {
        cooldownManager.removeAllForSourceKey(level.dimension(), sourceKey);
    }

    public static void wakeupGroup(MinecraftServer server, String groupId) {
        GlobalLogisticsManager manager = GlobalLogisticsManager.get(server);
        List<LogisticsNode> senders = manager.getSenders(groupId);
        for (LogisticsNode sender : senders) {
            ServerLevel level = server.getLevel(sender.gPos().dimension());
            if (level != null) {
                cooldownManager.removeAllForSourceKey(level.dimension(), sender.toKey());
            }
        }
    }

    private static LogisticsResource<?>[] getCachedTypes() {
        int gen = TransferRegistries.generation();
        if (gen != cachedTypesGen) {
            cachedTypes = TransferRegistries.getAllActive().toArray(new LogisticsResource<?>[0]);
            cachedTypesGen = gen;
        }
        return cachedTypes;
    }

    private static int getCachedActualInterval(FaceConfigComposite config) {
        if (config.sharedContainerConfig != null) {
            return config.sharedContainerConfig.getCachedActualInterval();
        }
        return SLConfig.getDefaultTickInterval();
    }
}