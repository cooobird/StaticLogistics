package com.coobird.staticlogistics.integration;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.api.ITransferHandler;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.transfer.context.TransferContext;
import com.coobird.staticlogistics.transfer.handler.TransferUtils;
import com.mojang.logging.LogUtils;
import me.desht.pneumaticcraft.common.registry.ModBlocks;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

public class ExtendedTypeRegisterHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ThreadLocal<Boolean> isInMekChemicalTransfer = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> isInMekHeatTransfer = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> isInArsSourceTransfer = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> isInPncPressureTransfer = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> isInPncHeatTransfer = ThreadLocal.withInitial(() -> false);

    public static void init() {
        if (ModCompat.isMekanismLoaded()) {
            registerMekanismChemicals();
            registerMekanismHeat();
        }
        if (ModCompat.isArsNouveauLoaded()) {
            registerArsNouveauSource();
        }
        if (ModCompat.isPneumaticcraftLoaded()) {
            registerPneumaticcraftPressure();
            registerPneumaticcraftHeat();
        }
    }

    private static void registerMekanismChemicals() {
        TransferType mekChemicals = new TransferType(
            Staticlogistics.asResource("mek_chemicals"),
            0xFF66FF66,
            3,
            "transfer_type.staticlogistics.mek_chemicals",
            mekanism.common.capabilities.Capabilities.CHEMICAL.block(),
            SLConfig::getMekChemicalStack,
            () -> ModCompat.isMekanismLoaded()
                ? new ItemStack(mekanism.common.registries.MekanismBlocks.BASIC_CHEMICAL_TANK.get())
                : new ItemStack(Items.BARRIER)
        );

        ITransferHandler handler = (context, targets) -> {
            if (isInMekChemicalTransfer.get()) {
                LOGGER.debug("Skipped reentrant mekanism chemical transfer for {}", context.sourceNode());
                return false;
            }

            TransferContext newContext = null;
            try {
                isInMekChemicalTransfer.set(true);
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
                                return (int) (stack.getAmount() - dst.insertChemical(stack, Action.EXECUTE).getAmount());
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
                    ctx,
                    ctx.linkManager().getCapabilityCache()
                );
            } finally {
                if (newContext != null) newContext.recycle();
                isInMekChemicalTransfer.set(false);
            }
        };

        TransferRegistries.registerExternal(mekChemicals, handler);
        LOGGER.info("Registered Mekanism chemical transfer support");
    }

    private static void registerMekanismHeat() {
        TransferType mekHeat = new TransferType(
            Staticlogistics.asResource("mek_heat"),
            0xFFFF6600,
            5,
            "transfer_type.staticlogistics.mek_heat",
            mekanism.common.capabilities.Capabilities.HEAT,
            SLConfig::getMekHeatStack,
            () -> ModCompat.isMekanismLoaded()
                ? new ItemStack(MekanismBlocks.RESISTIVE_HEATER.get())
                : new ItemStack(Items.BARRIER)
        );

        ITransferHandler handler = (context, targets) -> {
            if (isInMekHeatTransfer.get()) {
                LOGGER.debug("Skipped reentrant mekanism heat transfer for {}", context.sourceNode());
                return false;
            }

            TransferContext newContext = null;
            try {
                isInMekHeatTransfer.set(true);
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
                                    double extractable = temp * capacity;
                                    totalHeat += extractable;
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
                    ctx,
                    ctx.linkManager().getCapabilityCache()
                );
            } finally {
                if (newContext != null) newContext.recycle();
                isInMekHeatTransfer.set(false);
            }
        };

        TransferRegistries.registerExternal(mekHeat, handler);
        LOGGER.info("Registered Mekanism heat transfer support");
    }

    private static void registerArsNouveauSource() {
        TransferType arsType = new TransferType(
            Staticlogistics.asResource("ars_source"),
            0xFF8000FF,
            4,
            "transfer_type.staticlogistics.ars_source",
            com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry.SOURCE_CAPABILITY,
            SLConfig::getArsSourceStack,
            () -> ModCompat.isArsNouveauLoaded()
                ? new ItemStack(com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry.SOURCE_GEM)
                : new ItemStack(Items.BARRIER)
        );

        ITransferHandler arsHandler = (context, targets) -> {
            if (isInArsSourceTransfer.get()) {
                LOGGER.debug("Skipped reentrant ars source transfer for {}", context.sourceNode());
                return false;
            }

            TransferContext newContext = null;
            try {
                isInArsSourceTransfer.set(true);
                newContext = context.withIncrementedDepth();
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
                                LOGGER.error("Failed to simulate extract source: {}", e.getMessage());
                                return 0;
                            }
                        },
                        (dst, val) -> {
                            try {
                                return dst.receiveSource(val, false);
                            } catch (Exception e) {
                                LOGGER.error("Failed to receive source: {}", e.getMessage());
                                return 0;
                            }
                        },
                        (src, val, act) -> {
                            try {
                                src.extractSource(act, false);
                            } catch (Exception e) {
                                LOGGER.error("Failed to commit extract source: {}", e.getMessage());
                            }
                        },
                        val -> val <= 0
                    ),
                    ctx.isPullMode(),
                    ctx,
                    ctx.linkManager().getCapabilityCache()
                );
            } finally {
                if (newContext != null) newContext.recycle();
                isInArsSourceTransfer.set(false);
            }
        };

        TransferRegistries.registerExternal(arsType, arsHandler);
        LOGGER.info("Registered Ars Nouveau source transfer support");
    }

    private static void registerPneumaticcraftPressure() {
        TransferType pncPressure = new TransferType(
            Staticlogistics.asResource("pnc_pressure"),
            0xFF66CCFF,
            7,
            "transfer_type.staticlogistics.pnc_pressure",
            me.desht.pneumaticcraft.api.PNCCapabilities.AIR_HANDLER_MACHINE,
            SLConfig::getPneumaticPressureStack,
            () -> ModCompat.isPneumaticcraftLoaded()
                ? new ItemStack(me.desht.pneumaticcraft.common.registry.ModBlocks.PRESSURE_TUBE.get())
                : new ItemStack(Items.BARRIER)
        );

        ITransferHandler handler = (context, targets) -> {
            if (isInPncPressureTransfer.get()) {
                LOGGER.debug("Skipped reentrant pneumaticcraft pressure transfer for {}", context.sourceNode());
                return false;
            }

            TransferContext newContext = null;
            try {
                isInPncPressureTransfer.set(true);
                newContext = context.withIncrementedDepth();
                final TransferContext ctx = newContext;
                return TransferUtils.doTransferNodes(
                    ctx.level(),
                    ctx.sourceNode().gPos().pos(),
                    ctx.sourceNode().face(),
                    targets,
                    me.desht.pneumaticcraft.api.PNCCapabilities.AIR_HANDLER_MACHINE,
                    ctx.limit(),
                    new TransferUtils.SimpleProtocol<>(
                        (src, max) -> {
                            try {
                                return Math.min(max, src.getAir());
                            } catch (Exception e) {
                                LOGGER.error("Failed to simulate extract pressure: {}", e.getMessage());
                                return 0;
                            }
                        },
                        (dst, val) -> {
                            try {
                                int maxAir = dst.getVolume();
                                int accepted = Math.min(val, maxAir - dst.getAir());
                                dst.addAir(accepted);
                                return accepted;
                            } catch (Exception e) {
                                LOGGER.error("Failed to insert pressure: {}", e.getMessage());
                                return 0;
                            }
                        },
                        (src, val, act) -> {
                            try {
                                src.addAir(-act);
                            } catch (Exception e) {
                                LOGGER.error("Failed to commit extract pressure: {}", e.getMessage());
                            }
                        },
                        val -> val <= 0
                    ),
                    ctx.isPullMode(),
                    ctx,
                    ctx.linkManager().getCapabilityCache()
                );
            } finally {
                if (newContext != null) newContext.recycle();
                isInPncPressureTransfer.set(false);
            }
        };

        TransferRegistries.registerExternal(pncPressure, handler);
        LOGGER.info("Registered PneumaticCraft pressure transfer support");
    }

    private static void registerPneumaticcraftHeat() {
        TransferType pncHeat = new TransferType(
            Staticlogistics.asResource("pnc_heat"),
            0xFFFF5500,
            8,
            "transfer_type.staticlogistics.pnc_heat",
            me.desht.pneumaticcraft.api.PNCCapabilities.HEAT_EXCHANGER_BLOCK,
            SLConfig::getPneumaticHeatStack,
            () -> ModCompat.isPneumaticcraftLoaded()
                ? new ItemStack(ModBlocks.HEAT_SINK)
                : new ItemStack(Items.BARRIER)
        );

        ITransferHandler handler = (context, targets) -> {
            if (isInPncHeatTransfer.get()) {
                LOGGER.debug("Skipped reentrant pneumaticcraft heat transfer for {}", context.sourceNode());
                return false;
            }

            TransferContext newContext = null;
            try {
                isInPncHeatTransfer.set(true);
                newContext = context.withIncrementedDepth();
                final TransferContext ctx = newContext;
                return TransferUtils.doTransferNodes(
                    ctx.level(),
                    ctx.sourceNode().gPos().pos(),
                    ctx.sourceNode().face(),
                    targets,
                    me.desht.pneumaticcraft.api.PNCCapabilities.HEAT_EXCHANGER_BLOCK,
                    ctx.limit(),
                    new TransferUtils.SimpleProtocol<>(
                        (src, max) -> {
                            try {
                                return (int) Math.min(max, src.getTemperature());
                            } catch (Exception e) {
                                LOGGER.error("Failed to simulate extract heat temperature: {}", e.getMessage());
                                return 0;
                            }
                        },
                        (dst, val) -> {
                            try {
                                dst.addHeat(val);
                                return val;
                            } catch (Exception e) {
                                LOGGER.error("Failed to insert heat temperature: {}", e.getMessage());
                                return 0;
                            }
                        },
                        (src, val, act) -> {
                            try {
                                src.addHeat(-act);
                            } catch (Exception e) {
                                LOGGER.error("Failed to commit extract heat temperature: {}", e.getMessage());
                            }
                        },
                        val -> val <= 0
                    ),
                    ctx.isPullMode(),
                    ctx,
                    ctx.linkManager().getCapabilityCache()
                );
            } finally {
                if (newContext != null) newContext.recycle();
                isInPncHeatTransfer.set(false);
            }
        };

        TransferRegistries.registerExternal(pncHeat, handler);
        LOGGER.info("Registered PneumaticCraft heat transfer support");
    }
}