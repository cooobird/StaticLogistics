package com.coobird.staticlogistics.api.type;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

public record TransferType(
    ResourceLocation id,
    int color,
    int bitOffset,
    String translationKey,
    @Nullable BlockCapability<?, Direction> capability,
    IntSupplier baseStackSizeSupplier,
    Supplier<ItemStack> iconSupplier
) {
    public TransferType(ResourceLocation id, int color, int bitOffset, String translationKey,
                        @Nullable BlockCapability<?, Direction> capability, IntSupplier baseStackSizeSupplier) {
        this(id, color, bitOffset, translationKey, capability, baseStackSizeSupplier,
            () -> new ItemStack(Items.PAPER));
    }

    public TransferType(ResourceLocation id, int color, int bitOffset, String translationKey,
                        @Nullable BlockCapability<?, Direction> capability, IntSupplier baseStackSizeSupplier,
                        Supplier<ItemStack> iconSupplier) {
        this.id = id;
        this.color = color;
        this.bitOffset = bitOffset;
        this.translationKey = translationKey;
        this.capability = capability;
        this.baseStackSizeSupplier = baseStackSizeSupplier;
        this.iconSupplier = iconSupplier;
    }

    public int getFlag() {
        return 1 << bitOffset;
    }

    public ItemStack getIcon() {
        return iconSupplier.get();
    }

    public int getBaseStackSize() {
        return baseStackSizeSupplier.getAsInt();
    }
}