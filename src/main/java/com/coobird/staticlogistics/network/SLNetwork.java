package com.coobird.staticlogistics.network;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.network.c2s.*;
import com.coobird.staticlogistics.network.s2c.*;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortNetworkHandler;

public class SLNetwork {
    public static final PortNetworkHandler HANDLER = new PortNetworkHandler(StaticLogistics.MODID, "3");

    public static void init() {
        HANDLER.registerInGameC2S(C2SBlueprintUndoPayload.class, C2SBlueprintUndoPayload.ID,
            C2SBlueprintUndoPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SBulkSelectNodesPayload.class, C2SBulkSelectNodesPayload.ID,
            C2SBulkSelectNodesPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SClearLinkEndpointPayload.class, C2SClearLinkEndpointPayload.ID,
            C2SClearLinkEndpointPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SClearStoredNodesPayload.class, C2SClearStoredNodesPayload.ID,
            C2SClearStoredNodesPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SConfigureFacePayload.class, C2SConfigureFacePayload.ID,
            C2SConfigureFacePayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SConfigureFacesPayload.class, C2SConfigureFacesPayload.ID,
            C2SConfigureFacesPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SApplyNodeTemplatePayload.class, C2SApplyNodeTemplatePayload.ID,
            C2SApplyNodeTemplatePayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SCreateEmptyGroupPayload.class, C2SCreateEmptyGroupPayload.ID,
            C2SCreateEmptyGroupPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SDeleteConnectionPayload.class, C2SDeleteConnectionPayload.ID,
            C2SDeleteConnectionPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SDeleteGroupPayload.class, C2SDeleteGroupPayload.ID,
            C2SDeleteGroupPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SGroupRenamePayload.class, C2SGroupRenamePayload.ID,
            C2SGroupRenamePayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SOpenHandFilterPayload.class, C2SOpenHandFilterPayload.ID,
            C2SOpenHandFilterPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SOpenLinkEndpointPayload.class, C2SOpenLinkEndpointPayload.ID,
            C2SOpenLinkEndpointPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SOpenNodeFilterPayload.class, C2SOpenNodeFilterPayload.ID,
            C2SOpenNodeFilterPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SRenameConnectionPayload.class, C2SRenameConnectionPayload.ID,
            C2SRenameConnectionPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SReturnToLinkConfiguratorPayload.class, C2SReturnToLinkConfiguratorPayload.ID,
            C2SReturnToLinkConfiguratorPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SSelectLinkEndpointSidePayload.class, C2SSelectLinkEndpointSidePayload.ID,
            C2SSelectLinkEndpointSidePayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SUpdateBlueprintPreviewPayload.class, C2SUpdateBlueprintPreviewPayload.ID,
            C2SUpdateBlueprintPreviewPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SUpdateFilterOnHandPayload.class, C2SUpdateFilterOnHandPayload.ID,
            C2SUpdateFilterOnHandPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SUpdateFilterOnItemPayload.class, C2SUpdateFilterOnItemPayload.ID,
            C2SUpdateFilterOnItemPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SUpdateToolModePayload.class, C2SUpdateToolModePayload.ID,
            C2SUpdateToolModePayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SUpdateToolTypesPayload.class, C2SUpdateToolTypesPayload.ID,
            C2SUpdateToolTypesPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SUpdateToolGroupPayload.class, C2SUpdateToolGroupPayload.ID,
            C2SUpdateToolGroupPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameC2S(C2SUpdateToolConnectionPayload.class, C2SUpdateToolConnectionPayload.ID,
            C2SUpdateToolConnectionPayload.STREAM_CODEC, IPortPacket.C2S::handle);
        HANDLER.registerInGameS2C(S2CConfigSyncPayload.class, S2CConfigSyncPayload.ID,
            S2CConfigSyncPayload.STREAM_CODEC, IPortPacket.S2C::handle);
        HANDLER.registerInGameS2C(S2CClearLinkEndpointPayload.class, S2CClearLinkEndpointPayload.ID,
            S2CClearLinkEndpointPayload.STREAM_CODEC, IPortPacket.S2C::handle);
        HANDLER.registerInGameS2C(S2CAccessSnapshotPayload.class, S2CAccessSnapshotPayload.ID,
            S2CAccessSnapshotPayload.STREAM_CODEC, IPortPacket.S2C::handle);
        HANDLER.registerInGameS2C(S2CGroupDirectoryPayload.class, S2CGroupDirectoryPayload.ID,
            S2CGroupDirectoryPayload.STREAM_CODEC, IPortPacket.S2C::handle);
        HANDLER.registerInGameS2C(S2CRemoveFaceTopologyPayload.class, S2CRemoveFaceTopologyPayload.ID,
            S2CRemoveFaceTopologyPayload.STREAM_CODEC, IPortPacket.S2C::handle);
        HANDLER.registerInGameS2C(S2CSelectLinkEndpointPayload.class, S2CSelectLinkEndpointPayload.ID,
            S2CSelectLinkEndpointPayload.STREAM_CODEC, IPortPacket.S2C::handle);
        HANDLER.registerInGameS2C(S2CTopologyUpdatePayload.class, S2CTopologyUpdatePayload.ID,
            S2CTopologyUpdatePayload.STREAM_CODEC, IPortPacket.S2C::handle);
    }
}
