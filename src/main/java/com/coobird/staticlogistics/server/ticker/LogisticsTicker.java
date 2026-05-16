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

    private static final int CLEAN_INTERVAL = 200;
    private static final Map<ResourceKey<Level>, Integer> dimensionCleanCounters = new ConcurrentHashMap<>();

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
        if (counter >= CLEAN_INTERVAL) {
            cooldownManager.tick(dim, currentTick);
            dimensionCleanCounters.put(dim, 0);
        }

        LinkManager manager = LinkManager.get(level);
        LongSet activeKeys = manager.getActiveProviderKeys();
        if (activeKeys.isEmpty()) return;

        for (long sourceKey : activeKeys.toLongArray()) {
            if (cooldownManager.hasCooldown(dim, sourceKey, currentTick)) continue;

            LogisticsNode sourceNode = LogisticsNode.fromKey(sourceKey, level.dimension());
            FaceConfigComposite config = manager.getFaceConfig(sourceKey);
            if (config == null || config.isDefault()) continue;

            boolean movedSomething = false;

            for (var type : TransferRegistries.getAllActive()) {
                if (!config.isTypeSelected(type)) continue;

                int limit = config.getTransferLimit(type);
                TransferContext context = TransferContext.obtain(
                    level, sourceNode, config, type, limit, false, currentTick, manager
                );

                try {
                    if (transferExecutor.executeTransfer(context)) {
                        movedSomething = true;
                    }
                } finally {
                    context.recycle();
                }
            }

            int baseInterval = SLConfig.getDefaultTickInterval();
            int speedMult = config.sharedContainerConfig.getSpeedMultiplier();
            int actualInterval = (int) Math.max(1, baseInterval / Math.sqrt(speedMult));
            if (movedSomething) {
                cooldownManager.setCooldown(dim, sourceKey, actualInterval, currentTick);
            } else {
                cooldownManager.setCooldown(dim, sourceKey, 10, currentTick);
            }
        }
    }

    public static void wakeup(ServerLevel level, long sourceKey) {
        cooldownManager.removeCooldown(level.dimension(), sourceKey);
    }

    public static void wakeupGroup(MinecraftServer server, String groupId) {
        GlobalLogisticsManager manager = GlobalLogisticsManager.get(server);
        List<LogisticsNode> senders = manager.getSenders(groupId);
        for (LogisticsNode sender : senders) {
            ServerLevel level = server.getLevel(sender.gPos().dimension());
            if (level != null) {
                cooldownManager.removeCooldown(level.dimension(), sender.toKey());
            }
        }
    }
}