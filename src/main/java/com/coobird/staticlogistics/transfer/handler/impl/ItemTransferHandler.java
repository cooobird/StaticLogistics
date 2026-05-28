package com.coobird.staticlogistics.transfer.handler.impl;

import com.coobird.staticlogistics.api.ITransferHandler;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.config.manager.ConfigFilterManager;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.ContainerConfig;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.context.TransferContext;
import com.coobird.staticlogistics.transfer.strategy.extract.ItemExtractionStrategy;
import com.coobird.staticlogistics.util.LogisticsCalculator;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 物品传输处理器 —— 包含推送/拉取两种模式的完整逻辑。
 */
public class ItemTransferHandler implements ITransferHandler {
    public static final ItemTransferHandler INSTANCE = new ItemTransferHandler();

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<Boolean> isInTransfer = ThreadLocal.withInitial(() -> false);

    @Override
    public boolean performTransfer(TransferContext context, List<LogisticsNode> targets) {
        if (isInTransfer.get()) {
            LOGGER.debug("Skipped reentrant item transfer for {}", context.sourceNode());
            return false;
        }
        TransferContext newContext = null;
        try {
            isInTransfer.set(true);
            newContext = context.withIncrementedDepth();
            final TransferContext ctx = newContext;

            ServerLevel localLevel = ctx.level();
            BlockPos localPos = ctx.sourceNode().gPos().pos();
            Direction localFace = ctx.sourceNode().face();
            LinkManager localMgr = ctx.linkManager();
            FaceConfigComposite sourceCfg = localMgr.getFaceConfig(ctx.sourceNode().toKey());
            ContainerConfig localContainer = localMgr.getContainerConfig(localPos);
            if (localContainer == null && sourceCfg != null) {
                localContainer = sourceCfg.sharedContainerConfig;
            }
            if (localContainer == null) return false;

            IItemHandler localCap = localLevel.getCapability(Capabilities.ItemHandler.BLOCK, localPos, localFace);
            if (localCap == null) return false;
            if (sourceCfg == null) return false;

            return ctx.isPullMode()
                ? transferPull(ctx, targets, sourceCfg, localContainer)
                : transferPush(ctx, targets, sourceCfg, localContainer);
        } finally {
            if (newContext != null) newContext.recycle();
            isInTransfer.set(false);
        }
    }

    // ===== 推送模式 =====

    private boolean transferPush(TransferContext ctx, List<LogisticsNode> targets,
                                 FaceConfigComposite sourceCfg, ContainerConfig localContainer) {
        ServerLevel localLevel = ctx.level();
        BlockPos localPos = ctx.sourceNode().gPos().pos();
        Direction localFace = ctx.sourceNode().face();
        int limit = ctx.limit();
        boolean canCrossDim = LogisticsCalculator.isDimensionEffective(localContainer);
        boolean movedAny = false;

        IItemHandler from = localLevel.getCapability(Capabilities.ItemHandler.BLOCK, localPos, localFace);
        if (from == null) return false;
        int slots = from.getSlots();

        int[] priorities = new int[slots];
        int[] slotOrder = new int[slots];
        int passCount = 0;
        for (int s = 0; s < slots; s++) {
            ItemStack sim = from.extractItem(s, limit, true);
            if (sim.isEmpty()) continue;
            if (!isItemOutputAllowed(sourceCfg, sim)) continue;
            priorities[s] = sourceCfg.linkConfig.getPriority();
            slotOrder[passCount++] = s;
        }
        if (passCount == 0) return false;

        // 按 priority 降序排
        for (int i = 1; i < passCount; i++) {
            int key = slotOrder[i];
            int keyPrio = priorities[key];
            int j = i - 1;
            while (j >= 0 && priorities[slotOrder[j]] < keyPrio) {
                slotOrder[j + 1] = slotOrder[j];
                j--;
            }
            slotOrder[j + 1] = key;
        }

        ItemExtractionStrategy extractionStrategy = ItemExtractionStrategy.forMode(
            sourceCfg.linkConfig.getExtractionMode());
        int startIdx = extractionStrategy.beginTick(passCount, ctx);
        int lastProcessedIdx = startIdx;

        for (LogisticsNode remoteNode : targets) {
            if (limit <= 0) break;

            boolean isSameDim = remoteNode.isInSameDimension(localLevel.dimension());
            if (!isSameDim && !canCrossDim) continue;
            if (isSameDim && !LogisticsCalculator.isWithinRange(localPos, remoteNode.gPos().pos(), localContainer))
                continue;

            ServerLevel remoteLevel = isSameDim ? localLevel
                : localLevel.getServer().getLevel(remoteNode.gPos().dimension());
            if (remoteLevel == null || !remoteLevel.getChunkSource()
                .hasChunk(remoteNode.gPos().pos().getX() >> 4, remoteNode.gPos().pos().getZ() >> 4))
                continue;

            IItemHandler to = remoteLevel.getCapability(
                Capabilities.ItemHandler.BLOCK, remoteNode.gPos().pos(), remoteNode.face());
            if (to == null) {
                if (isChunkLoadedAndBEGone(remoteLevel, remoteNode.gPos().pos())) {
                    removeStaleTarget(sourceCfg, remoteNode, ctx);
                }
                continue;
            }

            FaceConfigComposite targetCfg = LinkManager.get(remoteLevel).getFaceConfig(remoteNode.toKey());

            for (int count = 0; count < passCount && limit > 0; count++) {
                int idx = (startIdx + count) % passCount;
                int s = slotOrder[idx];
                ItemStack sim = from.extractItem(s, limit, true);
                if (sim.isEmpty()) continue;
                if (targetCfg != null && !ConfigFilterManager.isItemInputAllowed(sim, targetCfg))
                    continue;

                // 存量维持：如果目标配置了 keepStock，检查目标已有数量
                int stockLimit = targetCfg != null ? targetCfg.linkConfig.getKeepStock() : 0;
                if (stockLimit > 0) {
                    int alreadyHas = 0;
                    for (int i = 0; i < to.getSlots(); i++) {
                        ItemStack targetStack = to.getStackInSlot(i);
                        if (ItemStack.isSameItemSameComponents(sim, targetStack)) {
                            alreadyHas += targetStack.getCount();
                        }
                    }
                    int needed = stockLimit - alreadyHas;
                    if (needed <= 0) continue; // 已达存量，跳过
                    sim = sim.copyWithCount(Math.min(sim.getCount(), needed));
                }

                ItemStack remain = sim.copy();
                for (int i = 0; i < to.getSlots(); i++) {
                    remain = to.insertItem(i, remain, false);
                    if (remain.isEmpty()) break;
                }
                int accepted = sim.getCount() - remain.getCount();
                if (accepted <= 0) continue;

                from.extractItem(s, accepted, false);
                limit -= accepted;
                movedAny = true;
                lastProcessedIdx = idx;
            }
        }

        extractionStrategy.endTick(lastProcessedIdx, passCount, ctx, movedAny);
        return movedAny;
    }

    // ===== 拉取模式 =====

    private boolean transferPull(TransferContext ctx, List<LogisticsNode> targets,
                                 FaceConfigComposite sourceCfg, ContainerConfig localContainer) {
        ServerLevel localLevel = ctx.level();
        BlockPos localPos = ctx.sourceNode().gPos().pos();
        Direction localFace = ctx.sourceNode().face();
        int limit = ctx.limit();
        boolean canCrossDim = LogisticsCalculator.isDimensionEffective(localContainer);
        boolean movedAny = false;

        IItemHandler to = localLevel.getCapability(Capabilities.ItemHandler.BLOCK, localPos, localFace);
        if (to == null) return false;

        for (LogisticsNode remoteNode : targets) {
            if (limit <= 0) break;
            boolean isSameDim = remoteNode.isInSameDimension(localLevel.dimension());
            if (!isSameDim && !canCrossDim) continue;
            if (isSameDim && !LogisticsCalculator.isWithinRange(localPos, remoteNode.gPos().pos(), localContainer))
                continue;

            ServerLevel remoteLevel = isSameDim ? localLevel
                : localLevel.getServer().getLevel(remoteNode.gPos().dimension());
            if (remoteLevel == null || !remoteLevel.getChunkSource()
                .hasChunk(remoteNode.gPos().pos().getX() >> 4, remoteNode.gPos().pos().getZ() >> 4))
                continue;

            IItemHandler from = remoteLevel.getCapability(
                Capabilities.ItemHandler.BLOCK, remoteNode.gPos().pos(), remoteNode.face());
            if (from == null) {
                if (isChunkLoadedAndBEGone(remoteLevel, remoteNode.gPos().pos())) {
                    removeStaleTarget(sourceCfg, remoteNode, ctx);
                }
                continue;
            }
            if (from == null) continue;

            List<SlotItem> available = new ArrayList<>();
            int slots = from.getSlots();
            for (int s = 0; s < slots; s++) {
                ItemStack sim = from.extractItem(s, limit, true);
                if (sim.isEmpty()) continue;
                if (!ConfigFilterManager.isItemInputAllowed(sim, sourceCfg)) continue;
                if (!ConfigFilterManager.isItemOutputAllowed(sim, sourceCfg)) continue;
                available.add(new SlotItem(sim, s));
            }
            if (available.isEmpty()) continue;

            available.sort(Comparator.comparingInt(a -> ctx.sourceConfig().linkConfig.getPriority()));

            for (SlotItem si : available) {
                if (limit <= 0) break;
                ItemStack sim = from.extractItem(si.slot, limit, true);
                if (sim.isEmpty()) continue;
                ItemStack remain = sim.copy();
                int accepted = 0;
                for (int i = 0; i < to.getSlots(); i++) {
                    remain = to.insertItem(i, remain, false);
                    accepted = sim.getCount() - remain.getCount();
                    if (remain.isEmpty()) break;
                }
                if (accepted <= 0) continue;
                from.extractItem(si.slot, accepted, false);
                limit -= accepted;
                movedAny = true;
            }
        }
        return movedAny;
    }

    private static boolean isItemOutputAllowed(FaceConfigComposite config, ItemStack stack) {
        if (config == null) return true;
        return ConfigFilterManager.isItemOutputAllowed(stack, config);
    }

    private record SlotItem(ItemStack stack, int slot) {
    }

    // ==== 虚空链接自动清理 ====

    private static boolean isChunkLoadedAndBEGone(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
            && level.getBlockEntity(pos) == null;
    }

    private static void removeStaleTarget(FaceConfigComposite sourceCfg, LogisticsNode remoteNode,
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
