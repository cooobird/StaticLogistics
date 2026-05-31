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
 * 浼犺緭绫诲瀷瀹氫箟鈥斺€旂墿鍝併€佹祦浣撱€佽兘閲忕瓑锛屾瘡绉嶇被鍨嬫湁鑷繁鐨勫浘鏍囥€侀鑹层€佷綅鏍囪绛夊厓鏁版嵁
 */
public record TransferType(
    ResourceLocation id,
    int color,
    int bitOffset,
    String translationKey,
    @Nullable BlockCapability<?, Direction> capability,
    IntSupplier baseStackSizeSupplier,
    Supplier<ItemStack> iconSupplier,
    @Nullable BiPredicate<Level, BlockPos> customCapCheck,
    boolean requiresCooldown,
    boolean requiresValidLinks
) {
    public TransferType(ResourceLocation id, int color, int bitOffset, String translationKey,
                        @Nullable BlockCapability<?, Direction> capability, IntSupplier baseStackSizeSupplier) {
        this(id, color, bitOffset, translationKey, capability, baseStackSizeSupplier,
            () -> new ItemStack(Items.PAPER), null, true, true);
    }

    public TransferType(ResourceLocation id, int color, int bitOffset, String translationKey,
                        @Nullable BlockCapability<?, Direction> capability, IntSupplier baseStackSizeSupplier,
                        Supplier<ItemStack> iconSupplier) {
        this(id, color, bitOffset, translationKey, capability, baseStackSizeSupplier, iconSupplier, null, true, true);
    }

    public TransferType(ResourceLocation id, int color, int bitOffset, String translationKey,
                        @Nullable BlockCapability<?, Direction> capability, IntSupplier baseStackSizeSupplier,
                        Supplier<ItemStack> iconSupplier, @Nullable BiPredicate<Level, BlockPos> customCapCheck) {
        this(id, color, bitOffset, translationKey, capability, baseStackSizeSupplier, iconSupplier, customCapCheck, true, true);
    }

    public TransferType(ResourceLocation id, int color, int bitOffset, String translationKey,
                        @Nullable BlockCapability<?, Direction> capability, IntSupplier baseStackSizeSupplier,
                        Supplier<ItemStack> iconSupplier, @Nullable BiPredicate<Level, BlockPos> customCapCheck,
                        boolean requiresCooldown, boolean requiresValidLinks) {
        this.id = id;
        this.color = color;
        this.bitOffset = bitOffset;
        this.translationKey = translationKey;
        this.capability = capability;
        this.baseStackSizeSupplier = baseStackSizeSupplier;
        this.iconSupplier = iconSupplier;
        this.customCapCheck = customCapCheck;
        this.requiresCooldown = requiresCooldown;
        this.requiresValidLinks = requiresValidLinks;
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