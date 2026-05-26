package com.coobird.staticlogistics.server.ticker;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.context.TransferContext;
import com.coobird.staticlogistics.transfer.cooldown.CooldownManager;
import com.coobird.staticlogistics.transfer.handler.TransferExecutor;
import com.coobird.staticlogistics.transfer.strategy.StrategyBasedTargetSelector;
import com.coobird.staticlogistics.util.LogisticsConstants;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = Staticlogistics.MODID)
public class LogisticsTicker {
    private static final CooldownManager cooldownManager = new CooldownManager();
    private static final TransferExecutor transferExecutor = new TransferExecutor(new StrategyBasedTargetSelector());

    private static final Map<ResourceKey<Level>, Integer> dimensionCleanCounters = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Integer> dimensionBatchOffsets = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) tick(level);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            cooldownManager.clearForDimension(level.dimension());
            dimensionCleanCounters.remove(level.dimension());
        }
    }

    private static void tick(ServerLevel level) {
        ResourceKey<Level> dim = level.dimension();
        long currentTick = level.getGameTime();

        int counter = dimensionCleanCounters.compute(dim, (k, v) -> (v == null) ? 1 : v + 1);
        if (counter >= LogisticsConstants.Performance.getCleanIntervalTicks()) {
            cooldownManager.tick(dim, currentTick);
            dimensionCleanCounters.put(dim, 0);
        }

        LinkManager manager = LinkManager.get(level);
        if (!manager.hasActiveProviders()) return;
        LongSet activeKeys = manager.getActiveProviderKeys();

        long[] keys = activeKeys.toLongArray();
        int totalBatches = (keys.length + LogisticsConstants.Performance.getTickerBatchSize() - 1) / LogisticsConstants.Performance.getTickerBatchSize();
        int batchOffset = dimensionBatchOffsets.compute(dim, (k, v) -> (v == null) ? 0 : v);

        int startIdx = (batchOffset % totalBatches) * LogisticsConstants.Performance.getTickerBatchSize();
        int endIdx = Math.min(startIdx + LogisticsConstants.Performance.getTickerBatchSize(), keys.length);

        for (int i = startIdx; i < endIdx; i++) {
            long sourceKey = keys[i];
            LogisticsNode sourceNode = manager.createNodeFromKey(sourceKey);
            FaceConfigComposite config = manager.getFaceConfig(sourceKey);
            if (config == null || config.isDefault()) continue;

            for (var type : TransferRegistries.getAllActive()) {
                if (!config.isTypeSelected(type)) continue;

                boolean isEnergy = type.capability() != null && type.capability() == net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK;
                long typeCooldownKey = (sourceKey << 8) | type.bitOffset();
                if (!isEnergy && cooldownManager.hasCooldown(dim, typeCooldownKey, currentTick)) continue;

                int limit = config.getTransferLimit(type);
                TransferContext context = TransferContext.obtain(
                    level, sourceNode, config, type, limit, false, currentTick, manager
                );

                boolean typeMoved;
                try {
                    typeMoved = transferExecutor.executeTransfer(context);
                } finally {
                    context.recycle();
                }

                if (!isEnergy) {
                    int baseInterval = SLConfig.getDefaultTickInterval();
                    int speedMult = config.sharedContainerConfig != null
                        ? config.sharedContainerConfig.getSpeedMultiplier() : 1;
                    int actualInterval = (int) Math.max(1, baseInterval / Math.sqrt(Math.max(1, speedMult)));
                    if (typeMoved) {
                        cooldownManager.setCooldown(dim, typeCooldownKey, actualInterval, currentTick);
                    } else {
                        cooldownManager.setCooldown(dim, typeCooldownKey, LogisticsConstants.Performance.getDefaultCooldownTicks(), currentTick);
                    }
                }
            }
        }

        dimensionBatchOffsets.put(dim, (batchOffset + 1) % totalBatches);
    }

    public static void wakeup(ServerLevel level, long sourceKey) {
        cooldownManager.removeAllForSourceKey(level.dimension(), sourceKey);
    }

    /**
     * 方块/节点移除时批量清理冷却，防止已拆除节点的冷却记录残留
     */
    public static void cleanupCooldowns(ResourceKey<Level> dimension, long[] keys) {
        for (long k : keys) cooldownManager.removeAllForSourceKey(dimension, k);
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
}