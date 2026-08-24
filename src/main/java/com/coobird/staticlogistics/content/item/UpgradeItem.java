package com.coobird.staticlogistics.content.item;

import PortLib.extensions.net.minecraft.world.item.ItemStack.PortItemStackExtension;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.content.SLKeyNames;
import com.coobird.staticlogistics.logistics.LogisticsUpgrade;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import com.coobird.staticlogistics.logistics.filter.NbtMatchMode;
import com.coobird.staticlogistics.network.SLNetwork;
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
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            if (type == UpgradeType.BASIC_FILTER || type == UpgradeType.TAG_FILTER || type == UpgradeType.NBT_FILTER) {
                SLNetwork.HANDLER.sendToServer(new C2SOpenHandFilterPayload(
                    hand == InteractionHand.OFF_HAND));
            }
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
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
                String valueDisplay;
                if (multiplier >= Integer.MAX_VALUE) {
                    valueDisplay = Component.translatable("gui.staticlogistics.infinite").getString();
                } else {
                    valueDisplay = "x" + multiplier;
                }
                tooltip.add(Component.translatable("tooltip.staticlogistics.upgrade.value", valueDisplay)
                    .withStyle(ChatFormatting.GREEN));
            }
        }

        if (isFilterUpgrade()) appendFilterDataTooltip(stack, tooltip);

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.staticlogistics.upgrade.install_hint")
            .withStyle(ChatFormatting.DARK_GRAY));
        if (type == UpgradeType.TAG_FILTER || type == UpgradeType.NBT_FILTER || type == UpgradeType.BASIC_FILTER) {
            tooltip.add(Component.translatable("tooltip.staticlogistics.shift_right_mark",
                    Component.keybind(SLKeyNames.QUICK_FILTER_MARK))
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    /**
     * 直接展示过滤器物品持久化的数据，保证配置界面和物品提示同源。
     */
    private void appendFilterDataTooltip(ItemStack stack, List<Component> tooltip) {
        FilterData filter = PortItemStackExtension.getDataOrDefault(
            stack, SLDataComponents.FILTER_DATA.get(), FilterData.EMPTY).normalizedFor(type);
        tooltip.add(Component.translatable("tooltip.staticlogistics.filter.mode",
                Component.translatable(filter.isBlacklist()
                    ? "gui.staticlogistics.blacklist_button"
                    : "gui.staticlogistics.whitelist_button"))
            .withStyle(filter.isBlacklist() ? ChatFormatting.RED : ChatFormatting.GREEN));

        if (type == UpgradeType.NBT_FILTER) {
            tooltip.add(Component.translatable("tooltip.staticlogistics.filter.nbt_mode",
                    Component.translatable(filter.nbtMatchMode() == NbtMatchMode.FULL
                        ? "gui.staticlogistics.full_match_button"
                        : "gui.staticlogistics.part_match_button"))
                .withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.staticlogistics.filter.damage",
                    Component.translatable(filter.ignoreDamage()
                        ? "gui.staticlogistics.true" : "gui.staticlogistics.false"))
                .withStyle(ChatFormatting.DARK_GRAY));
        }

        List<Map.Entry<String, ItemStack>> markedItems = filter.items().entrySet().stream()
            .filter(entry -> !entry.getValue().isEmpty())
            .sorted(Map.Entry.comparingByKey(UpgradeItem::compareSlotKeys)).toList();
        if (!markedItems.isEmpty()) {
            tooltip.add(Component.translatable(
                    "tooltip.staticlogistics.filter.items", markedItems.size())
                .withStyle(ChatFormatting.GRAY));
            for (Map.Entry<String, ItemStack> entry : markedItems) {
                tooltip.add(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(entry.getValue().getHoverName().copy()
                        .withStyle(ChatFormatting.WHITE)));
            }
        }

        var markedFluids = filter.fluids().entrySet().stream()
            .sorted(Map.Entry.comparingByKey(UpgradeItem::compareSlotKeys)).toList();
        if (!markedFluids.isEmpty()) {
            tooltip.add(Component.translatable(
                    "tooltip.staticlogistics.filter.fluids", markedFluids.size())
                .withStyle(ChatFormatting.GRAY));
            for (var entry : markedFluids) {
                tooltip.add(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(new FluidStack(entry.getValue(), 1).getDisplayName().copy()
                        .withStyle(ChatFormatting.AQUA)));
            }
        }

        List<Component> tagLines = new java.util.ArrayList<>();
        filter.tagSlots().values().stream().flatMap(java.util.Collection::stream)
            .map(tag -> tag.location().toString()).distinct().sorted()
            .forEach(tag -> tagLines.add(Component.literal("  + #" + tag)
                .withStyle(ChatFormatting.GREEN)));
        filter.excludedTagSlots().values().stream().flatMap(java.util.Collection::stream)
            .map(tag -> tag.location().toString()).distinct().sorted()
            .forEach(tag -> tagLines.add(Component.literal("  − #" + tag)
                .withStyle(ChatFormatting.RED)));
        filter.fluidFilterTags().values().stream().flatMap(java.util.Collection::stream)
            .map(tag -> tag.location().toString()).distinct().sorted()
            .forEach(tag -> tagLines.add(Component.literal("  + #" + tag)
                .withStyle(ChatFormatting.AQUA)));
        filter.excludedFluidTags().values().stream().flatMap(java.util.Collection::stream)
            .map(tag -> tag.location().toString()).distinct().sorted()
            .forEach(tag -> tagLines.add(Component.literal("  − #" + tag)
                .withStyle(ChatFormatting.RED)));
        if (!tagLines.isEmpty()) {
            tooltip.add(Component.translatable(
                    "tooltip.staticlogistics.filter.tags", tagLines.size())
                .withStyle(ChatFormatting.GRAY));
            tooltip.addAll(tagLines);
        }

        if (markedItems.isEmpty() && markedFluids.isEmpty() && tagLines.isEmpty()) {
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
