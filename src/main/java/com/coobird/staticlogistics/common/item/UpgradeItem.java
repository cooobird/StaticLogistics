package com.coobird.staticlogistics.common.item;

import com.coobird.staticlogistics.SLConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class UpgradeItem extends Item {
    private final UpgradeType type;
    @Nullable
    private final UpgradeTier tier;

    public UpgradeItem(UpgradeType type, UpgradeTier tier) {
        super(new Properties()
            .stacksTo(64)
            .rarity(tier.rarity));
        this.type = type;
        this.tier = tier;
    }

    public UpgradeItem(UpgradeType type) {
        super(new Properties()
            .stacksTo(1)
            .rarity(Rarity.RARE));
        this.type = type;
        this.tier = null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(type.getDescription().withStyle(ChatFormatting.GRAY));

        if (type == UpgradeType.DIMENSION) {
            tooltip.add(Component.translatable("tooltip.staticlogistics.upgrade.dimension_feature")
                .withStyle(ChatFormatting.GOLD));
        } else if (tier != null) {
            tooltip.add(Component.translatable("tooltip.staticlogistics.upgrade.tier_display", tier.getDisplayName()));
            int multiplier = tier.getMultiplier();
            String valueDisplay = (multiplier == Integer.MAX_VALUE) ? "∞" : "x" + multiplier;

            tooltip.add(Component.translatable("tooltip.staticlogistics.upgrade.value", valueDisplay)
                .withStyle(ChatFormatting.GREEN));
        }

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.staticlogistics.upgrade.install_hint")
            .withStyle(ChatFormatting.DARK_GRAY));
    }

    public UpgradeType getType() {
        return this.type;
    }

    @Nullable
    public UpgradeTier getTier() {
        return this.tier;
    }

    public enum UpgradeType {
        SPEED("speed"),
        RANGE("range"),
        STACK("stack"),
        DIMENSION("dimension");

        private final String name;

        UpgradeType(String name) {
            this.name = name;
        }

        public MutableComponent getDescription() {
            return Component.translatable("tooltip.staticlogistics.upgrade." + name + "_desc");
        }
    }

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
}