package com.coobird.staticlogistics.common.event.game;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.common.util.TransferUtils;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(modid = Staticlogistics.MODID)
public class SLLevelEvents {
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) {
            TransferUtils.clearCache();
        }
    }
}
