package com.coobird.staticlogistics.api.type;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

public record TransferType(
    ResourceLocation id,
    int color,
    int bitOffset,
    String translationKey,
    @Nullable BlockCapability<?, Direction> capability,
    int baseStackSize
) {
    public int getFlag() {
        return 1 << bitOffset;
    }
}