package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.node.EndpointAddress;
import com.coobird.staticlogistics.logistics.node.EndpointConfig;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.*;

/// 提供实时链接校验和能力查询。
public final class TransferUtils {
    private TransferUtils() {
    }
    private static final Logger LOGGER = LogUtils.getLogger();

    /// 检查指定位置是否支持任何物流 capability。
    public static boolean hasLogisticsCapability(Level level, BlockPos pos, Direction face) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        for (var type : TransferRegistries.getAllActive()) {
            try {
                if (type.isPresent(serverLevel, pos, face)) return true;
            } catch (Exception e) {
                LOGGER.error("Capability check failed at {} face {} type {}: {}", pos, face, type.typeId(), e.getMessage());
            }
        }
        return false;
    }

    /// 返回指定面实际暴露的全部物流资源类型。
    /// 单个第三方适配器探测失败时只跳过该类型，避免影响其他类型和调用方界面。
    public static List<LogisticsResource<?>> getPresentTypes(ServerLevel level, BlockPos pos, Direction face) {
        List<LogisticsResource<?>> result = new ArrayList<>();
        for (LogisticsResource<?> type : TransferRegistries.getAllActive()) {
            try {
                if (type.isPresent(level, pos, face)) result.add(type);
            } catch (Exception e) {
                LOGGER.error("Capability check failed at {} face {} type {}", pos, face, type.typeId(), e);
            }
        }
        return List.copyOf(result);
    }

    /// 按资源能力覆盖度返回方块的可用物流面。
    /// 玩家命中的面在覆盖度相同时优先，随后保持原版方向顺序。未选择传输类型时，
    /// 使用全部已注册类型寻找一个可建链面；已经选择类型时，只统计这些类型，避免批量选点
    /// 落到与当前传输目标无关的能力面上。
    /// 指定朝向筛选时只检查该面，避免对区域内每个方块扫描六个面。
    public static List<Direction> getCapabilityFaces(
        ServerLevel level,
        BlockPos pos,
        Direction preferredFace,
        Collection<ResourceLocation> selectedTypeIds
    ) {
        Set<ResourceLocation> selected = new LinkedHashSet<>(selectedTypeIds);
        List<LogisticsResource<?>> types = selected.isEmpty()
            ? List.copyOf(TransferRegistries.getAllActive())
            : TransferRegistries.getAllActive().stream()
            .filter(type -> selected.contains(type.typeId()))
            .toList();
        if (types.isEmpty()) return List.of();

        List<Direction> directions = new ArrayList<>(Direction.values().length);
        directions.add(preferredFace);
        for (Direction direction : Direction.values()) {
            if (direction != preferredFace) directions.add(direction);
        }

        List<CapabilityFace> available = new ArrayList<>();
        for (Direction direction : directions) {
            int supportedTypes = 0;
            for (LogisticsResource<?> type : types) {
                try {
                    if (type.isPresent(level, pos, direction)) supportedTypes++;
                } catch (Exception exception) {
                    LOGGER.error("Capability check failed at {} face {} type {}",
                        pos, direction, type.typeId(), exception);
                }
            }
            if (supportedTypes > 0) available.add(new CapabilityFace(direction, supportedTypes));
        }
        available.sort(Comparator.comparingInt(CapabilityFace::supportedTypes).reversed());
        return available.stream().map(CapabilityFace::face).toList();
    }

    /// 返回当前输入节点通过有效链接实际允许接收的资源类型。
    /// 结果同时受远端输出类型、双方分组关系与当前面的实际能力约束。
    public static List<LogisticsResource<?>> getEffectiveReceiveTypes(
        ServerLevel targetLevel, LogisticsNode targetNode, EndpointConfig targetConfig
    ) {
        if (!targetConfig.isGlobalInputEnabled()) return List.of();

        /// 只探测目标实际支持的类型；一种类型找到有效来源后，不再查询其他来源。
        List<LogisticsResource<?>> targetTypes = getPresentTypes(targetLevel, targetNode.gPos().pos(), targetNode.face());
        if (targetTypes.isEmpty()) return List.of();
        Set<ResourceLocation> linkedTypeIds = new HashSet<>();
        Set<LogisticsNode> inspectedSources = new HashSet<>();
        for (var scope : targetConfig.getLinkedNodesByGroup().entrySet()) {
            if (!targetConfig.faceConfig.containsGroup(scope.getKey())) continue;
            for (LogisticsNode sourceNode : scope.getValue()) {
                if (inspectedSources.contains(sourceNode)) continue;
                ServerLevel sourceLevel = targetLevel.getServer().getLevel(sourceNode.gPos().dimension());
                if (sourceLevel == null) continue;
                EndpointConfig sourceConfig = LinkManager.get(sourceLevel)
                    .getEndpointConfig(EndpointAddress.of(sourceNode));
                if (sourceConfig == null || !isTransferLinkConfigured(
                    sourceNode, sourceConfig, targetNode, targetConfig, scope.getKey())) continue;
                /// 无效分组不能阻止同一来源通过另一个有效分组被识别。
                inspectedSources.add(sourceNode);
                for (LogisticsResource<?> type : targetTypes) {
                    if (linkedTypeIds.contains(type.typeId()) || !sourceConfig.isTypeSelected(type)) continue;
                    try {
                        if (type.isPresent(sourceLevel, sourceNode.gPos().pos(), sourceNode.face()))
                            linkedTypeIds.add(type.typeId());
                    } catch (Exception exception) {
                        LOGGER.error("Capability check failed at {} face {} type {}",
                            sourceNode.gPos().pos(), sourceNode.face(), type.typeId(), exception);
                    }
                }
                if (linkedTypeIds.size() == targetTypes.size()) return targetTypes;
            }
        }
        return targetTypes.stream().filter(type -> linkedTypeIds.contains(type.typeId())).toList();
    }

    /// 统一判断一条分组内链接当前是否可参与传输。
    private static boolean allowsChannelRedstone(MinecraftServer server, LogisticsNode node, EndpointConfig config) {
        ServerLevel level = server.getLevel(node.gPos().dimension());
        return level != null && config.getContainerConfig() != null && config.getContainerConfig().allowsRedstone(level, config.linkConfig.getRedstoneMode());
    }

    public static boolean isTransferLinkActive(
        MinecraftServer server,
        LogisticsNode sourceNode, EndpointConfig sourceConfig,
        LogisticsNode targetNode, EndpointConfig targetConfig, GroupKey groupKey
    ) {
        return isTransferLinkConfigured(sourceNode, sourceConfig, targetNode, targetConfig, groupKey)
            && sourceConfig.linkConfig.isEnabled() && targetConfig.linkConfig.isEnabled()
            && allowsChannelRedstone(server, sourceNode, sourceConfig)
            && allowsChannelRedstone(server, targetNode, targetConfig);
    }

    /// 执行时重新解析当前端点；规划、前置事件或配置更新都不能替代这一步。
    public static boolean isCurrentTransferAllowed(TransferContext context, LogisticsNode remoteNode) {
        MinecraftServer server = context.level().getServer();
        TransferContext.requireServerThread(context.level());
        EndpointConfig local = context.linkManager().getEndpointConfig(EndpointAddress.of(context.sourceNode()));
        if (local == null || local != context.sourceConfig()) return false;
        ServerLevel remoteLevel = server.getLevel(remoteNode.gPos().dimension());
        if (remoteLevel == null) return false;
        EndpointConfig remote = LinkManager.get(remoteLevel).getEndpointConfig(EndpointAddress.of(remoteNode));
        if (remote == null) return false;
        EndpointConfig source = context.isPullMode() ? remote : local;
        EndpointConfig target = context.isPullMode() ? local : remote;
        LogisticsNode sourceNode = context.isPullMode() ? remoteNode : context.sourceNode();
        LogisticsNode targetNode = context.isPullMode() ? context.sourceNode() : remoteNode;
        return source.isTypeSelected(context.type())
            && source.isGlobalOutputEnabled() && target.isGlobalInputEnabled()
            && source.linkConfig.isEnabled() && target.linkConfig.isEnabled()
            && source.hasReciprocalLink(sourceNode, targetNode, target)
            && allowsChannelRedstone(server, sourceNode, source)
            && allowsChannelRedstone(server, targetNode, target);
    }

    private static boolean isTransferLinkConfigured(LogisticsNode sourceNode, EndpointConfig sourceConfig,
                                                    LogisticsNode targetNode, EndpointConfig targetConfig, GroupKey groupKey) {
        if (!sourceConfig.isGlobalOutputEnabled() || !targetConfig.isGlobalInputEnabled()) return false;
        if (!sourceConfig.faceConfig.containsGroup(groupKey)
            || !targetConfig.faceConfig.containsGroup(groupKey)) return false;
        return sourceConfig.isLinkedTo(groupKey, targetNode)
            && targetConfig.isLinkedTo(groupKey, sourceNode);
    }

    private record CapabilityFace(Direction face, int supportedTypes) {
    }

}
