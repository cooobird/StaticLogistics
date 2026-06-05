package com.coobird.staticlogistics.transfer.handler.impl.item;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.filter.FilterEvaluator;
import com.coobird.staticlogistics.storage.model.ContainerConfig;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.TransferContext;
import com.coobird.staticlogistics.transfer.TransferLogManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 物品拉取策略 —— 从远程源容器提取物品，插入本地目标容器。
 */
public class ItemPullTransfer extends AbstractItemTransfer {

    @Override
    public boolean execute(TransferContext ctx, List<LogisticsNode> targets,
                           FaceConfigComposite sourceCfg, ContainerConfig localContainer) {
        ServerLevel localLevel = ctx.level();
        BlockPos localPos = ctx.sourceNode().gPos().pos();
        Direction localFace = ctx.sourceNode().face();
        int limit = ctx.limit();
        boolean movedAny = false;

        IItemHandler to = localLevel.getCapability(Capabilities.ItemHandler.BLOCK, localPos, localFace);
        if (to == null) return false;

        for (LogisticsNode remoteNode : targets) {
            if (limit <= 0) break;

            ServerLevel remoteLevel = validateTarget(remoteNode, localLevel, localPos, localContainer);
            if (remoteLevel == null) continue;

            IItemHandler from = resolveRemoteCapability(remoteLevel, remoteNode, sourceCfg, ctx);
            if (from == null) continue;

            // 收集远程可用槽位（通过输入+输出过滤器）
            List<SlotItem> available = new ArrayList<>();
            int slots = from.getSlots();
            for (int s = 0; s < slots; s++) {
                ItemStack sim = from.extractItem(s, limit, true);
                if (sim.isEmpty()) continue;
                if (!FilterEvaluator.isItemInputAllowed(sim, sourceCfg)) continue;
                if (!FilterEvaluator.isItemOutputAllowed(sim, sourceCfg)) continue;
                available.add(new SlotItem(sim, s));
            }
            if (available.isEmpty()) continue;

            available.sort(Comparator.comparingInt(a -> ctx.sourceConfig().linkConfig.getPriority()));

            for (SlotItem si : available) {
                if (limit <= 0) break;
                ItemStack sim = from.extractItem(si.slot, limit, true);
                if (sim.isEmpty()) continue;

                int accepted = insertInto(to, sim);
                if (accepted <= 0) continue;

                from.extractItem(si.slot, accepted, false);
                limit -= accepted;
                movedAny = true;
                TransferLogManager.get().logTransfer(remoteNode, ctx.sourceNode(), ctx.type(), accepted, true);
            }
        }
        return movedAny;
    }

    private record SlotItem(ItemStack stack, int slot) {
    }
}
