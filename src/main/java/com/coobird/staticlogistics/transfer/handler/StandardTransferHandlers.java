package com.coobird.staticlogistics.transfer.handler;

import com.coobird.staticlogistics.api.ITransferHandler;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.config.manager.ConfigFilterManager;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.ContainerConfig;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.context.TransferContext;
import com.coobird.staticlogistics.util.LogisticsCalculator;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class StandardTransferHandlers {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ThreadLocal<Boolean> isInItemTransfer = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> isInFluidTransfer = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> isInEnergyTransfer = ThreadLocal.withInitial(() -> false);

    public static final ITransferHandler ITEM = (context, targets) -> {
        if (isInItemTransfer.get()) {
            LOGGER.debug("Skipped reentrant item transfer for {}", context.sourceNode());
            return false;
        }
        TransferContext newContext = null;
        try {
            isInItemTransfer.set(true);
            newContext = context.withIncrementedDepth();
            final TransferContext ctx = newContext;

            ServerLevel localLevel = ctx.level();
            BlockPos localPos = ctx.sourceNode().gPos().pos();
            Direction localFace = ctx.sourceNode().face();
            LinkManager localMgr = ctx.linkManager();
            FaceConfigComposite sourceCfg = localMgr.getFaceConfig(ctx.sourceNode().toKey());
            ContainerConfig localContainer = localMgr.getContainerConfig(localPos);
            if (localContainer == null) return false;

            IItemHandler localCap = ctx.linkManager().getCapabilityCache()
                .getOrCreateCache(localLevel, localPos, localFace, Capabilities.ItemHandler.BLOCK).getCapability();
            if (localCap == null) {
                localCap = localLevel.getCapability(Capabilities.ItemHandler.BLOCK, localPos, localFace);
                if (localCap == null) return false;
            }

            int limit = ctx.limit();
            boolean canCrossDim = LogisticsCalculator.isDimensionEffective(localContainer);
            boolean isPullMode = ctx.isPullMode();
            boolean movedAny = false;

            for (LogisticsNode remoteNode : targets) {
                if (limit <= 0) break;
                boolean isSameDim = remoteNode.isInSameDimension(localLevel.dimension());
                if (!isSameDim && !canCrossDim) continue;
                if (isSameDim && !LogisticsCalculator.isWithinRange(localPos, remoteNode.gPos().pos(), localContainer))
                    continue;

                ServerLevel remoteLevel = isSameDim ? localLevel : localLevel.getServer().getLevel(remoteNode.gPos().dimension());
                if (remoteLevel == null || !remoteLevel.getChunkSource().hasChunk(remoteNode.gPos().pos().getX() >> 4, remoteNode.gPos().pos().getZ() >> 4))
                    continue;

                IItemHandler remoteCap = ctx.linkManager().getCapabilityCache().getOrCreateCache(remoteLevel, remoteNode.gPos().pos(), remoteNode.face(), Capabilities.ItemHandler.BLOCK).getCapability();
                if (remoteCap == null) remoteCap = remoteLevel.getCapability(Capabilities.ItemHandler.BLOCK, remoteNode.gPos().pos(), remoteNode.face());
                if (remoteCap == null) continue;

                IItemHandler from = isPullMode ? remoteCap : localCap;
                IItemHandler to = isPullMode ? localCap : remoteCap;
                FaceConfigComposite targetCfg = isPullMode ? sourceCfg : LinkManager.get(remoteLevel).getFaceConfig(remoteNode.toKey());

                // 收集所有可用物品
                List<SlotItem> available = new ArrayList<>();
                int slots = from.getSlots();
                for (int s = 0; s < slots; s++) {
                    ItemStack sim = from.extractItem(s, limit, true);
                    if (sim.isEmpty()) continue;
                    if (!isItemAllowed(sourceCfg, sim, isPullMode)) continue;
                    if (!isPullMode && targetCfg != null && !ConfigFilterManager.isItemInputAllowed(sim, targetCfg))
                        continue;
                    available.add(new SlotItem(sim, s));
                }
                if (available.isEmpty()) continue;

                // 应用分发策略
                DistributionStrategy strategy = sourceCfg.linkConfig.getStrategy();
                // 全局 priority 排序
                available.sort((a, b) -> Integer.compare(getPriority(ctx, b.stack), getPriority(ctx, a.stack)));
                // 组内应用策略
                if (strategy == DistributionStrategy.RANDOM) Collections.shuffle(available);
                else if (strategy == DistributionStrategy.NEAREST)
                    available.sort(Comparator.comparingDouble(a -> distSq(localPos, remoteNode)));

                for (SlotItem si : available) {
                    if (limit <= 0) break;
                    ItemStack sim = from.extractItem(si.slot, limit, true);
                    if (sim.isEmpty()) continue;
                    int accepted = 0;
                    ItemStack remain = sim.copy();
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
        } finally {
            if (newContext != null) newContext.recycle();
            isInItemTransfer.set(false);
        }
    };

    private static int getPriority(TransferContext ctx, ItemStack stack) {
        return ctx.sourceConfig().linkConfig.getPriority();
    }

    private static double distSq(BlockPos a, LogisticsNode b) {
        return a.distSqr(b.gPos().pos());
    }

    private record SlotItem(ItemStack stack, int slot) {
    }

    private static boolean isItemAllowed(FaceConfigComposite config, ItemStack stack, boolean isPullMode) {
        if (config == null) return true;
        return isPullMode
            ? ConfigFilterManager.isItemInputAllowed(stack, config)
            : ConfigFilterManager.isItemOutputAllowed(stack, config);
    }

    private static boolean isFluidAllowed(FaceConfigComposite config, FluidStack stack, boolean isPullMode) {
        if (config == null) return true;
        return isPullMode
            ? ConfigFilterManager.isFluidInputAllowed(stack, config)
            : ConfigFilterManager.isFluidOutputAllowed(stack, config);
    }

    public static final ITransferHandler FLUID = (context, targets) -> {
        if (isInFluidTransfer.get()) {
            LOGGER.debug("Skipped reentrant fluid transfer for {}", context.sourceNode());
            return false;
        }

        TransferContext newContext = null;
        try {
            isInFluidTransfer.set(true);
            newContext = context.withIncrementedDepth();
            final TransferContext ctx = newContext;
            FaceConfigComposite sourceCfg = ctx.linkManager().getFaceConfig(ctx.sourceNode().toKey());

            return TransferUtils.doTransferNodes(
                ctx.level(),
                ctx.sourceNode().gPos().pos(),
                ctx.sourceNode().face(),
                targets,
                Capabilities.FluidHandler.BLOCK,
                ctx.limit(),
                new TransferUtils.SimpleProtocol<>(
                    (handler, max) -> {
                        FluidStack drained = handler.drain(max, IFluidHandler.FluidAction.SIMULATE);
                        if (!drained.isEmpty() && !isFluidAllowed(sourceCfg, drained, ctx.isPullMode())) {
                            return FluidStack.EMPTY;
                        }
                        return drained;
                    },
                    (handler, stack) -> {
                        if (!isFluidAllowed(sourceCfg, stack, ctx.isPullMode())) return 0;
                        try {
                            return handler.fill(stack, IFluidHandler.FluidAction.EXECUTE);
                        } catch (Exception e) {
                            LOGGER.error("Failed to insert fluid: {}", e.getMessage());
                            return 0;
                        }
                    },
                    (handler, stack, act) -> handler.drain(act, IFluidHandler.FluidAction.EXECUTE),
                    FluidStack::isEmpty,
                    (stack, targetNode) -> {
                        if (ctx.isPullMode()) return true;
                        ServerLevel targetLevel = ctx.level().getServer().getLevel(targetNode.gPos().dimension());
                        if (targetLevel == null) return true;
                        FaceConfigComposite targetCfg = LinkManager.get(targetLevel).getFaceConfig(targetNode.toKey());
                        return targetCfg == null || ConfigFilterManager.isFluidInputAllowed(stack, targetCfg);
                    }
                ),
                ctx.isPullMode(),
                ctx,
                ctx.linkManager().getCapabilityCache()
            );
        } finally {
            if (newContext != null) newContext.recycle();
            isInFluidTransfer.set(false);
        }
    };

    public static final ITransferHandler ENERGY = (context, targets) -> {
        if (isInEnergyTransfer.get()) {
            LOGGER.debug("Skipped reentrant energy transfer for {}", context.sourceNode());
            return false;
        }

        TransferContext newContext = null;
        try {
            isInEnergyTransfer.set(true);
            newContext = context.withIncrementedDepth();
            final TransferContext ctx = newContext;

            return TransferUtils.doTransferNodes(
                ctx.level(),
                ctx.sourceNode().gPos().pos(),
                ctx.sourceNode().face(),
                targets,
                Capabilities.EnergyStorage.BLOCK,
                ctx.limit(),
                new TransferUtils.SimpleProtocol<>(
                    (handler, max) -> handler.extractEnergy(max, true),
                    (handler, val) -> {
                        try {
                            return handler.receiveEnergy(val, false);
                        } catch (Exception e) {
                            LOGGER.error("Failed to receive energy: {}", e.getMessage());
                            return 0;
                        }
                    },
                    (handler, val, act) -> handler.extractEnergy(act, false),
                    val -> val <= 0
                ),
                ctx.isPullMode(),
                ctx,
                ctx.linkManager().getCapabilityCache()
            );
        } finally {
            if (newContext != null) newContext.recycle();
            isInEnergyTransfer.set(false);
        }
    };
}