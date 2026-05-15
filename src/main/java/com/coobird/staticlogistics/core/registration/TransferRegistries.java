package com.coobird.staticlogistics.core.registration;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.api.ITransferHandler;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.transfer.handler.StandardTransferHandlers;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class TransferRegistries {
    private static final Map<ResourceLocation, TransferType> TYPES = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ITransferHandler> HANDLERS = new LinkedHashMap<>();

    public static TransferType ITEM;
    public static TransferType FLUID;
    public static TransferType ENERGY;

    public static void init() {
        ITEM = registerInternal("item", 0xFFFFFFFF, 0, Capabilities.ItemHandler.BLOCK,
            SLConfig::getItemStack, () -> new ItemStack(Items.IRON_INGOT));

        FLUID = registerInternal("fluid", 0xFF3366FF, 1, Capabilities.FluidHandler.BLOCK,
            SLConfig::getFluidStack, () -> new ItemStack(Items.WATER_BUCKET));

        ENERGY = registerInternal("energy", 0xFFFFFF00, 2, Capabilities.EnergyStorage.BLOCK,
            SLConfig::getEnergyStack, () -> new ItemStack(Items.REDSTONE));

        registerHandler(ITEM, StandardTransferHandlers.ITEM);
        registerHandler(FLUID, StandardTransferHandlers.FLUID);
        registerHandler(ENERGY, StandardTransferHandlers.ENERGY);
    }

    private static TransferType registerInternal(String name, int color, int offset,
                                                 BlockCapability<?, Direction> cap,
                                                 IntSupplier sizeSupplier, Supplier<ItemStack> icon) {
        ResourceLocation id = Staticlogistics.asResource(name);
        TransferType type = new TransferType(id, color, offset, "transfer_type.staticlogistics." + name, cap, sizeSupplier, icon);
        TYPES.put(id, type);
        return type;
    }

    @Nullable
    public static TransferType get(ResourceLocation id) {
        return TYPES.get(id);
    }

    public static void registerHandler(TransferType type, ITransferHandler handler) {
        HANDLERS.put(type.id(), handler);
    }

    public static void registerExternal(TransferType type, ITransferHandler handler) {
        TYPES.put(type.id(), type);
        HANDLERS.put(type.id(), handler);
    }

    public static Collection<TransferType> getAllActive() {
        return TYPES.values();
    }

    @Nullable
    public static ITransferHandler getHandler(TransferType type) {
        return HANDLERS.get(type.id());
    }
}