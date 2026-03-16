package com.coobird.staticlogistics.common.event.game;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.common.util.TransferUtils;
import com.coobird.staticlogistics.storage.LinkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(modid = Staticlogistics.MODID)
public class SLLevelEvents {

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel currentLevel)) return;
        BlockPos pos = event.getPos();
        LinkManager currentManager = LinkManager.get(currentLevel);
        boolean handledGlobally = false;
        if (currentManager != null) {
            handledGlobally = currentManager.onBlockRemovedWithResult(pos, currentLevel);
        }
        if (!handledGlobally) {
            currentLevel.getServer().getAllLevels().forEach(level -> {
                if (level == currentLevel) return;
                LinkManager manager = LinkManager.get(level);
                if (manager != null) {
                    manager.onBlockRemovedWithResult(pos, currentLevel);
                }
            });
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) {
            TransferUtils.clearCache();
        }
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        LinkManager manager = LinkManager.get(serverLevel);
        if (manager == null) return;
        if (!event.getAffectedBlocks().isEmpty()) {
            manager.onBlocksRemovedBulk(event.getAffectedBlocks(), serverLevel);
        }
    }
}