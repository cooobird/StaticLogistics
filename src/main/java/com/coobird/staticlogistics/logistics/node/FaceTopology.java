package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.transfer.LogisticsCalculator;
import com.coobird.staticlogistics.transfer.NodeQueryService;
import com.coobird.staticlogistics.transfer.NodeQuerySnapshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 面配置面向客户端目录与世界渲染的轻量拓扑摘要。
 *
 * <p>这里同步网络预览所需的身份、角色、升级效果、过滤器存在状态，以及服务端
 * 计算完成的有效接收/传输类型；不同步过滤规则、传输策略与原始类型选择等私有配置。
 */
public record FaceTopology(
    LogisticsNode node,
    Set<GroupRef> groups,
    NodeRole role,
    int maxTransferBlocks,
    boolean dimensionEffective,
    long speedMultiplier,
    long rangeMultiplier,
    long stackMultiplier,
    boolean inputFilterPresent,
    boolean outputFilterPresent,
    List<ResourceLocation> outputTypeIds,
    List<ResourceLocation> acceptedTypeIds,
    @Nullable UUID ownerId,
    String ownerName,
    long version
) {
    public FaceTopology {
        Objects.requireNonNull(node, "Topology node must not be null");
        groups = Set.copyOf(Objects.requireNonNull(groups, "Topology groups must not be null"));
        Objects.requireNonNull(role, "Topology role must not be null");
        if (maxTransferBlocks < 0) {
            throw new IllegalArgumentException("Invalid topology transfer distance");
        }
        if (speedMultiplier < 1 || rangeMultiplier < 1 || stackMultiplier < 1) {
            throw new IllegalArgumentException("Invalid topology upgrade multiplier");
        }
        outputTypeIds = List.copyOf(
            Objects.requireNonNull(outputTypeIds, "Topology output types must not be null"));
        acceptedTypeIds = List.copyOf(
            Objects.requireNonNull(acceptedTypeIds, "Topology accepted types must not be null"));
        ownerName = ownerName == null || ownerName.isBlank() ? "Unknown" : ownerName;
        UUID effectiveOwner = ownerId == null ? GroupKey.LEGACY_UNOWNED : ownerId;
        if (groups.stream().anyMatch(group -> !effectiveOwner.equals(group.key().ownerId()))) {
            throw new IllegalArgumentException("Topology group owner does not match face owner");
        }
    }

    public static FaceTopology from(
        ServerLevel level,
        LogisticsNode node,
        FaceConfigComposite config
    ) {
        Objects.requireNonNull(level, "Topology level must not be null");
        Objects.requireNonNull(config, "Face configuration must not be null");
        ContainerConfig container = config.getContainerConfig();
        NodeQuerySnapshot query = NodeQueryService.query(
            level, node.gPos().pos(), node.face()).orElse(null);
        boolean dimensionEffective = LogisticsCalculator.isDimensionEffective(container);
        int maxTransferBlocks = LogisticsCalculator.getMaxTransferBlocks(container);
        return new FaceTopology(
            node,
            config.faceConfig.getGroups(),
            config.determineRole(),
            maxTransferBlocks,
            dimensionEffective,
            LogisticsCalculator.getSpeedMultiplier(
                container),
            LogisticsCalculator.getRangeMultiplier(
                container),
            LogisticsCalculator.getStackMultiplier(
                container),
            !config.filterConfig.getUpgrades().getStackInSlot(0).isEmpty(),
            !config.filterConfig.getUpgrades().getStackInSlot(1).isEmpty(),
            query == null ? List.of() : query.outputTypeIds(),
            query == null ? List.of() : query.acceptedTypeIds(),
            config.faceConfig.getOwner(),
            config.faceConfig.getOwnerName(),
            config.getVersion()
        );
    }

    public Set<GroupKey> groupKeys() {
        return groups.stream().map(GroupRef::key).collect(Collectors.toUnmodifiableSet());
    }
}
