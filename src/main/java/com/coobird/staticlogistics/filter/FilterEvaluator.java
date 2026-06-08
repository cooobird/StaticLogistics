package com.coobird.staticlogistics.filter;

import com.coobird.staticlogistics.filter.core.BasicLogisticsFilter;
import com.coobird.staticlogistics.filter.core.NbtLogisticsFilter;
import com.coobird.staticlogistics.filter.core.TagLogisticsFilter;
import com.coobird.staticlogistics.item.UpgradeItem;
import com.coobird.staticlogistics.logic.UpgradeType;
import com.coobird.staticlogistics.registry.SLDataComponents;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 过滤评估器 —— 判断物品/流体是否通过面配置的过滤规则。
 *
 * <p>编译结果按 {@link FilterData} 实例缓存，同一 FilterData 只编译一次。
 * FilterData 是不可变 record（DataComponent），修改过滤器时会创建新实例，
 * 旧实例会被 GC 回收，缓存随之失效。
 */
public class FilterEvaluator {

    // 编译缓存
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
        Set<TagKey<Fluid>> fluidWhitelistTags,
        Set<TagKey<Fluid>> fluidBlacklistTags,
        BasicLogisticsFilter basicFilter,
        TagLogisticsFilter tagFilter
    ) {
    }

    private static final Map<FilterData, CompiledItemFilters> ITEM_FILTER_CACHE = new WeakHashMap<>(64);
    private static final Map<FilterData, CompiledFluidFilters> FLUID_FILTER_CACHE = new WeakHashMap<>(64);

    private static CompiledItemFilters compileItemFilters(FilterData filter) {
        return ITEM_FILTER_CACHE.computeIfAbsent(filter, f -> {
            Set<Item> basicItems = f.items().values().stream()
                .filter(s -> !s.isEmpty())
                .map(ItemStack::getItem)
                .collect(Collectors.toSet());

            Set<TagKey<Item>> whitelistTags = f.tagSlots().values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
            Set<TagKey<Item>> blacklistTags = f.excludedTagSlots().values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());

            ItemStack nbtTemplate = f.items().values().stream()
                .filter(s -> !s.isEmpty())
                .findFirst().orElse(ItemStack.EMPTY);

            return new CompiledItemFilters(
                basicItems, whitelistTags, blacklistTags, nbtTemplate,
                new BasicLogisticsFilter(basicItems, Collections.emptySet(), true),
                new TagLogisticsFilter(whitelistTags, blacklistTags, Collections.emptySet(), Collections.emptySet(), true),
                new NbtLogisticsFilter(nbtTemplate, f.nbtMatchMode(), true, f.ignoreDamage())
            );
        });
    }

    private static CompiledFluidFilters compileFluidFilters(FilterData filter) {
        return FLUID_FILTER_CACHE.computeIfAbsent(filter, f -> {
            Set<Fluid> fluids = new HashSet<>();
            fluids.addAll(f.fluids().values());
            for (ItemStack itemStack : f.items().values()) {
                if (!itemStack.isEmpty() && itemStack.getItem() instanceof BucketItem bucket) {
                    fluids.add(bucket.content);
                }
            }

            Set<TagKey<Fluid>> fluidWhitelistTags = f.fluidFilterTags().values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
            Set<TagKey<Fluid>> fluidBlacklistTags = f.excludedFluidTags().values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());

            return new CompiledFluidFilters(
                fluids, fluidWhitelistTags, fluidBlacklistTags,
                new BasicLogisticsFilter(Collections.emptySet(), fluids, true),
                new TagLogisticsFilter(Collections.emptySet(), Collections.emptySet(),
                    fluidWhitelistTags, fluidBlacklistTags, true)
            );
        });
    }

    // 查询接口
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

        CompiledItemFilters compiled = compileItemFilters(filter);

        boolean useBasic = upgradeType == UpgradeType.BASIC_FILTER;
        boolean useTag = upgradeType == UpgradeType.TAG_FILTER;
        boolean useNbt = upgradeType == UpgradeType.NBT_FILTER;

        boolean hasBasicFilter = useBasic && !compiled.basicItems.isEmpty();
        boolean hasTagFilter = useTag && (!compiled.whitelistTags.isEmpty() || !compiled.blacklistTags.isEmpty());
        boolean hasNbtFilter = useNbt && !compiled.nbtTemplate.isEmpty();

        if (!hasBasicFilter && !hasTagFilter && !hasNbtFilter) return true;

        boolean isBlacklist = filter.isBlacklist();

        boolean basicPass = !hasBasicFilter || compiled.basicFilter.test(stack, isBlacklist);
        boolean tagPass = !hasTagFilter || compiled.tagFilter.test(stack, isBlacklist);
        boolean nbtPass = !hasNbtFilter || compiled.nbtFilter.test(stack, isBlacklist);

        return basicPass && tagPass && nbtPass;
    }

    private static boolean testFluidFilters(FluidStack stack, FilterData filter, UpgradeType upgradeType) {
        Objects.requireNonNull(filter, "filter must not be null");
        Objects.requireNonNull(upgradeType, "upgradeType must not be null");

        CompiledFluidFilters compiled = compileFluidFilters(filter);

        boolean useBasic = upgradeType == UpgradeType.BASIC_FILTER;
        boolean useTag = upgradeType == UpgradeType.TAG_FILTER;

        boolean hasBasicFilter = useBasic && !compiled.fluids.isEmpty();
        boolean hasTagFilter = useTag && (!compiled.fluidWhitelistTags.isEmpty() || !compiled.fluidBlacklistTags.isEmpty());

        if (!hasBasicFilter && !hasTagFilter) return true;

        boolean isBlacklist = filter.isBlacklist();

        boolean basicPass = !hasBasicFilter || compiled.basicFilter.test(stack, isBlacklist);
        boolean tagPass = !hasTagFilter || compiled.tagFilter.test(stack, isBlacklist);

        return basicPass && tagPass;
    }
}
