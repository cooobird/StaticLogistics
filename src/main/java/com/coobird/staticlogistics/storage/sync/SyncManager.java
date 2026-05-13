package com.coobird.staticlogistics.storage.sync;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public class SyncManager {
    private final ResourceKey<Level> dimension;
    private final GlobalLogisticsManager globalManager;

    public SyncManager(ResourceKey<Level> dimension, GlobalLogisticsManager globalManager) {
        this.dimension = dimension;
        this.globalManager = globalManager;
    }

    public void syncNode(BlockPos pos, Direction face, FaceConfigComposite config) {
        LogisticsNode node = new LogisticsNode(GlobalPos.of(dimension, pos), face);
        if (config.faceConfig.hasGroup()) {
            NodeRole role = config.determineRole();
            globalManager.registerNode(config.faceConfig.getGroupId(), node, role);
        } else {
            globalManager.unregisterNode(node);
        }
    }
}