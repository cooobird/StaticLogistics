package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.api.CapGetter;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.node.FaceAddress;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.*;

/**
 * 传输工具类 —— 提供传输协议接口和 capability 查询工具。
 */
public class TransferUtils {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static <C, T> boolean doTransferNodes(
        ServerLevel localLevel, BlockPos localPos, Direction localFace,
        List<LogisticsNode> destinations, CapGetter<C> capGetter,
        long limit, TransferProtocol<C, T> protocol, boolean isPullMode,
        TransferContext context
    ) {
        return TransferPipeline.execute(localLevel, localPos, localFace, destinations, capGetter, limit, protocol, isPullMode, context);
    }

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

    /**
     * 返回指定面实际暴露的全部物流资源类型。
     */
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

    /**
     * 按资源能力覆盖度返回方块的可用物流面。
     *
     * <p>玩家命中的面在覆盖度相同时优先，随后保持原版方向顺序。未选择传输类型时，
     * 使用全部已注册类型寻找一个可建链面；已经选择类型时，只统计这些类型，避免批量选点
     * 落到与当前传输目标无关的能力面上。
     */
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

    /**
     * 返回当前输入节点通过有效链接实际允许接收的资源类型。
     */
    public static List<LogisticsResource<?>> getEffectiveReceiveTypes(
        ServerLevel targetLevel, LogisticsNode targetNode, FaceConfigComposite targetConfig
    ) {
        if (!targetConfig.isGlobalInputEnabled()) return List.of();

        Set<ResourceLocation> linkedTypeIds = new HashSet<>();
        for (var scope : targetConfig.getLinkedNodesByGroup().entrySet()) {
            if (!targetConfig.faceConfig.containsGroup(scope.getKey())) continue;
            for (LogisticsNode sourceNode : scope.getValue()) {
                ServerLevel sourceLevel = targetLevel.getServer().getLevel(sourceNode.gPos().dimension());
                if (sourceLevel == null) continue;
                FaceConfigComposite sourceConfig = LinkManager.get(sourceLevel)
                    .getFaceConfig(FaceAddress.of(sourceNode));
                if (sourceConfig == null || !isTransferLinkActive(
                    sourceNode, sourceConfig, targetNode, targetConfig, scope.getKey())) continue;
                Set<ResourceLocation> selectedTypeIds = Set.copyOf(sourceConfig.getSelectedTypeIds());
                for (LogisticsResource<?> type : getPresentTypes(
                    sourceLevel, sourceNode.gPos().pos(), sourceNode.face())) {
                    if (selectedTypeIds.contains(type.typeId())) linkedTypeIds.add(type.typeId());
                }
            }
        }

        return getPresentTypes(targetLevel, targetNode.gPos().pos(), targetNode.face()).stream()
            .filter(type -> linkedTypeIds.contains(type.typeId()))
            .toList();
    }

    /**
     * 统一判断一条分组内链接当前是否可参与传输。
     */
    public static boolean isTransferLinkActive(
        LogisticsNode sourceNode, FaceConfigComposite sourceConfig,
        LogisticsNode targetNode, FaceConfigComposite targetConfig, GroupKey groupKey
    ) {
        if (!sourceConfig.isGlobalOutputEnabled() || !targetConfig.isGlobalInputEnabled()) return false;
        if (!sourceConfig.faceConfig.containsGroup(groupKey)
            || !targetConfig.faceConfig.containsGroup(groupKey)) return false;
        if (!sourceConfig.getLinkedNodes(groupKey).contains(targetNode)
            || !targetConfig.getLinkedNodes(groupKey).contains(sourceNode)) return false;

        return true;
    }

    public interface TransferProtocol<C, T> {
        ExtractionResult<T> simulateExtract(C source, long max);

        long executeInsert(C dest, T stack);

        default long simulateInsert(C dest, T stack) {
            return 0L;
        }

        default ExtractionResult<T> executeExtract(
            C source, ExtractionResult<T> simulated, long requested) {
            throw new UnsupportedOperationException(
                "Transfer protocol must report the actual extracted resource");
        }

        default long amountOf(T value) {
            throw new UnsupportedOperationException(
                "Transfer protocol must report resource amounts");
        }

        default T withAmount(T value, long amount) {
            return null;
        }

        default boolean rollbackRemainder(C source, ExtractionResult<T> extracted, long accepted) {
            return false;
        }

        boolean isEmpty(ExtractionResult<T> result);

        default boolean canInsert(C dest, T stack, LogisticsNode targetNode) {
            return true;
        }

        /**
         * 单次激活正常结束后，清理仅应在本轮内保留的扫描状态。
         */
        default void onActivationCompleted() {
        }

        default boolean advanceRejectedCandidate(ExtractionResult<T> simulated) {
            return false;
        }
    }

    private record CapabilityFace(Direction face, int supportedTypes) {
    }

}
