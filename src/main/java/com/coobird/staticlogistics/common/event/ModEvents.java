package com.coobird.staticlogistics.common.event;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.client.network.ClientPayloadHandler;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.network.c2s.C2SRemoveLinkPayload;
import com.coobird.staticlogistics.network.c2s.C2SUpdateSettingsPayload;
import com.coobird.staticlogistics.network.s2c.S2CSyncLinksPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = Staticlogistics.MODID)
public class ModEvents {

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        LinkManager manager = LinkManager.get(level);
        manager.onBlockRemoved(pos);
        manager.syncToAll(level);
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
            .playToClient(S2CSyncLinksPacket.TYPE, S2CSyncLinksPacket.STREAM_CODEC, ClientPayloadHandler::handleSyncLinks)
            .playToServer(C2SUpdateSettingsPayload.TYPE, C2SUpdateSettingsPayload.STREAM_CODEC, C2SUpdateSettingsPayload::handle)
            .playToServer(C2SRemoveLinkPayload.TYPE, C2SRemoveLinkPayload.STREAM_CODEC, C2SRemoveLinkPayload::handle);
    }
}