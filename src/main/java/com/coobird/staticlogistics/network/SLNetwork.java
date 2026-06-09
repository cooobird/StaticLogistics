package com.coobird.staticlogistics.network;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.network.c2s.*;
import com.coobird.staticlogistics.network.s2c.S2CConfigSyncPayload;
import com.coobird.staticlogistics.network.s2c.S2CRemoveBulkFaceConfigPayload;
import com.coobird.staticlogistics.network.s2c.S2CSyncBulkFaceConfigPayload;
import com.coobird.staticlogistics.network.s2c.S2CSyncFaceConfigPayload;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortNetworkHandler;

public class SLNetwork {
    public static final PortNetworkHandler HANDLER = new PortNetworkHandler(StaticLogistics.MODID, "1");

    public static void init() {
        HANDLER.registerInGameC2S(C2SClearStoredNodesPayload.class, C2SClearStoredNodesPayload.ID,
            C2SClearStoredNodesPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SConfigureFacePayload.class, C2SConfigureFacePayload.ID,
            C2SConfigureFacePayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SDeleteGroupPayload.class, C2SDeleteGroupPayload.ID,
            C2SDeleteGroupPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SGroupRenamePayload.class, C2SGroupRenamePayload.ID,
            C2SGroupRenamePayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SOpenHandFilterPayload.class, C2SOpenHandFilterPayload.ID,
            C2SOpenHandFilterPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SOpenNodeConfigPayload.class, C2SOpenNodeConfigPayload.ID,
            C2SOpenNodeConfigPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SRemoveLinkPayload.class, C2SRemoveLinkPayload.ID,
            C2SRemoveLinkPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SUpdateBlueprintPreviewPayload.class, C2SUpdateBlueprintPreviewPayload.ID,
            C2SUpdateBlueprintPreviewPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SUpdateFilterOnHandPayload.class, C2SUpdateFilterOnHandPayload.ID,
            C2SUpdateFilterOnHandPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SUpdateFilterOnItemPayload.class, C2SUpdateFilterOnItemPayload.ID,
            C2SUpdateFilterOnItemPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SUpdateToolSettingsPayload.class, C2SUpdateToolSettingsPayload.ID,
            C2SUpdateToolSettingsPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameS2C(S2CConfigSyncPayload.class, S2CConfigSyncPayload.ID,
            S2CConfigSyncPayload.STREAM_CODEC, IPortPacket.S2C::handle);
        HANDLER.registerInGameS2C(S2CRemoveBulkFaceConfigPayload.class, S2CRemoveBulkFaceConfigPayload.ID,
            S2CRemoveBulkFaceConfigPayload.STREAM_CODEC, IPortPacket.S2C::handle);
        HANDLER.registerInGameS2C(S2CSyncBulkFaceConfigPayload.class, S2CSyncBulkFaceConfigPayload.ID,
            S2CSyncBulkFaceConfigPayload.STREAM_CODEC, IPortPacket.S2C::handle);
        HANDLER.registerInGameS2C(S2CSyncFaceConfigPayload.class, S2CSyncFaceConfigPayload.ID,
            S2CSyncFaceConfigPayload.STREAM_CODEC, IPortPacket.S2C::handle);
    }
}
