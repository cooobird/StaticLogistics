package com.coobird.staticlogistics.api.type;

import com.coobird.staticlogistics.config.SLConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Rarity;

public enum UpgradeTier {
    IRON("iron", ChatFormatting.GRAY, Rarity.COMMON),
    GOLD("gold", ChatFormatting.GOLD, Rarity.COMMON),
    DIAMOND("diamond", ChatFormatting.AQUA, Rarity.RARE),
    NETHERITE("netherite", ChatFormatting.DARK_PURPLE, Rarity.EPIC),
    NETHER_STAR("nether_star", ChatFormatting.DARK_PURPLE, Rarity.EPIC);

    private final String name;
    private final ChatFormatting color;
    public final Rarity rarity;

    UpgradeTier(String name, ChatFormatting color, Rarity rarity) {
        this.name = name;
        this.color = color;
        this.rarity = rarity;
    }

    public String getSerializedName() {
        return this.name;
    }

    public int getMultiplier() {
        return SLConfig.getMultiplierForTier(this.name);
    }

    public Component getDisplayName() {
        return Component.translatable("tier.staticlogistics." + name).withStyle(color);
    }
}