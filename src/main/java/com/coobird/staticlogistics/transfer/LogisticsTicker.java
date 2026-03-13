package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.SLConfig;
import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.core.FaceConfig;
import com.coobird.staticlogistics.core.StaticLink;
import com.coobird.staticlogistics.storage.LinkManager;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = Staticlogistics.MODID)
public class LogisticsTicker {

    private static final TransferType[] TYPES = TransferType.values();

    private static final Map<ResourceKey<Level>, Long2ByteMap> DIMENSION_COOLDOWNS = new HashMap<>();

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            tick(serverLevel);
        }
    }

    private static void tick(ServerLevel level) {
        LinkManager manager = LinkManager.get(level);
        if (manager == null) return;

        long gameTime = level.getGameTime();
        var activeKeys = manager.getActiveSourceKeys();
        if (activeKeys.isEmpty()) return;

        int defaultInterval = SLConfig.getDefaultTickInterval();
        Long2ByteMap cooldownMap = DIMENSION_COOLDOWNS.computeIfAbsent(level.dimension(), k -> new Long2ByteOpenHashMap());

        LongIterator iterator = activeKeys.iterator();
        while (iterator.hasNext()) {
            long sourceKey = iterator.nextLong();

            byte cooldown = cooldownMap.get(sourceKey);
            if (cooldown > 0) {
                cooldownMap.put(sourceKey, (byte) (cooldown - 1));
                continue;
            }

            LinkManager.CachedSourceData cached = manager.getCachedSource(sourceKey);
            if (cached == null || cached.sortedLinks() == null) continue;

            int speedMult = cached.config().getSpeedMultiplier();
            int currentInterval = (speedMult >= defaultInterval) ? 1 : Math.max(1, defaultInterval / speedMult);

            boolean anyMoved = false;
            boolean anyTypeProcessed = false;

            for (TransferType type : TYPES) {
                List<StaticLink> linksForType = cached.sortedLinks().get(type);
                if (linksForType == null || linksForType.isEmpty()) continue;

                FaceConfig.SideData sideData = cached.config().getSettings(type);
                if (!sideData.mode.allowsOutput()) continue;

                anyTypeProcessed = true;
                UUID sourceOwner = linksForType.getFirst().owner();

                if (type == TransferType.ENERGY) {
                    boolean moved = TransferEngine.execute(level, linksForType, type, cached.config(), sideData.rrCursor, sourceOwner);
                    if (moved) anyMoved = true;
                } else if (gameTime % currentInterval == 0) {
                    boolean moved = TransferEngine.execute(level, linksForType, type, cached.config(), sideData.rrCursor, sourceOwner);
                    if (moved) anyMoved = true;
                } else {
                    anyMoved = true;
                }
            }

            if (anyTypeProcessed && !anyMoved) {
                cooldownMap.put(sourceKey, (byte) 10);
            }
        }
    }

    public static void wakeup(ServerLevel level, long sourceKey) {
        Long2ByteMap cooldownMap = DIMENSION_COOLDOWNS.get(level.dimension());
        if (cooldownMap != null) {
            cooldownMap.remove(sourceKey);
        }
    }

    public static void wakeup(long sourceKey) {
        DIMENSION_COOLDOWNS.values().forEach(map -> map.remove(sourceKey));
    }
}