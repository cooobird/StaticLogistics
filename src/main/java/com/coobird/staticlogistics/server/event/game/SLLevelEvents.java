package com.coobird.staticlogistics.server.event.game;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.util.CapabilityCache;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

@EventBusSubscriber(modid = Staticlogistics.MODID)
public class SLLevelEvents {

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        LinkManager.get(level).onBlockRemoved(event.getPos());
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        List<BlockPos> affected = event.getAffectedBlocks();
        if (affected.isEmpty()) return;
        LinkManager.get(level).onBlocksRemovedBulk(affected);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) {
            CapabilityCache.clearCache();
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        GlobalLogisticsManager manager = GlobalLogisticsManager.get(event.getServer());
        manager.tick();
    }
}