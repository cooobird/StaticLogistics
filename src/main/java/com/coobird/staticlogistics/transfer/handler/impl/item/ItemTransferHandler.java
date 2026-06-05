package com.coobird.staticlogistics.transfer.handler.impl.item;

import com.coobird.staticlogistics.api.ITransferContext;
import com.coobird.staticlogistics.api.ITransferHandler;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.model.ContainerConfig;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.TransferContext;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;

import java.util.List;

/**
 * 物品传输处理器 —— 分发器，根据 pull/push 模式委托给对应策略。
 */
public class ItemTransferHandler implements ITransferHandler {
    public static final ItemTransferHandler INSTANCE = new ItemTransferHandler();

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<Boolean> isInTransfer = ThreadLocal.withInitial(() -> false);

    private final ItemPushTransfer push = new ItemPushTransfer();
    private final ItemPullTransfer pull = new ItemPullTransfer();

    @Override
    public boolean performTransfer(ITransferContext context, List<LogisticsNode> targets) {
        if (isInTransfer.get()) {
            LOGGER.debug("Skipped reentrant item transfer for {}", context.sourceNode());
            return false;
        }
        TransferContext newContext = null;
        try {
            isInTransfer.set(true);
            newContext = ((TransferContext) context).withIncrementedDepth();
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

            AbstractItemTransfer strategy = ctx.isPullMode() ? pull : push;
            return strategy.execute(ctx, targets, sourceCfg, localContainer);
        } finally {
            if (newContext != null) newContext.recycle();
            isInTransfer.set(false);
        }
    }
}
