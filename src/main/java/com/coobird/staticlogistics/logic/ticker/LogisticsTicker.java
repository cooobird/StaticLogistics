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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 物流定时器 —— 每个世界 tick 驱动物流网络运转的核心调度器。
 */
@Mod.EventBusSubscriber(modid = StaticLogistics.MODID)
public class LogisticsTicker {
    private static final CooldownManager cooldownManager = new CooldownManager();
    private static final TransferExecutor transferExecutor = new TransferExecutor(new StrategyBasedTargetSelector());

    private static final Map<ResourceKey<Level>, Integer> dimensionCleanCounters = new HashMap<>();
    private static final Map<ResourceKey<Level>, Integer> dimensionBatchOffsets = new HashMap<>();

    // 缓存类型数组，避免每 tick 每节点创建迭代器
    private static LogisticsResource<?>[] cachedTypes = new LogisticsResource<?>[0];
    private static int cachedTypesGen = -1;

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.level instanceof ServerLevel level) tick(level);
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
