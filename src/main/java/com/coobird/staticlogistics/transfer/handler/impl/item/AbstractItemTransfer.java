package com.coobird.staticlogistics.transfer.handler.impl.item;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.model.ContainerConfig;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.TransferContext;
import com.coobird.staticlogistics.util.LogisticsCalculator;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;

import java.util.List;

/**
 * 物品传输基类 —— 提取 push/pull 的公共逻辑：目标验证、能力解析、插入、死链接清理。
 */
public abstract class AbstractItemTransfer {
    protected static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 执行传输
     *
     * @return 是否有物品被移动
     */
    public abstract boolean execute(TransferContext ctx, List<LogisticsNode> targets,
                                    FaceConfigComposite sourceCfg, ContainerConfig localContainer);

    /**
     * 验证远程目标是否可达：维度、范围、区块加载检查。
     *
     * @return 远程 ServerLevel，不可达返回 null
     */
    protected ServerLevel validateTarget(LogisticsNode remoteNode, ServerLevel localLevel,
                                         BlockPos localPos, ContainerConfig localContainer) {
        boolean isSameDim = remoteNode.isInSameDimension(localLevel.dimension());
        boolean canCrossDim = LogisticsCalculator.isDimensionEffective(localContainer);

        if (!isSameDim && !canCrossDim) return null;
        if (isSameDim && !LogisticsCalculator.isWithinRange(localPos, remoteNode.gPos().pos(), localContainer))
            return null;

        ServerLevel remoteLevel = isSameDim ? localLevel
            : localLevel.getServer().getLevel(remoteNode.gPos().dimension());
        if (remoteLevel == null) return null;
        if (!remoteLevel.getChunkSource().hasChunk(
            remoteNode.gPos().pos().getX() >> 4, remoteNode.gPos().pos().getZ() >> 4))
            return null;

        return remoteLevel;
    }

    /**
     * 获取远程 IItemHandler，若方块实体已消失则清理死链接。
     *
     * @return 远程 IItemHandler，不可用返回 null
     */
    protected IItemHandler resolveRemoteCapability(ServerLevel remoteLevel, LogisticsNode remoteNode,
                                                   FaceConfigComposite sourceCfg, TransferContext ctx) {
        IItemHandler cap = remoteLevel.getCapability(
            Capabilities.ItemHandler.BLOCK, remoteNode.gPos().pos(), remoteNode.face());
        if (cap == null) {
            if (isChunkLoadedAndBEGone(remoteLevel, remoteNode.gPos().pos())) {
                removeStaleTarget(sourceCfg, remoteNode, ctx);
            }
            return null;
        }
        return cap;
    }

    /**
     * 将物品插入目标容器，返回实际插入数量。
     */
    protected int insertInto(IItemHandler to, ItemStack stack) {
        ItemStack remain = stack.copy();
        for (int i = 0; i < to.getSlots(); i++) {
            remain = to.insertItem(i, remain, false);
            if (remain.isEmpty()) break;
        }
        return stack.getCount() - remain.getCount();
    }

    protected static boolean isChunkLoadedAndBEGone(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
            && level.getBlockEntity(pos) == null;
    }

    protected static void removeStaleTarget(FaceConfigComposite sourceCfg, LogisticsNode remoteNode,
                                            TransferContext ctx) {
        sourceCfg.getLinkedNodes().remove(remoteNode);
        LinkManager mgr = ctx.linkManager();
        FaceConfigComposite targetCfg = mgr.getFaceConfig(remoteNode.toKey());
        if (targetCfg != null) {
            targetCfg.getLinkedNodes().remove(ctx.sourceNode());
            targetCfg.markDirty();
        }
        if (sourceCfg.getLinkedNodes().isEmpty()) {
            sourceCfg.setGlobalOutputEnabled(false);
            sourceCfg.setGlobalInputEnabled(false);
        }
        sourceCfg.markDirty();
        LOGGER.debug("Auto-cleaned stale link: {} -> {}", ctx.sourceNode().gPos(), remoteNode.gPos());
    }
}
