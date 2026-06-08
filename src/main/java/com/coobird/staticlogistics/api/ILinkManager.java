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
 *
 * <p>职责：
 * <ul>
 *   <li>面配置 CRUD</li>
 *   <li>容器配置查询</li>
 *   <li>网络同步</li>
 *   <li>批量操作</li>
 * </ul>
 *
 * <p>实现类：{@link com.coobird.staticlogistics.storage.link.LinkManager}
 */
public interface ILinkManager {

    // 面配置 CRUD
    @Nullable
    FaceConfigComposite getFaceConfig(long key);

    FaceConfigComposite getOrCreateFaceConfig(BlockPos pos, Direction face);

    void removeFaceConfig(long key);

    void removeFaceConfigDataOnly(long key);

    void removeLink(LogisticsNode source, LogisticsNode target);

    void cleanUpFaceIfNeeded(LogisticsNode node, FaceConfigComposite cfg);

    // 容器配置
    @Nullable
    ContainerConfig getContainerConfig(BlockPos pos);

    ContainerConfig getOrCreateContainerConfig(BlockPos pos);

    // 缓存
    boolean hasActiveProviders();

    Set<Long> getAllConfigKeys();

    // 网络同步
    void scheduleNetworkSync(LogisticsNode node);

    void syncToPlayer(ServerPlayer player);

    void syncConfigToClients(BlockPos pos);

    void syncNodeToDimensionDirect(LogisticsNode node);

    void syncNodeToPlayer(ServerPlayer player, LogisticsNode node);

    // 批量操作
    void onBlocksRemovedBulk(Collection<BlockPos> positions);

    // 脏数据
    void markFaceDirty(long faceKey);

    void markContainerDirty(long containerKey);

    // 生命周期
    void shutdown();
}
