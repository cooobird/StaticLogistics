package com.coobird.staticlogistics.content.event;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.logistics.group.GlobalLogisticsManager;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import com.coobird.staticlogistics.transfer.NodeQueryService;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.EventPriority;
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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        try {
            LinkManager mgr = LinkManager.get(level);
            NodeQueryService.invalidateBlock(level, event.getPos());
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
            for (BlockPos pos : affected) NodeQueryService.invalidateBlock(level, pos);
            mgr.onBlocksRemovedBulk(affected);
            mgr.markOrphanScanNeeded();
        } catch (Exception e) {
            LOGGER.error("Failed to clean logistics data during explosion: {}", e.getMessage(), e);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LinkManager.get(level).invalidateCapabilityCache(event.getPos());
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) {
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
