package com.coobird.staticlogistics.transfer.handler.impl.item;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.filter.FilterEvaluator;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.model.ContainerConfig;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.TransferContext;
import com.coobird.staticlogistics.transfer.TransferLogManager;
import com.coobird.staticlogistics.transfer.strategy.extract.ItemExtractionStrategy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * 物品推送策略 —— 从本地源容器提取物品，插入远程目标容器。
 */
public class ItemPushTransfer extends AbstractItemTransfer {

    @Override
    public boolean execute(TransferContext ctx, List<LogisticsNode> targets,
                           FaceConfigComposite sourceCfg, ContainerConfig localContainer) {
        ServerLevel localLevel = ctx.level();
        BlockPos localPos = ctx.sourceNode().gPos().pos();
        Direction localFace = ctx.sourceNode().face();
        int limit = ctx.limit();
        boolean movedAny = false;

        IItemHandler from = localLevel.getCapability(Capabilities.ItemHandler.BLOCK, localPos, localFace);
        if (from == null) return false;

        // 扫描源槽位，过滤输出，按 priority 排序
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

        // priority 降序排序（插入排序）
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

        // 提取策略
        ItemExtractionStrategy extractionStrategy = ItemExtractionStrategy.forMode(
            sourceCfg.linkConfig.getExtractionMode());
        int startIdx = extractionStrategy.beginTick(passCount, ctx);
        int lastProcessedIdx = startIdx;

        for (LogisticsNode remoteNode : targets) {
            if (limit <= 0) break;

            ServerLevel remoteLevel = validateTarget(remoteNode, localLevel, localPos, localContainer);
            if (remoteLevel == null) continue;

            IItemHandler to = resolveRemoteCapability(remoteLevel, remoteNode, sourceCfg, ctx);
            if (to == null) continue;

            FaceConfigComposite targetCfg = LinkManager.get(remoteLevel).getFaceConfig(remoteNode.toKey());

            for (int count = 0; count < passCount && limit > 0; count++) {
                int idx = (startIdx + count) % passCount;
                int s = slotOrder[idx];
                ItemStack sim = from.extractItem(s, limit, true);
                if (sim.isEmpty()) continue;
                if (targetCfg != null && !FilterEvaluator.isItemInputAllowed(sim, targetCfg))
                    continue;

                // 存量维持
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
                    if (needed <= 0) continue;
                    sim = sim.copyWithCount(Math.min(sim.getCount(), needed));
                }

                int accepted = insertInto(to, sim);
                if (accepted <= 0) continue;

                from.extractItem(s, accepted, false);
                limit -= accepted;
                movedAny = true;
                lastProcessedIdx = idx;
                TransferLogManager.get().logTransfer(ctx.sourceNode(), remoteNode, ctx.type(), accepted, true);
            }
        }

        extractionStrategy.endTick(lastProcessedIdx, passCount, ctx, movedAny);
        return movedAny;
    }

    private static boolean isItemOutputAllowed(FaceConfigComposite config, ItemStack stack) {
        if (config == null) return true;
        return FilterEvaluator.isItemOutputAllowed(stack, config);
    }
}
