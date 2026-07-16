package com.coobird.staticlogistics.logistics.filter;

import PortLib.extensions.net.minecraft.world.item.ItemStack.PortItemStackExtension;
import com.coobird.staticlogistics.logistics.LogisticsUpgrade;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.UpgradeType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

public class FilterEvaluator {

    private record CompiledItemFilters(
        Set<Item> basicItems,
        Set<TagKey<Item>> whitelistTags,
        Set<TagKey<Item>> blacklistTags,
        ItemStack nbtTemplate,
        BasicLogisticsFilter basicFilter,
        TagLogisticsFilter tagFilter,
        NbtLogisticsFilter nbtFilter
    ) {
    }

    private record CompiledFluidFilters(
        Set<Fluid> fluids,
        Set<TagKey<Fluid>> whitelistTags,
        Set<TagKey<Fluid>> blacklistTags,
        BasicLogisticsFilter basicFilter,
        TagLogisticsFilter tagFilter
    ) {
    }

    private static final Map<FilterData, CompiledItemFilters> ITEM_FILTER_CACHE =
        Collections.synchronizedMap(new WeakHashMap<>(64));
    private static final Map<FilterData, CompiledFluidFilters> FLUID_FILTER_CACHE =
        Collections.synchronizedMap(new WeakHashMap<>(64));

    private static CompiledItemFilters compileItemFilters(FilterData filter) {
        return ITEM_FILTER_CACHE.computeIfAbsent(filter, data -> {
            Set<Item> items = data.items().values().stream()
                .filter(stack -> !stack.isEmpty())
                .map(ItemStack::getItem)
                .collect(Collectors.toUnmodifiableSet());
            Set<TagKey<Item>> whitelist = data.tagSlots().values().stream()
                .flatMap(Set::stream).collect(Collectors.toUnmodifiableSet());
            Set<TagKey<Item>> blacklist = data.excludedTagSlots().values().stream()
                .flatMap(Set::stream).collect(Collectors.toUnmodifiableSet());
            ItemStack template = data.items().values().stream()
                .filter(stack -> !stack.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
            return new CompiledItemFilters(items, whitelist, blacklist, template,
                new BasicLogisticsFilter(items, Collections.emptySet(), true),
                new TagLogisticsFilter(whitelist, blacklist,
                    Collections.emptySet(), Collections.emptySet(), true),
                new NbtLogisticsFilter(template, data.nbtMatchMode(), true, data.ignoreDamage()));
        });
    }

    private static CompiledFluidFilters compileFluidFilters(FilterData filter) {
        return FLUID_FILTER_CACHE.computeIfAbsent(filter, data -> {
            Set<Fluid> fluids = new HashSet<>(data.fluids().values());
            for (ItemStack stack : data.items().values()) {
                if (!stack.isEmpty() && stack.getItem() instanceof BucketItem bucket) {
                    fluids.add(bucket.getFluid());
                }
            }
            Set<TagKey<Fluid>> whitelist = data.fluidFilterTags().values().stream()
                .flatMap(Set::stream).collect(Collectors.toUnmodifiableSet());
            Set<TagKey<Fluid>> blacklist = data.excludedFluidTags().values().stream()
                .flatMap(Set::stream).collect(Collectors.toUnmodifiableSet());
            return new CompiledFluidFilters(fluids, whitelist, blacklist,
                new BasicLogisticsFilter(Collections.emptySet(), fluids, true),
                new TagLogisticsFilter(Collections.emptySet(), Collections.emptySet(),
                    whitelist, blacklist, true));
        });
    }

    private record FilterDataWithType(FilterData filter, @Nullable UpgradeType type) {
        private FilterDataWithType(FilterData filter, @Nullable UpgradeType type) {
            this.filter = Objects.requireNonNull(filter);
            this.type = type;
        }
    }

    public static boolean isItemOutputAllowed(ItemStack stack, FaceConfigComposite config) {
        if (!config.isGlobalOutputEnabled()) return false;
        FilterDataWithType fwt = getFilterDataFromSlot(config, false);
        if (fwt.filter == FilterData.EMPTY || fwt.type == null) return true;
        return testItemFilters(stack, fwt.filter, fwt.type);
    }

    public static boolean isItemInputAllowed(ItemStack stack, FaceConfigComposite config) {
        if (!config.isGlobalInputEnabled()) return false;
        FilterDataWithType fwt = getFilterDataFromSlot(config, true);
        if (fwt.filter == FilterData.EMPTY || fwt.type == null) return true;
        return testItemFilters(stack, fwt.filter, fwt.type);
    }

    public static boolean isFluidOutputAllowed(FluidStack stack, FaceConfigComposite config) {
        if (!config.isGlobalOutputEnabled()) return false;
        FilterDataWithType fwt = getFilterDataFromSlot(config, false);
        if (fwt.filter == FilterData.EMPTY || fwt.type == null) return true;
        return testFluidFilters(stack, fwt.filter, fwt.type);
    }

    public static boolean isFluidInputAllowed(FluidStack stack, FaceConfigComposite config) {
        if (!config.isGlobalInputEnabled()) return false;
        FilterDataWithType fwt = getFilterDataFromSlot(config, true);
        if (fwt.filter == FilterData.EMPTY || fwt.type == null) return true;
        return testFluidFilters(stack, fwt.filter, fwt.type);
    }

    private static FilterDataWithType getFilterDataFromSlot(FaceConfigComposite config, boolean isInput) {
        int slot = isInput ? 0 : 1;
        ItemStack upgradeStack = config.filterConfig.getUpgrades().getStackInSlot(slot);
        if (upgradeStack.isEmpty() || !(upgradeStack.getItem() instanceof LogisticsUpgrade)) {
            return new FilterDataWithType(FilterData.EMPTY, null);
        }
        UpgradeType type = ((LogisticsUpgrade) upgradeStack.getItem()).getType();
        if (type != UpgradeType.BASIC_FILTER && type != UpgradeType.TAG_FILTER && type != UpgradeType.NBT_FILTER) {
            return new FilterDataWithType(FilterData.EMPTY, null);
        }
        FilterData data = PortItemStackExtension.getDataOrDefault(upgradeStack, SLDataComponents.FILTER_DATA.get(), FilterData.EMPTY);
        return new FilterDataWithType(data, type);
    }

    private static boolean testItemFilters(ItemStack stack, FilterData filter, UpgradeType upgradeType) {
        Objects.requireNonNull(filter, "filter must not be null");
        Objects.requireNonNull(upgradeType, "upgradeType must not be null");

        boolean useBasic = upgradeType == UpgradeType.BASIC_FILTER;
        boolean useTag = upgradeType == UpgradeType.TAG_FILTER;
        boolean useNbt = upgradeType == UpgradeType.NBT_FILTER;

        CompiledItemFilters compiled = compileItemFilters(filter);
        boolean hasBasicFilter = useBasic && !compiled.basicItems().isEmpty();
        boolean hasTagFilter = useTag
            && (!compiled.whitelistTags().isEmpty() || !compiled.blacklistTags().isEmpty());
        boolean hasNbtFilter = useNbt && !compiled.nbtTemplate().isEmpty();

        if (!hasBasicFilter && !hasTagFilter && !hasNbtFilter) return true;

        boolean isBlacklist = filter.isBlacklist();

        boolean basicPass = !hasBasicFilter || compiled.basicFilter().test(stack, isBlacklist);
        boolean tagPass = !hasTagFilter || compiled.tagFilter().test(stack, isBlacklist);
        boolean nbtPass = !hasNbtFilter || compiled.nbtFilter().test(stack, isBlacklist);

        return basicPass && tagPass && nbtPass;
    }

    private static boolean testFluidFilters(FluidStack stack, FilterData filter, UpgradeType upgradeType) {
        Objects.requireNonNull(filter, "filter must not be null");
        Objects.requireNonNull(upgradeType, "upgradeType must not be null");

        boolean useBasic = upgradeType == UpgradeType.BASIC_FILTER;
        boolean useTag = upgradeType == UpgradeType.TAG_FILTER;

        CompiledFluidFilters compiled = compileFluidFilters(filter);
        boolean hasBasicFilter = useBasic && !compiled.fluids().isEmpty();
        boolean hasTagFilter = useTag
            && (!compiled.whitelistTags().isEmpty() || !compiled.blacklistTags().isEmpty());

        if (!hasBasicFilter && !hasTagFilter) return true;

        boolean isBlacklist = filter.isBlacklist();

        boolean basicPass = !hasBasicFilter || compiled.basicFilter().test(stack, isBlacklist);
        boolean tagPass = !hasTagFilter || compiled.tagFilter().test(stack, isBlacklist);

        return basicPass && tagPass;
    }


}
