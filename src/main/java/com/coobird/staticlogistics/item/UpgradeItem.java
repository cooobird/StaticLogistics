package com.coobird.staticlogistics.item;

import com.coobird.staticlogistics.api.type.UpgradeTier;
import com.coobird.staticlogistics.api.type.UpgradeType;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.network.c2s.C2SOpenHandFilterPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class UpgradeItem extends Item {
    private final UpgradeType type;
    @Nullable
    private final UpgradeTier tier;

    public UpgradeItem(UpgradeType type) {
        super(new Item.Properties()
            .stacksTo(64)
            .rarity(Rarity.RARE));
        this.type = type;
        this.tier = null;
    }

    public UpgradeItem(UpgradeType type, UpgradeTier tier) {
        super(new Item.Properties()
            .stacksTo(64)
            .rarity(tier.rarity));
        this.type = type;
        this.tier = tier;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            PacketDistributor.sendToServer(new C2SOpenHandFilterPayload());
        }
        return InteractionResultHolder.success(stack);
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
            if (type == UpgradeType.SPEED) {
                int baseInterval = SLConfig.getDefaultTickInterval();
                int effectiveInterval = Math.max(1, (int) (baseInterval / Math.sqrt(multiplier)));
                String valueDisplay = effectiveInterval + " tick" + (effectiveInterval != 1 ? "s" : "");
                tooltip.add(Component.translatable("tooltip.staticlogistics.upgrade.value", valueDisplay)
                    .withStyle(ChatFormatting.GREEN));
            } else {
                String valueDisplay = (multiplier >= Integer.MAX_VALUE) ? "∞" : "x" + multiplier;
                tooltip.add(Component.translatable("tooltip.staticlogistics.upgrade.value", valueDisplay)
                    .withStyle(ChatFormatting.GREEN));
            }
        }

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.staticlogistics.upgrade.install_hint")
            .withStyle(ChatFormatting.DARK_GRAY));
        if (type == UpgradeType.TAG_FILTER || type == UpgradeType.NBT_FILTER || type == UpgradeType.BASIC_FILTER) {
            tooltip.add(Component.translatable("tooltip.staticlogistics.shift_right_mark")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    public UpgradeType getType() {
        return this.type;
    }

    @Nullable
    public UpgradeTier getTier() {
        return this.tier;
    }
}