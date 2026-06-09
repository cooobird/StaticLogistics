package com.coobird.staticlogistics.api;

import com.coobird.staticlogistics.storage.model.ContainerConfig;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * 物流链接管理器接口 —— 定义外部模块可访问的操作。
 */
public interface ILinkManager {

    @Nullable
    FaceConfigComposite getFaceConfig(long key);

    FaceConfigComposite getOrCreateFaceConfig(BlockPos pos, Direction face);

    void removeFaceConfig(long key);

    void removeFaceConfigDataOnly(long key);

    void removeLink(LogisticsNode source, LogisticsNode target);

    void cleanUpFaceIfNeeded(LogisticsNode node, FaceConfigComposite cfg);

    @Nullable
    ContainerConfig getContainerConfig(BlockPos pos);

    ContainerConfig getOrCreateContainerConfig(BlockPos pos);

    boolean hasActiveProviders();

    Set<Long> getAllConfigKeys();

    void scheduleNetworkSync(LogisticsNode node);

    void syncToPlayer(ServerPlayer player);

    void syncConfigToClients(BlockPos pos);

    void syncNodeToDimensionDirect(LogisticsNode node);

    void syncNodeToPlayer(ServerPlayer player, LogisticsNode node);

    void onBlocksRemovedBulk(Collection<BlockPos> positions);

    void markFaceDirty(long faceKey);

    void markContainerDirty(long containerKey);

    void shutdown();
}
