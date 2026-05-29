package com.coobird.staticlogistics.integration.handler;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.api.ITransferHandler;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.transfer.context.TransferContext;
import com.coobird.staticlogistics.transfer.handler.TransferUtils;
import com.mojang.logging.LogUtils;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.List;

public class MekanismChemicalHandler implements ITransferHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<Boolean> isInTransfer = ThreadLocal.withInitial(() -> false);

    public static final TransferType TYPE = new TransferType(
        Staticlogistics.asResource("mek_chemicals"),
        0xFF66FF66,
        3,
        "transfer_type.staticlogistics.mek_chemicals",
        mekanism.common.capabilities.Capabilities.CHEMICAL.block(),
        SLConfig::getMekChemicalStack,
        () -> new ItemStack(mekanism.common.registries.MekanismBlocks.BASIC_CHEMICAL_TANK.get())
    );

    public static void register() {
        TransferRegistries.registerExternal(TYPE, new MekanismChemicalHandler());
        LOGGER.info("Registered Mekanism chemical transfer support");
    }

    @Override
    public boolean performTransfer(TransferContext context, List<LogisticsNode> targets) {
        if (isInTransfer.get()) {
            LOGGER.debug("Skipped reentrant mekanism chemical transfer for {}", context.sourceNode());
            return false;
        }

        TransferContext newContext = null;
        try {
            isInTransfer.set(true);
            newContext = context.withIncrementedDepth();
            final TransferContext ctx = newContext;
            return TransferUtils.doTransferNodes(
                ctx.level(),
                ctx.sourceNode().gPos().pos(),
                ctx.sourceNode().face(),
                targets,
                mekanism.common.capabilities.Capabilities.CHEMICAL.block(),
                ctx.limit(),
                new TransferUtils.SimpleProtocol<>(
                    (src, max) -> {
                        try {
                            return src.extractChemical((long) max, Action.SIMULATE);
                        } catch (Exception e) {
                            LOGGER.error("Failed to simulate extract chemical: {}", e.getMessage());
                            return ChemicalStack.EMPTY;
                        }
                    },
                    (dst, stack) -> {
                        try {
                            return (int) (stack.getAmount()
                                - dst.insertChemical(stack, Action.EXECUTE).getAmount());
                        } catch (Exception e) {
                            LOGGER.error("Failed to insert chemical: {}", e.getMessage());
                            return 0;
                        }
                    },
                    (src, stack, act) -> {
                        try {
                            src.extractChemical(stack.copyWithAmount((long) act), Action.EXECUTE);
                        } catch (Exception e) {
                            LOGGER.error("Failed to commit extract chemical: {}", e.getMessage());
                        }
                    },
                    ChemicalStack::isEmpty
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
