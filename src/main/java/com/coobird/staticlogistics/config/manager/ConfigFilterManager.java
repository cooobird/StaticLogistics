package com.coobird.staticlogistics.config.manager;

import com.coobird.staticlogistics.api.type.UpgradeType;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.filter.core.BasicLogisticsFilter;
import com.coobird.staticlogistics.filter.core.NbtLogisticsFilter;
import com.coobird.staticlogistics.filter.core.TagLogisticsFilter;
import com.coobird.staticlogistics.filter.data.FilterData;
import com.coobird.staticlogistics.item.UpgradeItem;
import com.coobird.staticlogistics.registry.SLDataComponents;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.storage.config.LinkConfig;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ConfigFilterManager {

    private record FilterDataWithType(FilterData filter, @Nullable UpgradeType type) {
        private FilterDataWithType(FilterData filter, @Nullable UpgradeType type) {
            this.filter = Objects.requireNonNull(filter);
            this.type = type;
        }
    }

    public static boolean isItemOutputAllowed(ItemStack stack, FaceConfigComposite config) {
        LinkConfig.SideData data = config.linkConfig.getSettings(TransferRegistries.ITEM);
        if (!data.outputEnabled) return false;
        FilterDataWithType fwt = getFilterDataFromSlot(config, false);
        if (fwt.filter == FilterData.EMPTY || fwt.type == null) return true;
        return testItemFilters(stack, fwt.filter, fwt.type);
    }

    public static boolean isItemInputAllowed(ItemStack stack, FaceConfigComposite config) {
        LinkConfig.SideData data = config.linkConfig.getSettings(TransferRegistries.ITEM);
        if (!data.inputEnabled) return false;
        FilterDataWithType fwt = getFilterDataFromSlot(config, true);
        if (fwt.filter == FilterData.EMPTY || fwt.type == null) return true;
        return testItemFilters(stack, fwt.filter, fwt.type);
    }

    public static boolean isFluidOutputAllowed(FluidStack stack, FaceConfigComposite config) {
        LinkConfig.SideData data = config.linkConfig.getSettings(TransferRegistries.FLUID);
        if (!data.outputEnabled) return false;
        FilterDataWithType fwt = getFilterDataFromSlot(config, false);
        if (fwt.filter == FilterData.EMPTY || fwt.type == null) return true;
        return testFluidFilters(stack, fwt.filter, fwt.type);
    }

    public static boolean isFluidInputAllowed(FluidStack stack, FaceConfigComposite config) {
        LinkConfig.SideData data = config.linkConfig.getSettings(TransferRegistries.FLUID);
        if (!data.inputEnabled) return false;
        FilterDataWithType fwt = getFilterDataFromSlot(config, true);
        if (fwt.filter == FilterData.EMPTY || fwt.type == null) return true;
        return testFluidFilters(stack, fwt.filter, fwt.type);
    }

    private static FilterDataWithType getFilterDataFromSlot(FaceConfigComposite config, boolean isInput) {
        int slot = isInput ? 0 : 1;
        ItemStack upgradeStack = config.filterConfig.getUpgrades().getStackInSlot(slot);
        if (upgradeStack.isEmpty() || !(upgradeStack.getItem() instanceof UpgradeItem)) {
            return new FilterDataWithType(FilterData.EMPTY, null);
        }
        UpgradeType type = ((UpgradeItem) upgradeStack.getItem()).getType();
        if (type != UpgradeType.BASIC_FILTER && type != UpgradeType.TAG_FILTER && type != UpgradeType.NBT_FILTER) {
            return new FilterDataWithType(FilterData.EMPTY, null);
        }
        FilterData data = upgradeStack.getOrDefault(SLDataComponents.FILTER_DATA.get(), FilterData.EMPTY);
        return new FilterDataWithType(data, type);
    }

    private static boolean testItemFilters(ItemStack stack, FilterData filter, UpgradeType upgradeType) {
        Objects.requireNonNull(filter, "filter must not be null");
        Objects.requireNonNull(upgradeType, "upgradeType must not be null");

        boolean useBasic = upgradeType == UpgradeType.BASIC_FILTER;
        boolean useTag = upgradeType == UpgradeType.TAG_FILTER;
        boolean useNbt = upgradeType == UpgradeType.NBT_FILTER;

        Set<Item> basicItems = useBasic ? filter.items().values().stream()
            .filter(s -> !s.isEmpty())
            .map(ItemStack::getItem)
            .collect(Collectors.toSet()) : Collections.emptySet();
        boolean hasBasicFilter = useBasic && !basicItems.isEmpty();

        Set<TagKey<Item>> allWhitelistTags = filter.tagSlots().values().stream()
            .flatMap(Set::stream)
            .collect(Collectors.toSet());
        Set<TagKey<Item>> allBlacklistTags = filter.excludedTagSlots().values().stream()
            .flatMap(Set::stream)
            .collect(Collectors.toSet());
        boolean hasTagFilter = useTag && (!allWhitelistTags.isEmpty() || !allBlacklistTags.isEmpty());

        ItemStack nbtTemplate = useNbt ? filter.items().values().stream()
            .filter(s -> !s.isEmpty())
            .findFirst().orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        boolean hasNbtFilter = useNbt && !nbtTemplate.isEmpty();

        if (!hasBasicFilter && !hasTagFilter && !hasNbtFilter) return true;

        boolean isBlacklist = filter.isBlacklist();

        boolean basicPass = !hasBasicFilter || new BasicLogisticsFilter(basicItems, Collections.emptySet(), true).test(stack, isBlacklist);
        boolean tagPass = !hasTagFilter || new TagLogisticsFilter(allWhitelistTags, allBlacklistTags, Collections.emptySet(), Collections.emptySet(), true).test(stack, isBlacklist);
        boolean nbtPass = !hasNbtFilter || new NbtLogisticsFilter(nbtTemplate, filter.nbtMatchMode(), true).test(stack, isBlacklist);

        return basicPass && tagPass && nbtPass;
    }

    private static boolean testFluidFilters(FluidStack stack, FilterData filter, UpgradeType upgradeType) {
        Objects.requireNonNull(filter, "filter must not be null");
        Objects.requireNonNull(upgradeType, "upgradeType must not be null");

        boolean useBasic = upgradeType == UpgradeType.BASIC_FILTER;
        boolean useTag = upgradeType == UpgradeType.TAG_FILTER;

        Set<Fluid> fluids = new HashSet<>();
        if (useBasic) {
            fluids.addAll(filter.fluids().values());
            for (ItemStack itemStack : filter.items().values()) {
                if (!itemStack.isEmpty() && itemStack.getItem() instanceof BucketItem bucket) {
                    fluids.add(bucket.content);
                }
            }
        }
        boolean hasBasicFilter = useBasic && !fluids.isEmpty();
        boolean hasTagFilter = useTag && (!filter.fluidFilterTags().isEmpty() || !filter.excludedFluidTags().isEmpty());

        if (!hasBasicFilter && !hasTagFilter) return true;

        boolean isBlacklist = filter.isBlacklist();

        boolean basicPass = !hasBasicFilter || new BasicLogisticsFilter(Collections.emptySet(), fluids, true).test(stack, isBlacklist);
        boolean tagPass = !hasTagFilter || new TagLogisticsFilter(Collections.emptySet(), Collections.emptySet(),
            filter.fluidFilterTags(), filter.excludedFluidTags(), true).test(stack, isBlacklist);

        return basicPass && tagPass;
    }
}