package com.coobird.staticlogistics.api.type;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiPredicate;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 传输类型定义——物品、流体、能量等，每种类型有自己的图标、颜色、位标记等元数据
 */
public record TransferType(
    ResourceLocation id,
    int color,
    int bitOffset,
    String translationKey,
    @Nullable BlockCapability<?, Direction> capability,
    IntSupplier baseStackSizeSupplier,
    Supplier<ItemStack> iconSupplier,
    @Nullable BiPredicate<Level, BlockPos> customCapCheck
) {
    public TransferType(ResourceLocation id, int color, int bitOffset, String translationKey,
                        @Nullable BlockCapability<?, Direction> capability, IntSupplier baseStackSizeSupplier) {
        this(id, color, bitOffset, translationKey, capability, baseStackSizeSupplier, () -> new ItemStack(Items.PAPER), null);
    }

    public TransferType(ResourceLocation id, int color, int bitOffset, String translationKey,
                        @Nullable BlockCapability<?, Direction> capability, IntSupplier baseStackSizeSupplier,
                        Supplier<ItemStack> iconSupplier) {
        this(id, color, bitOffset, translationKey, capability, baseStackSizeSupplier, iconSupplier, null);
    }

    public TransferType(ResourceLocation id, int color, int bitOffset, String translationKey,
                        @Nullable BlockCapability<?, Direction> capability, IntSupplier baseStackSizeSupplier,
                        Supplier<ItemStack> iconSupplier, @Nullable BiPredicate<Level, BlockPos> customCapCheck) {
        this.id = id;
        this.color = color;
        this.bitOffset = bitOffset;
        this.translationKey = translationKey;
        this.capability = capability;
        this.baseStackSizeSupplier = baseStackSizeSupplier;
        this.iconSupplier = iconSupplier;
        this.customCapCheck = customCapCheck;
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