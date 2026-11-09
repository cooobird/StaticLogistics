package com.coobird.staticlogistics.content.item;

import com.coobird.staticlogistics.content.SLKeyNames;
import com.coobird.staticlogistics.logistics.LogisticsUpgrade;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import com.coobird.staticlogistics.network.c2s.C2SOpenHandFilterPayload;
import com.coobird.staticlogistics.transfer.UpgradeTier;
import com.coobird.staticlogistics.transfer.UpgradeType;
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
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.mesdag.portlib.network.PortPacketDistributor;

import java.util.List;
import java.util.Map;

public class UpgradeItem extends Item implements LogisticsUpgrade {
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
    public boolean isFoil(ItemStack stack) {
        return tier == UpgradeTier.NETHER_STAR || super.isFoil(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide && isFilterUpgrade()) {
            PortPacketDistributor.sendToServer(new C2SOpenHandFilterPayload(hand == InteractionHand.OFF_HAND));
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(type.getDescription().withStyle(ChatFormatting.GRAY));

        if (tier != null) {
            tooltip.add(Component.translatable("tooltip.staticlogistics.upgrade.tier_display", tier.getDisplayName()));
            tooltip.add(Component.translatable("tooltip.staticlogistics.upgrade.value", "×" + tier.getMultiplier()).withStyle(ChatFormatting.GREEN));
            if (tier == UpgradeTier.NETHER_STAR)
                tooltip.add(Component.translatable("tooltip.staticlogistics.upgrade.dimension_feature").withStyle(ChatFormatting.GOLD));
        }

        if (isFilterUpgrade()) {
            appendFilterDataTooltip(stack, tooltip);
        }

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.staticlogistics.upgrade.install_hint")
            .withStyle(ChatFormatting.DARK_GRAY));
        if (type == UpgradeType.FILTER) {
            tooltip.add(Component.translatable("tooltip.staticlogistics.shift_left_mark",
                    Component.keybind(SLKeyNames.QUICK_FILTER_MARK))
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    /// 直接展示过滤器物品上持久化的数据组件，保证手持配置界面与物品提示始终同源。
    private void appendFilterDataTooltip(ItemStack stack, List<Component> tooltip) {
        FilterData filter = stack.getOrDefault(SLDataComponents.FILTER_DATA.get(), FilterData.EMPTY)
            .normalizedFor(type);



        List<Map.Entry<String, ItemStack>> markedItems = filter.items().entrySet().stream()
            .filter(entry -> !entry.getValue().isEmpty())
            .sorted(Map.Entry.comparingByKey(UpgradeItem::compareSlotKeys))
            .toList();
        if (!markedItems.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.staticlogistics.filter.items", markedItems.size())
                .withStyle(ChatFormatting.GRAY));
            for (Map.Entry<String, ItemStack> entry : markedItems) {
                tooltip.add(Component.literal(filter.blacklist().getOrDefault(entry.getKey(), false) ? "  − " : "  + ")
                    .withStyle(filter.blacklist().getOrDefault(entry.getKey(), false) ? ChatFormatting.RED : ChatFormatting.GREEN)
                    .append(entry.getValue().getHoverName().copy().withStyle(ChatFormatting.WHITE)));
            }
        }

        var markedFluids = filter.fluids().entrySet().stream()
            .sorted(Map.Entry.comparingByKey(UpgradeItem::compareSlotKeys)).toList();
        if (!markedFluids.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.staticlogistics.filter.fluids", markedFluids.size())
                .withStyle(ChatFormatting.GRAY));
            for (var entry : markedFluids) {
                tooltip.add(Component.literal(filter.blacklist().getOrDefault(entry.getKey(), false) ? "  − " : "  + ")
                    .withStyle(filter.blacklist().getOrDefault(entry.getKey(), false) ? ChatFormatting.RED : ChatFormatting.GREEN)
                    .append(new FluidStack(entry.getValue(), 1).getDisplayName()
                        .copy().withStyle(ChatFormatting.AQUA)));
            }
        }

        if (markedItems.isEmpty() && markedFluids.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.staticlogistics.filter.empty")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static int compareSlotKeys(String first, String second) {
        try {
            return Integer.compare(Integer.parseInt(first), Integer.parseInt(second));
        } catch (NumberFormatException ignored) {
            return first.compareTo(second);
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
