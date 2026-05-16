package com.coobird.staticlogistics.item;

import com.coobird.staticlogistics.api.type.UpgradeTier;
import com.coobird.staticlogistics.api.type.UpgradeType;
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
            .stacksTo(1)
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

    public UpgradeItem(UpgradeType type, int stackSize) {
        super(new Item.Properties()
            .stacksTo(stackSize)
            .rarity(Rarity.RARE));
        this.type = type;
        this.tier = null;
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
        } else if (type == UpgradeType.TAG_FILTER) {
            tooltip.add(Component.translatable("tooltip.staticlogistics.upgrade.tag_filter_feature")
                .withStyle(ChatFormatting.YELLOW));
        } else if (type == UpgradeType.NBT_FILTER) {
            tooltip.add(Component.translatable("tooltip.staticlogistics.upgrade.nbt_filter_feature")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
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
}