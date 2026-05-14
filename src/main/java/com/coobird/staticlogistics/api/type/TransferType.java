package com.coobird.staticlogistics.api.type;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public record TransferType(
    ResourceLocation id,
    int color,
    int bitOffset,
    String translationKey,
    @Nullable BlockCapability<?, Direction> capability,
    int baseStackSize,
    Supplier<ItemStack> iconSupplier
) {
    public TransferType(ResourceLocation id, int color, int bitOffset, String translationKey,
                        @Nullable BlockCapability<?, Direction> capability, int baseStackSize) {
        this(id, color, bitOffset, translationKey, capability, baseStackSize,
            () -> new ItemStack(Items.PAPER));
    }

    public TransferType(ResourceLocation id, int color, int bitOffset, String translationKey,
                        @Nullable BlockCapability<?, Direction> capability, int baseStackSize,
                        Supplier<ItemStack> iconSupplier) {
        this.id = id;
        this.color = color;
        this.bitOffset = bitOffset;
        this.translationKey = translationKey;
        this.capability = capability;
        this.baseStackSize = baseStackSize;
        this.iconSupplier = iconSupplier;
    }

    public int getFlag() {
        return 1 << bitOffset;
    }

    public ItemStack getIcon() {
        return iconSupplier.get();
    }
}