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
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.List;

public class MekanismHeatHandler implements ITransferHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<Boolean> isInTransfer = ThreadLocal.withInitial(() -> false);

    public static final TransferType TYPE = new TransferType(
        Staticlogistics.asResource("mek_heat"),
        0xFFFF6600,
        5,
        "transfer_type.staticlogistics.mek_heat",
        mekanism.common.capabilities.Capabilities.HEAT,
        SLConfig::getMekHeatStack,
        () -> new ItemStack(mekanism.common.registries.MekanismBlocks.RESISTIVE_HEATER.get())
    );

    public static void register() {
        TransferRegistries.registerExternal(TYPE, new MekanismHeatHandler());
        LOGGER.info("Registered Mekanism heat transfer support");
    }

    @Override
    public boolean performTransfer(TransferContext context, List<LogisticsNode> targets) {
        if (isInTransfer.get()) {
            LOGGER.debug("Skipped reentrant mekanism heat transfer for {}", context.sourceNode());
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
                mekanism.common.capabilities.Capabilities.HEAT,
                ctx.limit(),
                new TransferUtils.SimpleProtocol<>(
                    (src, max) -> {
                        try {
                            double totalHeat = 0;
                            int capacitorCount = src.getHeatCapacitorCount();
                            for (int i = 0; i < capacitorCount; i++) {
                                double temp = src.getTemperature(i);
                                double capacity = src.getHeatCapacity(i);
                                totalHeat += temp * capacity;
                            }
                            return (int) Math.min(max, totalHeat);
                        } catch (Exception e) {
                            LOGGER.error("Failed to simulate extract heat: {}", e.getMessage());
                            return 0;
                        }
                    },
                    (dst, val) -> {
                        try {
                            dst.handleHeat(val);
                            return val;
                        } catch (Exception e) {
                            LOGGER.error("Failed to insert heat: {}", e.getMessage());
                            return 0;
                        }
                    },
                    (src, val, act) -> {
                        try {
                            int capacitorCount = src.getHeatCapacitorCount();
                            double totalCapacity = src.getTotalHeatCapacity();
                            for (int i = 0; i < capacitorCount; i++) {
                                double ratio = src.getHeatCapacity(i) / totalCapacity;
                                double toExtract = act * ratio;
                                src.handleHeat(i, -toExtract);
                            }
                        } catch (Exception e) {
                            LOGGER.error("Failed to commit extract heat: {}", e.getMessage());
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
