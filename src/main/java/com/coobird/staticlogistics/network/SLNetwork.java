package com.coobird.staticlogistics.network;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.network.c2s.*;
import com.coobird.staticlogistics.network.s2c.*;
import org.mesdag.portlib.network.PortPayloadHandler;

public final class SLNetwork {
    private static final PortPayloadHandler registrar = new PortPayloadHandler(StaticLogistics.MODID, "58");

    private SLNetwork() {
    }

    public static void init() {
        registrar.registerInGameC2S(C2SBeginBlueprintPlacementPayload.class, C2SBeginBlueprintPlacementPayload.ID, C2SBeginBlueprintPlacementPayload.STREAM_CODEC);
        registrar.registerInGameC2S(C2SObserveTransferRatesPayload.class, C2SObserveTransferRatesPayload.ID, C2SObserveTransferRatesPayload.STREAM_CODEC);
        registrar.registerInGameS2C(S2CTransferRatesPayload.class, S2CTransferRatesPayload.ID, S2CTransferRatesPayload.STREAM_CODEC);
        registrar.registerInGameS2C(S2CConfiguratorReadyPayload.class, S2CConfiguratorReadyPayload.ID, S2CConfiguratorReadyPayload.STREAM_CODEC);
        registrar.registerInGameC2S(C2SManageConnectionsPayload.class, C2SManageConnectionsPayload.ID, C2SManageConnectionsPayload.STREAM_CODEC);
        registrar.registerInGameC2S(C2SManageChannelPayload.class, C2SManageChannelPayload.ID, C2SManageChannelPayload.STREAM_CODEC);
        registrar.registerInGameC2S(C2SCreateChannelPayload.class, C2SCreateChannelPayload.ID, C2SCreateChannelPayload.STREAM_CODEC);
        registrar.registerInGameS2C(S2CChannelCreatedPayload.class, S2CChannelCreatedPayload.ID, S2CChannelCreatedPayload.STREAM_CODEC);
        registrar.registerInGameC2S(C2SGroupHierarchyPayload.class, C2SGroupHierarchyPayload.ID, C2SGroupHierarchyPayload.STREAM_CODEC);
        registrar.registerInGameS2C(S2CGroupHierarchyPayload.class, S2CGroupHierarchyPayload.ID, S2CGroupHierarchyPayload.STREAM_CODEC);
        registrar.registerInGameS2C(S2CTopologyUpdatePayload.class, S2CTopologyUpdatePayload.ID, S2CTopologyUpdatePayload.STREAM_CODEC);
        registrar.registerInGameS2C(S2CConfigSyncPayload.class, S2CConfigSyncPayload.ID, S2CConfigSyncPayload.STREAM_CODEC);
        registrar.registerInGameS2C(S2CStoredSelectionPage.class, S2CStoredSelectionPage.ID, S2CStoredSelectionPage.STREAM_CODEC);
        registrar.registerInGameS2C(S2CPresetPreviewPayload.class, S2CPresetPreviewPayload.ID, S2CPresetPreviewPayload.STREAM_CODEC);
        registrar.registerInGameC2S(C2SPresetPreviewPayload.class, C2SPresetPreviewPayload.ID, C2SPresetPreviewPayload.STREAM_CODEC);
        registrar.registerInGameS2C(S2CRemoveFaceTopologyPayload.class, S2CRemoveFaceTopologyPayload.ID, S2CRemoveFaceTopologyPayload.STREAM_CODEC);
        registrar.registerInGameS2C(S2CGroupDirectoryPayload.class, S2CGroupDirectoryPayload.ID, S2CGroupDirectoryPayload.STREAM_CODEC);
        registrar.registerInGameS2C(S2CAccessSnapshotPayload.class, S2CAccessSnapshotPayload.ID, S2CAccessSnapshotPayload.STREAM_CODEC);
        registrar.registerInGameC2S(C2SRequestAccessSnapshotPayload.class, C2SRequestAccessSnapshotPayload.ID, C2SRequestAccessSnapshotPayload.STREAM_CODEC);
        registrar.registerInGameS2C(S2CSelectLinkEndpointPayload.class, S2CSelectLinkEndpointPayload.ID, S2CSelectLinkEndpointPayload.STREAM_CODEC);
        registrar.registerInGameS2C(S2CClearLinkEndpointPayload.class, S2CClearLinkEndpointPayload.ID, S2CClearLinkEndpointPayload.STREAM_CODEC);
        registrar.registerInGameC2S(C2SConfigureFacePayload.class, C2SConfigureFacePayload.ID, C2SConfigureFacePayload.STREAM_CODEC);
        registrar.registerInGameC2S(C2SBulkSelectNodesPayload.class, C2SBulkSelectNodesPayload.ID, C2SBulkSelectNodesPayload.STREAM_CODEC);
        registrar.registerInGameC2S(C2SOpenNodeFilterPayload.class, C2SOpenNodeFilterPayload.ID, C2SOpenNodeFilterPayload.STREAM_CODEC);
        registrar.registerInGameC2S(C2SReturnToLinkConfiguratorPayload.class, C2SReturnToLinkConfiguratorPayload.ID, C2SReturnToLinkConfiguratorPayload.STREAM_CODEC);
        registrar.registerInGameS2C(S2CToolModeResultPayload.class, S2CToolModeResultPayload.ID, S2CToolModeResultPayload.STREAM_CODEC);
        registrar.registerInGameC2S(C2SUpdateToolModePayload.class, C2SUpdateToolModePayload.ID, C2SUpdateToolModePayload.STREAM_CODEC);
        registrar.registerInGameC2S(C2SUpdateToolTypesPayload.class, C2SUpdateToolTypesPayload.ID, C2SUpdateToolTypesPayload.STREAM_CODEC);
        registrar.registerInGameC2S(C2SUpdateToolGroupPayload.class, C2SUpdateToolGroupPayload.ID, C2SUpdateToolGroupPayload.STREAM_CODEC);
        registrar.registerInGameC2S(C2SUpdateToolConnectionPayload.class, C2SUpdateToolConnectionPayload.ID, C2SUpdateToolConnectionPayload.STREAM_CODEC);
        registrar.registerInGameC2S(C2SGroupRenamePayload.class, C2SGroupRenamePayload.ID, C2SGroupRenamePayload.STREAM_CODEC);
        registrar.registerInGameC2S(C2SUpdateFilterOnItemPayload.class, C2SUpdateFilterOnItemPayload.ID, C2SUpdateFilterOnItemPayload.STREAM_CODEC);
        registrar.registerInGameC2S(C2SUpdateFilterOnHandPayload.class, C2SUpdateFilterOnHandPayload.ID, C2SUpdateFilterOnHandPayload.STREAM_CODEC);
        registrar.registerInGameC2S(C2SOpenHandFilterPayload.class, C2SOpenHandFilterPayload.ID, C2SOpenHandFilterPayload.STREAM_CODEC);
        registrar.registerInGameC2S(C2SUpdateBlueprintPreviewPayload.class, C2SUpdateBlueprintPreviewPayload.ID, C2SUpdateBlueprintPreviewPayload.STREAM_CODEC);
        registrar.registerInGameC2S(C2SOpenLinkEndpointPayload.class, C2SOpenLinkEndpointPayload.ID, C2SOpenLinkEndpointPayload.STREAM_CODEC);
        registrar.registerInGameC2S(C2SClearLinkEndpointPayload.class, C2SClearLinkEndpointPayload.ID, C2SClearLinkEndpointPayload.STREAM_CODEC);
        registrar.registerInGameC2S(C2SClearStoredNodesPayload.class, C2SClearStoredNodesPayload.ID, C2SClearStoredNodesPayload.STREAM_CODEC);
        registrar.registerInGameC2S(C2SDeleteGroupPayload.class, C2SDeleteGroupPayload.ID, C2SDeleteGroupPayload.STREAM_CODEC);
        registrar.registerInGameC2S(C2SSelectBlueprintGroupPayload.class, C2SSelectBlueprintGroupPayload.ID, C2SSelectBlueprintGroupPayload.STREAM_CODEC);
        registrar.registerInGameC2S(C2SCopyBlueprintSelectionPayload.class, C2SCopyBlueprintSelectionPayload.ID, C2SCopyBlueprintSelectionPayload.STREAM_CODEC);
        registrar.registerInGameS2C(S2CBlueprintCopyResultPayload.class, S2CBlueprintCopyResultPayload.ID, S2CBlueprintCopyResultPayload.STREAM_CODEC);
        registrar.registerInGameC2S(C2SBlueprintUndoPayload.class, C2SBlueprintUndoPayload.ID, C2SBlueprintUndoPayload.STREAM_CODEC);
        registrar.registerInGameC2S(C2SCreateEmptyGroupPayload.class, C2SCreateEmptyGroupPayload.ID, C2SCreateEmptyGroupPayload.STREAM_CODEC);
    }
}
