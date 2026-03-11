package com.coobird.staticlogistics.client.network;

import com.coobird.staticlogistics.client.ClientLinkCache;
import com.coobird.staticlogistics.network.s2c.S2CSyncLinksPacket;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ClientPayloadHandler {

    public static void handleSyncLinks(final S2CSyncLinksPacket payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientLinkCache.updateLinks(payload.links());
        });
    }
}