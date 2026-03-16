package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.SLConfig;
import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.core.StaticLink;
import com.coobird.staticlogistics.storage.LinkManager;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = Staticlogistics.MODID)
public class LogisticsTicker {
    private static final TransferType[] TYPES = TransferType.values();
    private static final Map<ResourceKey<Level>, Long2ByteMap> DIMENSION_COOLDOWNS = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            tick(serverLevel);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            DIMENSION_COOLDOWNS.remove(serverLevel.dimension());
        }
    }

    private static void tick(ServerLevel level) {
        LinkManager manager = LinkManager.get(level);
        if (manager == null) return;

        LongSet activeKeys = manager.getActiveSourceKeys();
        if (activeKeys.isEmpty()) return;

        long gameTime = level.getGameTime();
        ResourceKey<Level> dimKey = level.dimension();

        Long2ByteMap cooldownMap = DIMENSION_COOLDOWNS.computeIfAbsent(dimKey, k -> {
            Long2ByteMap map = new Long2ByteOpenHashMap();
            map.defaultReturnValue((byte) 0);
            return map;
        });

        LongIterator iterator = activeKeys.iterator();
        while (iterator.hasNext()) {
            long sourceKey = iterator.nextLong();

            byte cooldown = cooldownMap.get(sourceKey);
            if (cooldown > 0) {
                cooldownMap.put(sourceKey, (byte) (cooldown - 1));
                continue;
            }

            LinkManager.CachedSourceData cached = manager.getCachedSource(sourceKey);
            if (cached == null || cached.config() == null) continue;

            int speedMult = Math.max(1, cached.config().getSpeedMultiplier());
            int interval = Math.max(1, SLConfig.getDefaultTickInterval() / speedMult);
            boolean isIntervalTick = (gameTime % interval == 0);

            boolean movedSomething = false;
            boolean hadWorkToDo = false;

            for (TransferType type : TYPES) {
                Map<UUID, List<StaticLink>> ownerMap = cached.groupedLinks().get(type);
                if (ownerMap == null || ownerMap.isEmpty()) continue;

                var settings = cached.config().getSettings(type);
                if (settings == null || !settings.mode.allowsOutput()) continue;

                hadWorkToDo = true;

                if (type == TransferType.ENERGY || isIntervalTick) {
                    for (Map.Entry<UUID, List<StaticLink>> entry : ownerMap.entrySet()) {
                        UUID owner = entry.getKey();
                        List<StaticLink> linksOfOwner = entry.getValue();

                        if (TransferEngine.execute(level, linksOfOwner, type, cached.config(), settings.rrCursor, owner)) {
                            movedSomething = true;
                        }
                    }
                } else {
                    movedSomething = true;
                }
            }
            if (hadWorkToDo && !movedSomething) {
                cooldownMap.put(sourceKey, (byte) 10);
            }
        }
    }

    public static void wakeup(long sourceKey) {
        for (Long2ByteMap map : DIMENSION_COOLDOWNS.values()) {
            map.remove(sourceKey);
        }
    }
}