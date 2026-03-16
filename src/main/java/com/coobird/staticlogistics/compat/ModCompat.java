package com.coobird.staticlogistics.compat;

import com.coobird.staticlogistics.common.util.TransferUtils;
import com.coobird.staticlogistics.core.StaticLink;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

import java.util.List;
import java.util.function.Supplier;

public class ModCompat {

    public static boolean hasModdedCapability(Level level, BlockPos pos, Direction face) {
        return (isLoaded(ModIds.MEKANISM, () -> level.getCapability(mekanism.common.capabilities.Capabilities.CHEMICAL.block(), pos, face) != null))
            || (isLoaded(ModIds.ARS_NOUVEAU, () -> level.getCapability(com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry.SOURCE_CAPABILITY, pos, face) != null));
    }

    public static boolean executeMekanism(ServerLevel level, List<StaticLink> links, int limit, int[] rrCursor) {
        return TransferUtils.doTransfer(level, links,
            mekanism.common.capabilities.Capabilities.CHEMICAL.block(), limit, rrCursor,
            new TransferUtils.SimpleProtocol<>(
                (src, max) -> src.extractChemical((long) max, Action.SIMULATE),
                (dst, stack) -> (int) (stack.getAmount() - dst.insertChemical(stack, Action.EXECUTE).getAmount()),
                (src, stack, act) -> src.extractChemical(stack.copyWithAmount((long) act), Action.EXECUTE),
                ChemicalStack::isEmpty
            ));
    }

    public static boolean executeArs(ServerLevel level, List<StaticLink> links, int limit, int[] rrCursor) {
        return TransferUtils.doTransfer(level, links,
            com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry.SOURCE_CAPABILITY, limit, rrCursor,
            new TransferUtils.SimpleProtocol<>(
                (src, max) -> src.extractSource(max, true),
                (dst, val) -> dst.receiveSource(val, false),
                (src, val, act) -> src.extractSource(act, false),
                val -> val <= 0
            ));
    }

    private static boolean isLoaded(String modId, Supplier<Boolean> check) {
        if (ModList.get().isLoaded(modId)) {
            try {
                return check.get();
            } catch (Throwable ignored) {
            }
        }
        return false;
    }
}