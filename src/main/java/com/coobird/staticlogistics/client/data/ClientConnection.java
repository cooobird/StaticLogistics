package com.coobird.staticlogistics.client.data;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.node.EndpointTopology;

import java.util.Comparator;

/// 客户端用于展示的一条唯一连接。
/// 服务端拓扑会从两个端点分别投影出边，这里只做无损去重。端点顺序仅用于稳定显示，
/// 传输方向始终由两端实时角色推导，绝不写回或替代服务端连接关系。
public record ClientConnection(
    GroupKey groupKey,
    LogisticsNode first,
    LogisticsNode second,
    EndpointTopology firstTopology,
    EndpointTopology secondTopology,
    String displayName
) {
    public static final Comparator<ClientConnection> DISPLAY_ORDER = Comparator
        .comparing(ClientConnection::first, ConnectionKey.NODE_ORDER)
        .thenComparing(ClientConnection::second, ConnectionKey.NODE_ORDER);

    public ClientConnection {
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

    private static boolean canTransfer(EndpointTopology source, EndpointTopology target) {
        NodeRole sourceRole = source.role();
        NodeRole targetRole = target.role();
        return sourceRole.canSend() && targetRole.canReceive();
    }
}
