package com.coobird.staticlogistics.integration.handler;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.ITransferContext;
import com.coobird.staticlogistics.api.ITransferHandler;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.logic.TransferRegistries;
import com.coobird.staticlogistics.transfer.TransferContext;
import com.coobird.staticlogistics.transfer.handler.TransferUtils;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.List;

public class ArsSourceHandler implements ITransferHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<Boolean> isInTransfer = ThreadLocal.withInitial(() -> false);

    public static final TransferType TYPE = new TransferType(
        StaticLogistics.asResource("ars_source"),
        0xFF8000FF,
        4,
        "transfer_type.staticlogistics.ars_source",
        com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry.SOURCE_CAPABILITY,
        SLConfig::getArsSourceStack,
        () -> new ItemStack(com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry.SOURCE_GEM)
    );

    public static void register() {
        TransferRegistries.registerExternal(TYPE, new ArsSourceHandler());
        LOGGER.info("Registered Ars Nouveau source transfer support");
    }

    @Override
    public boolean performTransfer(ITransferContext context, List<LogisticsNode> targets) {
        if (isInTransfer.get()) {
            LOGGER.debug("Skipped reentrant ars source transfer for {}", context.sourceNode());
            return false;
        }

        TransferContext newContext = null;
        try {
            isInTransfer.set(true);
            newContext = ((TransferContext) context).withIncrementedDepth();
            final TransferContext ctx = newContext;
            return TransferUtils.doTransferNodes(
                ctx.level(),
                ctx.sourceNode().gPos().pos(),
                ctx.sourceNode().face(),
                targets,
                com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry.SOURCE_CAPABILITY,
                ctx.limit(),
                new TransferUtils.SimpleProtocol<>(
                    (src, max) -> {
                        try {
                            return src.extractSource(max, true);
                        } catch (Exception e) {
                            LOGGER.error("Transfer failed", e);
                            return 0;
                        }
                    },
                    (dst, val) -> {
                        try {
                            return dst.receiveSource(val, false);
                        } catch (Exception e) {
                            LOGGER.error("Transfer failed", e);
                            return 0;
                        }
                    },
                    (src, val, act) -> {
                        try {
                            src.extractSource(act, false);
                        } catch (Exception e) {
                            LOGGER.error("Transfer failed", e);
                        }
                    },
                    val -> val <= 0
                ),
                ctx.isPullMode(),
                ctx
            );
        } finally {
            if (newContext != null) newContext.recycle();
            isInTransfer.set(false);
        }
    }
}
