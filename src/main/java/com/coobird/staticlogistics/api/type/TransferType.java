package com.coobird.staticlogistics.api.type;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 传输类型定义——物品、流体、能量等，每种类型有自己的图标、颜色、位标记等元数据
 */
public record TransferType(
    ResourceLocation id,       // 唯一标识符
    int color,                  // 显示用的颜色
    int bitOffset,              // 位偏移量，用来生成位标记
    String translationKey,      // 本地化 key
    @Nullable BlockCapability<?, Direction> capability, // 对应的 NeoForge Capability
    IntSupplier baseStackSizeSupplier,   // 每次传输的基础数量
    Supplier<ItemStack> iconSupplier     // 图标物品的提供者
) {
    // 简化构造——没有图标时默认用纸
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

    // 位运算标志位：用 1 << bitOffset，用于位掩码判断
    public int getFlag() {
        return 1 << bitOffset;
    }

    // 获取这个传输类型对应的图标物品
    public ItemStack getIcon() {
        return iconSupplier.get();
    }

    // 获取每次传输的基础数量
    public int getBaseStackSize() {
        return baseStackSizeSupplier.getAsInt();
    }
}