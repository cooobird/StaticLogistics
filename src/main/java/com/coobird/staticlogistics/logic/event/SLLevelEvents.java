package com.coobird.staticlogistics.logic.event;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.logic.GlobalLogisticsManager;
import com.coobird.staticlogistics.storage.link.LinkManager;
import com.coobird.staticlogistics.transfer.handler.CapabilityCache;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.List;

@EventBusSubscriber(modid = StaticLogistics.MODID)
public class SLLevelEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        try {
            LinkManager mgr = LinkManager.get(level);
            mgr.onBlockRemoved(event.getPos());
            mgr.markOrphanScanNeeded();
            CapabilityCache.clearPositionAndNeighbors(level, event.getPos());
        } catch (Exception e) {
            LOGGER.error("Failed to clean logistics data at {}: {}", event.getPos(), e.getMessage(), e);
        }
    }

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getLevel() instanceof Level level)) return;

        CapabilityCache.clearPosition(level, event.getPos());
        for (var side : event.getNotifiedSides()) {
            CapabilityCache.clearPosition(level, event.getPos().relative(side));
        }
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        List<BlockPos> affected = event.getAffectedBlocks();
        if (affected.isEmpty()) return;
        try {
            LinkManager mgr = LinkManager.get(level);
            mgr.onBlocksRemovedBulk(affected);
            mgr.markOrphanScanNeeded();
            affected.forEach(pos -> CapabilityCache.clearPositionAndNeighbors(level, pos));
        } catch (Exception e) {
            LOGGER.error("Failed to clean logistics data during explosion: {}", e.getMessage(), e);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(net.neoforged.neoforge.event.level.ChunkEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            CapabilityCache.clearChunk(level, event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) {
            if (event.getLevel() instanceof Level level) {
                CapabilityCache.clearDimension(level);
            }
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                LinkManager mgr = LinkManager.get(serverLevel);
                if (mgr != null) {
                    mgr.shutdown();
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        GlobalLogisticsManager manager = GlobalLogisticsManager.get(event.getServer());
        manager.tick();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            LinkManager mgr = LinkManager.get(level);
            if (mgr.isOrphanScanNeeded()) {
                mgr.validateOrphanedConfigs();
            }
        }
    }
}
