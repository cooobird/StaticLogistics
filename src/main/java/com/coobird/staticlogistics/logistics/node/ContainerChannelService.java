package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.List;

/// 当前容器通道的创建用例，必须依托现有容器关系，绝不创建远端通道。
public final class ContainerChannelService {
    private ContainerChannelService() {
    }

    public static LogisticsNode create(ServerPlayer player, ConnectionKey key, LogisticsNode current) {
        if (!key.contains(current.gPos())) return null;
        var mutations = new NodeMutationService();
        var source = mutations.resolveRemote(player, current, key.groupKey());
        if (source == null) return null;
        var routes = ContainerConnections.routes(player.server, key);
        if (routes.isEmpty()) return null;
        var peers = routes.stream().filter(route -> {
            var local = route.first().gPos().equals(current.gPos()) ? route.first() : route.second();
            return local.face() == current.face();
        }).map(route -> route.first().gPos().equals(current.gPos()) ? route.second() : route.first()).distinct().toList();
        if (peers.isEmpty()) return null;
        if (peers.stream().anyMatch(node -> mutations.resolveRemote(player, node, key.groupKey()) == null)) return null;
        var group = PlayerGroupStore.get(player.server).findGroup(key.groupKey());
        if (group == null) return null;
        LogisticsNode created = freeChannel(source);
        if (created == null) {
            player.displayClientMessage(Component.translatable("gui.staticlogistics.compact.channel_limit"), false);
            return null;
        }
        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(player.server)) {
            transaction.captureState(created);
            peers.forEach(transaction::capture);
            source.manager().getOrCreateChannel(created);
            source.manager().claimOwner(created, player.getGameProfile());
            source.manager().addNodeToGroup(created, group);
            var config = source.manager().getEndpointConfig(EndpointAddress.of(created));
            config.setSelectedTypeIds(source.config().getSelectedTypeIds());
            config.setGlobalInputEnabled(source.config().isGlobalInputEnabled());
            config.setGlobalOutputEnabled(current.isConnectionFace() || source.config().isGlobalOutputEnabled());
            config.setChannelEnabled(false);
            for (var peer : peers) source.manager().addLink(group.key(), created, peer);
            transaction.commit();
        }
        return created;
    }

    /// 删除通道前保存连接面及其关系，其他通道的身份、参数和过滤器不受影响。
    public static LogisticsNode delete(ServerPlayer player, ConnectionKey key, LogisticsNode channel) {
        if (channel.isConnectionFace() || !key.contains(channel.gPos())) return null;
        var source = new NodeMutationService().resolveRemote(player, channel, key.groupKey());
        if (source == null || ContainerConnections.routes(player.server, key).isEmpty()) return null;
        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(player.server)) {
            transaction.capture(channel);
            for (var group : source.config().faceConfig.getGroups()) {
                for (var peer : List.copyOf(source.config().getLinkedNodes(group.key()))) {
                    var peerLevel = player.server.getLevel(peer.gPos().dimension());
                    if (peerLevel == null) return null;
                    var peerManager = LinkManager.get(peerLevel);
                    var peerConfig = peerManager.getEndpointConfig(EndpointAddress.of(peer));
                    if (peerConfig == null) return null;
                    preserveFace(source.manager(), channel, source.config(), group, transaction);
                    preserveFace(peerManager, peer, peerConfig, group, transaction);
                    transaction.capture(peer);
                    source.manager().addLink(group.key(), channel.connectionFace(), peer.connectionFace());
                    if (!peer.isConnectionFace()) source.manager().addLink(group.key(), channel.connectionFace(), peer);
                }
            }
            try (var receipt = new PlayerUpgradeHandoff(player).begin(List.of(
                new NodeLifecycleService.UpgradeSource(source.pos(), source.config().filterConfig.getUpgrades())))) {
                source.manager().removeFaceConfig(EndpointAddress.of(channel));
                if (source.manager().getEndpointConfig(EndpointAddress.of(channel)) != null) return null;
                transaction.commit();
                receipt.commit();
            }
        }
        return source.manager().getContainerConfig(source.pos()).getLinkedFaceKeys().stream()
            .filter(address -> address.face() == channel.face() && address.channel() >= 0)
            .map(address -> address.toNode(channel.gPos().dimension()))
            .filter(node -> source.manager().getEndpointConfig(EndpointAddress.of(node)).faceConfig.containsGroup(key.groupKey()))
            .min(Comparator.comparingInt(LogisticsNode::channel)).orElse(channel.connectionFace());
    }

    private static void preserveFace(LinkManager manager, LogisticsNode node, EndpointConfig config,
                                     GroupRef group, NodeMutationTransaction transaction) {
        var face = node.connectionFace();
        transaction.captureState(face);
        var persistent = manager.getOrCreateChannel(face);
        if (persistent.faceConfig.getOwner() == null && config.faceConfig.getOwner() != null) {
            manager.claimOwner(face, new GameProfile(config.faceConfig.getOwner(), config.faceConfig.getOwnerName()));
        }
        manager.addNodeToGroup(face, group);
        persistent.setChannelEnabled(false);
        persistent.setGlobalInputEnabled(false);
        persistent.setGlobalOutputEnabled(false);
    }

    public static boolean rename(ServerPlayer player, ConnectionKey key, LogisticsNode channel, String name) {
        if (channel.isConnectionFace() || !key.contains(channel.gPos())) return false;
        var source = new NodeMutationService().resolveRemote(player, channel, key.groupKey());
        if (source == null || ContainerConnections.routes(player.server, key).isEmpty()) return false;
        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(player.server)) {
            transaction.capture(channel);
            source.config().setChannelName(name);
            transaction.commit();
        }
        return true;
    }

    private static LogisticsNode freeChannel(NodeMutationService.ValidatedNode current) {
        int next = current.config().getContainerConfig().getLinkedFaceKeys().stream()
            .filter(address -> address.face() == current.face() && address.channel() >= 0)
            .mapToInt(EndpointAddress::channel).max().orElse(-1) + 1;
        return next < LogisticsNode.MAX_CHANNELS ? new LogisticsNode(current.node().gPos(), current.face(), next) : null;
    }
}
