package com.coobird.staticlogistics.network;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.network.c2s.*;
import com.coobird.staticlogistics.network.s2c.*;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortNetworkHandler;

public class SLNetwork {
    public static final PortNetworkHandler HANDLER = new PortNetworkHandler(StaticLogistics.MODID, "2");

    public static void init() {
        HANDLER.registerInGameC2S(C2SBlueprintUndoPayload.class, C2SBlueprintUndoPayload.ID,
            C2SBlueprintUndoPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SClearStoredNodesPayload.class, C2SClearStoredNodesPayload.ID,
            C2SClearStoredNodesPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SConfigureFacePayload.class, C2SConfigureFacePayload.ID,
            C2SConfigureFacePayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SCreateEmptyGroupPayload.class, C2SCreateEmptyGroupPayload.ID,
            C2SCreateEmptyGroupPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SDeleteGroupPayload.class, C2SDeleteGroupPayload.ID,
            C2SDeleteGroupPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SGroupRenamePayload.class, C2SGroupRenamePayload.ID,
            C2SGroupRenamePayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SOpenHandFilterPayload.class, C2SOpenHandFilterPayload.ID,
            C2SOpenHandFilterPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SOpenNodeConfigPayload.class, C2SOpenNodeConfigPayload.ID,
            C2SOpenNodeConfigPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SOpenNodeFilterPayload.class, C2SOpenNodeFilterPayload.ID,
            C2SOpenNodeFilterPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SReturnToNodeConfigPayload.class, C2SReturnToNodeConfigPayload.ID,
            C2SReturnToNodeConfigPayload.STREAM_CODEC, IPortPacket.C2S::handle);
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
        HANDLER.registerInGameS2C(S2CAccessSnapshotPayload.class, S2CAccessSnapshotPayload.ID,
            S2CAccessSnapshotPayload.STREAM_CODEC, IPortPacket.S2C::handle);
        HANDLER.registerInGameS2C(S2CGroupDirectoryPayload.class, S2CGroupDirectoryPayload.ID,
            S2CGroupDirectoryPayload.STREAM_CODEC, IPortPacket.S2C::handle);
        HANDLER.registerInGameS2C(S2CRemoveFaceTopologyPayload.class, S2CRemoveFaceTopologyPayload.ID,
            S2CRemoveFaceTopologyPayload.STREAM_CODEC, IPortPacket.S2C::handle);
        HANDLER.registerInGameS2C(S2CTopologyUpdatePayload.class, S2CTopologyUpdatePayload.ID,
            S2CTopologyUpdatePayload.STREAM_CODEC, IPortPacket.S2C::handle);
    }
}
