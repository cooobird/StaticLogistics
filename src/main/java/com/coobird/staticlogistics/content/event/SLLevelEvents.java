package com.coobird.staticlogistics.content.event;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.logistics.group.GlobalLogisticsManager;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import com.coobird.staticlogistics.transfer.CapabilityCache;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.List;

@Mod.EventBusSubscriber(modid = StaticLogistics.MODID)
public class SLLevelEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        try {
            CapabilityCache.clearPositionAndNeighbors(level, event.getPos());
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
            for (BlockPos pos : affected) {
                CapabilityCache.clearPositionAndNeighbors(level, pos);
            }
            LinkManager mgr = LinkManager.get(level);
            mgr.onBlocksRemovedBulk(affected);
            mgr.markOrphanScanNeeded();
        } catch (Exception e) {
            LOGGER.error("Failed to clean logistics data during explosion: {}", e.getMessage(), e);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) {
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                CapabilityCache.clearDimension(serverLevel);
                LinkManager mgr = LinkManager.get(serverLevel);
                if (mgr != null) {
                    mgr.shutdown();
                }
            }
        }
    }

    /**
     * 方块放置完成后清除当前位置及邻接面的旧能力句柄。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled()) return;
        if (event.getLevel() instanceof ServerLevel level) {
            CapabilityCache.clearPositionAndNeighbors(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        CapabilityCache.clearPosition(level, event.getPos());
        event.getNotifiedSides().forEach(side -> CapabilityCache.clearPosition(level, event.getPos().relative(side)));
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            CapabilityCache.clearChunk(level, event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
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
