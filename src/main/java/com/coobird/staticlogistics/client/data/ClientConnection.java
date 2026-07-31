package com.coobird.staticlogistics.client.data;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.node.FaceTopology;

import java.util.Comparator;
import java.util.Objects;

/**
 * 客户端用于展示的一条唯一连接。
 *
 * <p>服务端拓扑会从两个端点分别投影出边，这里只做无损去重。端点顺序仅用于稳定显示，
 * 传输方向始终由两端实时角色推导，绝不写回或替代服务端连接关系。
 */
public record ClientConnection(
    GroupKey groupKey,
    LogisticsNode first,
    LogisticsNode second,
    FaceTopology firstTopology,
    FaceTopology secondTopology,
    String displayName
) {
    public static final Comparator<ClientConnection> DISPLAY_ORDER = Comparator
        .comparing(ClientConnection::first, ConnectionKey.NODE_ORDER)
        .thenComparing(ClientConnection::second, ConnectionKey.NODE_ORDER);

    public ClientConnection {
        Objects.requireNonNull(groupKey, "Connection group must not be null");
        Objects.requireNonNull(first, "First connection endpoint must not be null");
        Objects.requireNonNull(second, "Second connection endpoint must not be null");
        Objects.requireNonNull(firstTopology, "First endpoint topology must not be null");
        Objects.requireNonNull(secondTopology, "Second endpoint topology must not be null");
        Objects.requireNonNull(displayName, "Connection display name must not be null");
        if (ConnectionKey.NODE_ORDER.compare(first, second) >= 0) {
            throw new IllegalArgumentException("Connection endpoints must use canonical order");
        }
    }

    public ConnectionKey key() {
        return new ConnectionKey(groupKey, first, second);
    }

    public boolean transfersFirstToSecond() {
        return canTransfer(firstTopology, secondTopology);
    }

    public boolean transfersSecondToFirst() {
        return canTransfer(secondTopology, firstTopology);
    }

    public boolean isBidirectional() {
        return transfersFirstToSecond() && transfersSecondToFirst();
    }

    public boolean isBlocked() {
        return !transfersFirstToSecond() && !transfersSecondToFirst();
    }

    private static boolean canTransfer(FaceTopology source, FaceTopology target) {
        NodeRole sourceRole = source.role();
        NodeRole targetRole = target.role();
        return sourceRole.canSend() && targetRole.canReceive();
    }
}
