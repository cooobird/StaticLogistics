package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.logistics.NodeConfiguratorTool;
import com.coobird.staticlogistics.transfer.TransferUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/** 统一校验来自客户端的节点交互请求。 */
public final class NodeInteractionValidator {
    private NodeInteractionValidator() {
    }

    public static boolean holdsConfigurator(ServerPlayer player) {
        return player.getMainHandItem().getItem() instanceof NodeConfiguratorTool
            || player.getOffhandItem().getItem() instanceof NodeConfiguratorTool;
    }

    public static boolean isPhysicalTargetValid(ServerPlayer player, BlockPos pos, Direction face) {
        if (player == null || pos == null || face == null || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
            && NodeInteractionRules.isWithinReach(player.getX(), player.getY(), player.getZ(), pos)
            && level.getBlockEntity(pos) != null
            && TransferUtils.hasLogisticsCapability(level, pos, face);
    }

    /** 直接方块交互必须由服务端视线精确命中请求的方块面。 */
    public static boolean isDirectInteractionTargetValid(ServerPlayer player, BlockPos pos, Direction face) {
        if (!isPhysicalTargetValid(player, pos, face)) return false;
        HitResult hit = player.pick(Math.sqrt(NodeInteractionRules.MAX_REACH_SQUARED), 0.0F, false);
        return hit instanceof BlockHitResult blockHit
            && blockHit.getType() == HitResult.Type.BLOCK
            && blockHit.getBlockPos().equals(pos)
            && blockHit.getDirection() == face;
    }

    public static boolean canUseExisting(ServerPlayer player, BlockPos pos, Direction face,
                                         @Nullable FaceConfigComposite config) {
        return config != null && isPhysicalTargetValid(player, pos, face)
            && config.canPlayerModify(player);
    }

}
