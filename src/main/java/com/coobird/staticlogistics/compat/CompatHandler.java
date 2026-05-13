package com.coobird.staticlogistics.compat;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.transfer.handler.TransferUtils;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import net.neoforged.fml.ModList;

import static com.coobird.staticlogistics.compat.ModIds.ARS_NOUVEAU;
import static com.coobird.staticlogistics.compat.ModIds.MEKANISM;

public class CompatHandler {
    public static void init() {
        if (ModList.get().isLoaded(MEKANISM)) {
            TransferType mekType = new TransferType(
                Staticlogistics.asResource("mek_chemicals"),
                0xFFFF66FF, 3, "transfer_type.staticlogistics.mek_chemicals",
                mekanism.common.capabilities.Capabilities.CHEMICAL.block(),
                SLConfig.getMekChemicalStack()
            );

            TransferRegistries.registerExternal(mekType, (context, targets) ->
                TransferUtils.doTransferNodes(
                    context.level(),
                    context.sourceNode().gPos().pos(),
                    context.sourceNode().face(),
                    targets,
                    mekanism.common.capabilities.Capabilities.CHEMICAL.block(),
                    context.limit(),
                    new TransferUtils.SimpleProtocol<>(
                        (src, max) -> src.extractChemical((long) max, Action.SIMULATE),
                        (dst, stack) -> (int) (stack.getAmount() - dst.insertChemical(stack, Action.EXECUTE).getAmount()),
                        (src, stack, act) -> src.extractChemical(stack.copyWithAmount((long) act), Action.EXECUTE),
                        ChemicalStack::isEmpty
                    ),
                    context.isPullMode()
                ));
        }

        if (ModList.get().isLoaded(ARS_NOUVEAU)) {
            TransferType arsType = new TransferType(
                Staticlogistics.asResource("ars_source"),
                0xFF8000FF, 4, "transfer_type.staticlogistics.ars_source",
                com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry.SOURCE_CAPABILITY,
                SLConfig.getArsSourceStack()
            );

            TransferRegistries.registerExternal(arsType, (context, targets) ->
                TransferUtils.doTransferNodes(
                    context.level(),
                    context.sourceNode().gPos().pos(),
                    context.sourceNode().face(),
                    targets,
                    com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry.SOURCE_CAPABILITY,
                    context.limit(),
                    new TransferUtils.SimpleProtocol<>(
                        (src, max) -> src.extractSource(max, true),
                        (dst, val) -> dst.receiveSource(val, false),
                        (src, val, act) -> src.extractSource(act, false),
                        val -> val <= 0
                    ),
                    context.isPullMode()
                ));
        }
    }
}