package com.coobird.staticlogistics.api.type;

import com.coobird.staticlogistics.config.SLConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Rarity;

/**
 * 升级等级——铁、金、钻石、下界合金、下界之星，等级越高效果越好
 */
public enum UpgradeTier {
    IRON("iron", ChatFormatting.GRAY, Rarity.COMMON),
    GOLD("gold", ChatFormatting.GOLD, Rarity.COMMON),
    DIAMOND("diamond", ChatFormatting.AQUA, Rarity.RARE),
    NETHERITE("netherite", ChatFormatting.DARK_PURPLE, Rarity.EPIC),
    NETHER_STAR("nether_star", ChatFormatting.DARK_PURPLE, Rarity.EPIC);

    private final String name;              // 等级名称
    private final ChatFormatting color;     // 显示颜色
    public final Rarity rarity;             // 稀有度

    UpgradeTier(String name, ChatFormatting color, Rarity rarity) {
        this.name = name;
        this.color = color;
        this.rarity = rarity;
    }

    public String getSerializedName() {
        return this.name;
    }

    // 从配置文件里读这个等级的倍率
    public int getMultiplier() {
        return SLConfig.getMultiplierForTier(this.name);
    }

    // 获取带颜色样式的显示名称
    public Component getDisplayName() {
        return Component.translatable("tier.staticlogistics." + name).withStyle(color);
    }
}