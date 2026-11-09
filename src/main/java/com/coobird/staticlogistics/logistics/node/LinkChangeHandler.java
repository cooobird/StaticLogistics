package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.node.sync.NodeMembershipPublisher;
import com.coobird.staticlogistics.transfer.LogisticsTicker;
import com.coobird.staticlogistics.transfer.NodeQueryService;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/// 配置变更后的持久化、缓存、调度与同步处理；连接修改由事务内的图服务负责。
public class LinkChangeHandler {
    private final ServerLevel level;
    private final NodeMembershipPublisher membershipPublisher;
    private final LinkManager linkManager;
    private final Map<EndpointConfig, TargetState> targetStates = new WeakHashMap<>();

    /// 只有影响排序或候选资格的字段参与失效；过滤、存量等在实际传输时实时校验。
    private record TargetState(long priority, NodeRole role, boolean enabled, ChannelRedstoneMode redstone,
                               List<ResourceLocation> types, Set<GroupKey> groups) {
        static TargetState of(EndpointConfig config) {
            return new TargetState(config.linkConfig.getPriority(), config.determineRole(),
                config.linkConfig.isEnabled(), config.linkConfig.getRedstoneMode(), List.copyOf(config.getSelectedTypeIds()),
                Set.copyOf(config.faceConfig.getGroupKeys()));
        }
    }

    public LinkChangeHandler(ServerLevel level, NodeMembershipPublisher membershipPublisher,
                             LinkManager linkManager) {
        this.level = level;
        this.membershipPublisher = membershipPublisher;
        this.linkManager = linkManager;
    }

    /// 面配置变更回调：标记脏数据、刷新缓存、同步到客户端
    public void onEndpointConfigChanged(EndpointAddress key, EndpointConfig cfg) {
        if (linkManager.isLifecycleRemovalInProgress()) return;
        /// 嵌套图清理可能在 BulkEdit 结束前移除当前面，陈旧回调不得再次修复链接。
        if (linkManager.getEndpointConfig(key) != cfg) return;
        if (NodeMutationTransaction.defer(level.getServer(),
            new FaceChangeKey(linkManager, key), () -> onEndpointConfigChanged(key, cfg))) return;
        if (cfg.isDefault()) {
            linkManager.markFaceDirty(key);
            if (!linkManager.removeFaceConfig(key)) {
                throw new IllegalStateException("Default face disappeared before lifecycle removal: " + key);
            }
            return;
        }
        NodeReconciliationQueue.execute(level.getServer(), new FaceChangeKey(linkManager, key), () -> {
            EndpointConfig current = linkManager.getEndpointConfig(key);
            if (current == cfg && !current.isDefault() && !linkManager.isLifecycleRemovalInProgress())
                reconcileEndpoint(key, current);
        });
    }

    private void reconcileEndpoint(EndpointAddress key, EndpointConfig cfg) {
        linkManager.markFaceDirty(key);
        long topologyRevision = cfg.topologyRevision();
        TargetState targetState = TargetState.of(cfg);
        if (!targetState.equals(targetStates.get(cfg))) {
            LogisticsTicker.targetChanged(level, cfg);
            targetStates.put(cfg, targetState);
        }
        NodeQueryService.invalidateNode(level.getServer(), linkManager.createNodeFromKey(key));
        cfg.setVersion(linkManager.normalizeVersion(key, cfg.getVersion()));
        LogisticsTicker.wakeup(level, key);
        linkManager.refreshLocalCache(key, cfg);
        /// 数值修改刷新持久化、调度和缓存即可，不重建连接成员关系。
        if (!cfg.hasTopologyChange()) return;
        LogisticsNode currentNode = linkManager.createNodeFromKey(key);
        membershipPublisher.syncNode(currentNode, cfg);
        if (cfg.faceConfig.hasGroup()) {
            linkManager.scheduleNetworkSync(currentNode);
        }
        cfg.acknowledgeTopology(topologyRevision);
    }

    /// 容器配置变更回调：保留拓扑，只刷新能力缓存与调度状态。
    /// 范围或维度能力暂时不足时，传输管线会阻止本次传输；升级恢复后原链接可直接复用。
    public void onContainerConfigChanged(ContainerConfig config) {
        onContainerConfigChanged(config.getPos().immutable(), config);
    }

    private void onContainerConfigChanged(BlockPos position, ContainerConfig config) {
        if (linkManager.isLifecycleRemovalInProgress()
            || !position.equals(config.getPos()) || linkManager.getContainerConfig(position) != config) return;
        ContainerChangeKey key = new ContainerChangeKey(linkManager, position);
        if (NodeMutationTransaction.defer(level.getServer(), key,
            () -> onContainerConfigChanged(position, config))) return;
        NodeReconciliationQueue.execute(level.getServer(), key, () -> {
            /// 固定入队时的位置和对象身份，移动、删除或替换后不再操作旧任务的目标。
            if (position.equals(config.getPos()) && linkManager.getContainerConfig(position) == config
                && !linkManager.isLifecycleRemovalInProgress()) reconcileContainer(config);
        });
    }

    private void reconcileContainer(ContainerConfig config) {
        linkManager.markContainerDirty(config.getPos().asLong());
        for (EndpointAddress faceKey : config.getLinkedFaceKeys()) {
            EndpointConfig faceCfg = linkManager.getEndpointConfig(faceKey);
            if (faceCfg != null) {
                NodeQueryService.invalidateNode(level.getServer(), linkManager.createNodeFromKey(faceKey));
                faceCfg.setVersion(linkManager.normalizeVersion(faceKey, faceCfg.getVersion()));
                LogisticsTicker.wakeup(level, faceKey);
                linkManager.markFaceDirty(faceKey);
                linkManager.refreshLocalCache(faceKey, faceCfg);
                linkManager.scheduleNetworkSync(linkManager.createNodeFromKey(faceKey));
            }
        }
    }

    private record FaceChangeKey(LinkManager manager, EndpointAddress faceKey) {
    }

    private record ContainerChangeKey(LinkManager manager, BlockPos pos) {
    }
}
