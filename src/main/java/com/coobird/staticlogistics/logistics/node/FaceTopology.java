package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 面配置面向客户端目录与世界渲染的轻量拓扑摘要。
 *
 * <p>这里只保留身份、角色和频道，不包含过滤器、传输策略、类型选择等私有配置。
 */
public record FaceTopology(
    LogisticsNode node,
    Set<GroupRef> groups,
    NodeRole role,
    int inputChannel,
    int outputChannel,
    @Nullable UUID ownerId,
    String ownerName,
    long version
) {
    public FaceTopology {
        Objects.requireNonNull(node, "Topology node must not be null");
        groups = Set.copyOf(Objects.requireNonNull(groups, "Topology groups must not be null"));
        Objects.requireNonNull(role, "Topology role must not be null");
        if (inputChannel < LinkConfig.UNSPECIFIED_CHANNEL || inputChannel > LinkConfig.MAX_CHANNEL) {
            throw new IllegalArgumentException("Invalid topology input channel: " + inputChannel);
        }
        if (outputChannel < LinkConfig.UNSPECIFIED_CHANNEL || outputChannel > LinkConfig.MAX_CHANNEL) {
            throw new IllegalArgumentException("Invalid topology output channel: " + outputChannel);
        }
        ownerName = ownerName == null || ownerName.isBlank() ? "Unknown" : ownerName;
        UUID effectiveOwner = ownerId == null ? GroupKey.LEGACY_UNOWNED : ownerId;
        if (groups.stream().anyMatch(group -> !effectiveOwner.equals(group.key().ownerId()))) {
            throw new IllegalArgumentException("Topology group owner does not match face owner");
        }
    }

    public static FaceTopology from(LogisticsNode node, FaceConfigComposite config) {
        Objects.requireNonNull(config, "Face configuration must not be null");
        return new FaceTopology(
            node,
            config.faceConfig.getGroups(),
            config.determineRole(),
            config.linkConfig.getInputChannel(),
            config.linkConfig.getOutputChannel(),
            config.faceConfig.getOwner(),
            config.faceConfig.getOwnerName(),
            config.getVersion()
        );
    }

    public Set<GroupKey> groupKeys() {
        return groups.stream().map(GroupRef::key).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
