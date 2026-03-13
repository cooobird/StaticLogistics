package com.coobird.staticlogistics.common.event;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.network.c2s.C2SConfigureFacePayload;
import com.coobird.staticlogistics.network.c2s.C2SRemoveLinkPayload;
import com.coobird.staticlogistics.network.c2s.C2SUpdateToolSettingsPayload;
import com.coobird.staticlogistics.network.s2c.S2CAddLinkPayload;
import com.coobird.staticlogistics.network.s2c.S2CRemoveLinkPayload;
import com.coobird.staticlogistics.network.s2c.S2CSyncFaceConfigPacket;
import com.coobird.staticlogistics.network.s2c.S2CSyncLinksPacket;
import com.coobird.staticlogistics.storage.LinkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Staticlogistics.MODID)
public class SLEvents {

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        LinkManager manager = LinkManager.get(level);

        manager.onBlockRemoved(pos, level);

        manager.syncLinksToAll(level);
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(S2CSyncLinksPacket.TYPE, S2CSyncLinksPacket.STREAM_CODEC, S2CSyncLinksPacket::handle);
        registrar.playToClient(S2CSyncFaceConfigPacket.TYPE, S2CSyncFaceConfigPacket.STREAM_CODEC, S2CSyncFaceConfigPacket::handle);
        registrar.playToClient(S2CRemoveLinkPayload.TYPE, S2CRemoveLinkPayload.STREAM_CODEC, S2CRemoveLinkPayload::handle);
        registrar.playToClient(S2CAddLinkPayload.TYPE, S2CAddLinkPayload.STREAM_CODEC, S2CAddLinkPayload::handle);

        registrar.playToServer(C2SRemoveLinkPayload.TYPE, C2SRemoveLinkPayload.STREAM_CODEC, C2SRemoveLinkPayload::handle);
        registrar.playToServer(C2SConfigureFacePayload.TYPE, C2SConfigureFacePayload.STREAM_CODEC, C2SConfigureFacePayload::handle);
        registrar.playToServer(C2SUpdateToolSettingsPayload.TYPE, C2SUpdateToolSettingsPayload.STREAM_CODEC, C2SUpdateToolSettingsPayload::handle);
    }
}