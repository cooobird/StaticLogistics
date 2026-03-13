package com.coobird.staticlogistics.client.event;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.core.ClientLinkCache;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(modid = Staticlogistics.MODID, value = Dist.CLIENT)
public class ClientEvents {
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ClientLinkCache.invalidate();
        }
    }
}