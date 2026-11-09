package com.coobird.staticlogistics.content.event;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.logistics.group.GlobalLogisticsManager;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.mesdag.portlib.event.tick.PortServerTickEvent;
import org.slf4j.Logger;

import java.util.List;

@EventBusSubscriber(modid = StaticLogistics.MODID)
public class SLLevelEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int INTEGRITY_SCAN_INTERVAL = 200;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        try {
            LinkManager mgr = LinkManager.get(level);
            mgr.onBlockRemoved(event.getPos());
            mgr.markOrphanScanNeeded();
        } catch (Exception e) {
            LOGGER.error("Failed to clean logistics data at {}: {}", event.getPos(), e.getMessage(), e);
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
        } catch (Exception e) {
            LOGGER.error("Failed to clean logistics data during explosion: {}", e.getMessage(), e);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled()) return;
        if (event.getLevel() instanceof ServerLevel level) {
            LinkManager manager = LinkManager.get(level);
            manager.onBlockRemoved(event.getPos());
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {

        if (event.getLevel() instanceof ServerLevel level) LinkManager.release(level);
    }

    public static void onServerTick(PortServerTickEvent.Post event) {
        GlobalLogisticsManager manager = GlobalLogisticsManager.get(event.getServer());
        manager.tick();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            LinkManager mgr = LinkManager.get(level);
            if (level.getGameTime() % INTEGRITY_SCAN_INTERVAL == 0) {
                mgr.markOrphanScanNeeded();
            }
            if (mgr.isOrphanScanNeeded()) {
                mgr.validateOrphanedConfigs();
            }
        }
    }
}
