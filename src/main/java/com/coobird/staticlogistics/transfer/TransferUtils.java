package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.api.CapGetter;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.transfer.LogisticsResource;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import com.coobird.staticlogistics.logistics.node.LinkConfig;
import com.coobird.staticlogistics.logistics.node.FaceAddress;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 传输工具类 —— 提供传输协议接口和 capability 查询工具。
 *
 * <p>职责分离：
 * <ul>
 *   <li>{@link CapabilityCache} — capability 缓存</li>
 *   <li>{@link TransferPipeline} — 传输管线编排</li>
 *   <li>本类 — 传输协议接口、capability 查询工具</li>
 * </ul>
 */
public class TransferUtils {
    private static final Logger LOGGER = LogUtils.getLogger();

    // ── 向后兼容：委托给 TransferPipeline ──

    public static <C, T> boolean doTransferNodes(
        ServerLevel localLevel, BlockPos localPos, Direction localFace,
        List<LogisticsNode> destinations, BlockCapability<C, Direction> cap,
        long limit, TransferProtocol<C, T> protocol, boolean isPullMode,
        TransferContext context
    ) {
        return TransferPipeline.execute(localLevel, localPos, localFace, destinations, cap, limit, protocol, isPullMode, context);
    }

    public static <C, T> boolean doTransferNodes(
        ServerLevel localLevel, BlockPos localPos, Direction localFace,
        List<LogisticsNode> destinations, CapGetter<C> capGetter,
        long limit, TransferProtocol<C, T> protocol, boolean isPullMode,
        TransferContext context
    ) {
        return TransferPipeline.execute(localLevel, localPos, localFace, destinations, capGetter, limit, protocol, isPullMode, context);
    }

    // ── capability 查询工具 ──

    /**
     * 检查指定位置是否支持任何物流 capability。
     */
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
     * 单个第三方适配器探测失败时只跳过该类型，避免影响其他类型和调用方界面。
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
     * 返回当前输入节点通过有效链接实际允许接收的资源类型。
     * 结果同时受远端输出类型、双方分组与频道关系、当前面的实际能力约束。
     */
    public static List<LogisticsResource<?>> getEffectiveReceiveTypes(
        ServerLevel targetLevel, LogisticsNode targetNode, FaceConfigComposite targetConfig
    ) {
        if (!targetConfig.isGlobalInputEnabled()) return List.of();

        Set<ResourceLocation> linkedTypeIds = new HashSet<>();
        for (var scope : targetConfig.getLinkedNodesByGroup().entrySet()) {
            if (!targetConfig.faceConfig.getGroupKeys().contains(scope.getKey())) continue;
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

    /** 统一判断一条分组内链接当前是否可参与传输。 */
    public static boolean isTransferLinkActive(
        LogisticsNode sourceNode, FaceConfigComposite sourceConfig,
        LogisticsNode targetNode, FaceConfigComposite targetConfig, GroupKey groupKey
    ) {
        if (!sourceConfig.isGlobalOutputEnabled() || !targetConfig.isGlobalInputEnabled()) return false;
        if (!sourceConfig.faceConfig.getGroupKeys().contains(groupKey)
            || !targetConfig.faceConfig.getGroupKeys().contains(groupKey)) return false;
        if (!sourceConfig.getLinkedNodes(groupKey).contains(targetNode)
            || !targetConfig.getLinkedNodes(groupKey).contains(sourceNode)) return false;

        int sourceChannel = sourceConfig.linkConfig.getOutputChannel();
        int targetChannel = targetConfig.linkConfig.getInputChannel();
        return LinkConfig.channelsMatch(sourceChannel, targetChannel);
    }

    // ── 传输协议接口 ──

    public interface TransferProtocol<C, T> {
        ExtractionResult<T> simulateExtract(C source, long max);

        long executeInsert(C dest, T stack);

        default long simulateInsert(C dest, T stack) {
            return 0L;
        }

        default ExtractionResult<T> executeExtract(C source, ExtractionResult<T> simulated, long requested) {
            throw new UnsupportedOperationException("Transfer protocol must report the actual extracted resource");
        }

        default long amountOf(T value) {
            throw new UnsupportedOperationException("Transfer protocol must report resource amounts");
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
    }

}
