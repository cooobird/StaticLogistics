package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.SLConfig;
import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.storage.LinkManager;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.Map;

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

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) {
            DIMENSION_COOLDOWNS.remove(event.getLevel().getServer().getLevel(Level.OVERWORLD).dimension()); // 简单的维度Key匹配
        }
    }

    private static void tick(ServerLevel level) {
        LinkManager manager = LinkManager.get(level);

        var activeKeys = manager.getActiveSourceKeys();
        if (activeKeys.isEmpty()) return;

        long gameTime = level.getGameTime();
        Long2ByteMap cooldownMap = DIMENSION_COOLDOWNS.computeIfAbsent(level.dimension(), k -> {
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

            int speedMult = cached.config().getSpeedMultiplier();
            int interval = Math.max(1, SLConfig.getDefaultTickInterval() / speedMult);
            boolean isIntervalTick = (gameTime % interval == 0);

            boolean movedSomething = false;
            boolean hadWorkToDo = false;

            for (TransferType type : TYPES) {
                var links = cached.sortedLinks().get(type);
                if (links == null || links.isEmpty()) continue;

                var settings = cached.config().getSettings(type);
                if (!settings.mode.allowsOutput()) continue;

                hadWorkToDo = true;

                boolean shouldTryTransfer = (type == TransferType.ENERGY) || isIntervalTick;

                if (shouldTryTransfer) {
                    if (TransferEngine.execute(level, links, type, cached.config(), settings.rrCursor, links.getFirst().owner())) {
                        movedSomething = true;
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
        DIMENSION_COOLDOWNS.values().forEach(map -> map.remove(sourceKey));
    }
}