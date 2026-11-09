package com.coobird.staticlogistics.logistics.filter;

import com.coobird.staticlogistics.logistics.LogisticsUpgrade;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.node.EndpointConfig;
import com.coobird.staticlogistics.logistics.node.FilterConfig;
import net.minecraft.nbt.NumericTag;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.math.BigDecimal;
import java.util.*;

public final class FilterEvaluator {
    private FilterEvaluator() {
    }

    private record Compiled(FilterData source, int tagGeneration, Map<String, ItemStack> items,
                            Map<String, Fluid> fluids,
                            Map<String, List<CompiledFilterRule>> rules,
                            Map<String, List<CompiledFilterRule>> itemRules) {
    }

    private static final Map<FilterConfig, Compiled> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private static Compiled compiled(EndpointConfig config, FilterData data) {
        return CACHE.compute(config.filterConfig, (owner, previous) -> {
            if (previous != null && previous.source() == data && previous.tagGeneration() == FilterTagRegistry.generation())
                return previous;
            Map<String, ItemStack> items = data.items();
            Map<String, Fluid> fluids = new HashMap<>(data.fluids());
            items.forEach((slot, stack) -> {
                if (stack.getItem() instanceof BucketItem bucket) fluids.put(slot, bucket.getFluid());
            });
            Map<String, List<CompiledFilterRule>> rules = new HashMap<>();
            Map<String, List<CompiledFilterRule>> itemRules = new HashMap<>();
            data.rules().forEach((slot, values) -> {
                List<CompiledFilterRule> parsed = values.stream().map(CompiledFilterRule::new).toList();
                rules.put(slot, parsed);
                Set<String> overridden = new HashSet<>();
                for (CompiledFilterRule rule : parsed) {
                    if (rule.source().kind() == FilterRule.Kind.NUMERIC) overridden.add(rule.componentId());
                    if (rule.source().kind() == FilterRule.Kind.DURABILITY) overridden.add("Damage");
                }
                List<CompiledFilterRule> effective = new ArrayList<>();
                for (CompiledFilterRule rule : parsed) {
                    FilterRule source = rule.source();
                    if (source.kind() == FilterRule.Kind.COMPONENT && source.mode() != FilterRule.Mode.MATCH
                        && overridden.contains(source.key())) continue;
                    effective.add(rule);
                }
                itemRules.put(slot, List.copyOf(effective));
            });
            return new Compiled(data, FilterTagRegistry.generation(), items, Map.copyOf(fluids), Map.copyOf(rules), Map.copyOf(itemRules));
        });
    }

    private static FilterData data(EndpointConfig config) {
        ItemStack stack = config.filterConfig.getUpgrades().getStackInSlot(0);
        if (!(stack.getItem() instanceof LogisticsUpgrade upgrade) || !upgrade.isFilterUpgrade())
            return FilterData.EMPTY;
        return stack.getOrDefault(SLDataComponents.FILTER_DATA.get(), FilterData.EMPTY);
    }

    static boolean itemRule(ItemStack sample, ItemStack target, CompiledFilterRule compiled) {
        FilterRule rule = compiled.source();
        boolean result;
        if (rule.kind() == FilterRule.Kind.NUMERIC) {
            if (!ItemStack.isSameItem(sample, target)) return false;
            var raw = NbtRuleValues.get(target, compiled.componentId());
            if (!(raw instanceof NumericTag numeric)) return false;
            Number number = numeric.getAsNumber();
            if (number instanceof Float || number instanceof Double) {
                double actual = number.doubleValue();
                double expected = compiled.floatingValue();
                return switch (compiled.operator()) {
                    case "gt" -> actual > expected;
                    case "lt" -> actual < expected;
                    case "eq" -> actual == expected;
                    default -> false;
                };
            }
            int comparison = new BigDecimal(number.toString()).compareTo(compiled.numericValue());
            return switch (compiled.operator()) {
                case "gt" -> comparison > 0;
                case "lt" -> comparison < 0;
                case "eq" -> comparison == 0;
                default -> false;
            };
        }
        if (rule.kind() == FilterRule.Kind.DURABILITY) {
            if (!ItemStack.isSameItem(sample, target) || !target.isDamageableItem()) return false;
            long remaining = target.getMaxDamage() - target.getDamageValue();
            long value = compiled.durabilityValue();
            if (compiled.percent()) {
                remaining *= 100;
                value *= target.getMaxDamage();
            }
            return switch (compiled.operator()) {
                case "gt" -> remaining > value;
                case "lt" -> remaining < value;
                case "eq" -> remaining == value;
                default -> false;
            };
        }
        if (rule.kind() == FilterRule.Kind.NORMAL) {
            result = ItemStack.isSameItem(sample, target);
        } else if (rule.kind() == FilterRule.Kind.TAG) {
            if (!compiled.tagAvailable()) return false;
            result = compiled.itemTag().test(target);
        } else if (rule.mode() == FilterRule.Mode.MATCH) {
            result = ItemStack.isSameItemSameTags(sample, target);
        } else {
            result = NbtRuleValues.matchesSelected(sample, target, compiled.componentId());
        }
        boolean matches = (rule.mode() == FilterRule.Mode.INVERT) != result;
        return rule.kind() == FilterRule.Kind.COMPONENT ? ItemStack.isSameItem(sample, target) && matches : matches;
    }

    private static boolean testItem(ItemStack stack, EndpointConfig config) {
        FilterData filter = data(config);
        Compiled compiled = compiled(config, filter);
        Map<String, ItemStack> samples = compiled.items();
        if (samples.isEmpty()) return true;
        boolean hasWhitelist = false;
        boolean matchedWhitelist = false;
        for (var entry : samples.entrySet()) {
            boolean blacklist = filter.blacklist().getOrDefault(entry.getKey(), false);
            if (!blacklist) hasWhitelist = true;
            ItemStack sample = entry.getValue();
            List<CompiledFilterRule> rules = compiled.itemRules().getOrDefault(entry.getKey(), List.of());
            boolean accepts = rules.isEmpty() ? ItemStack.isSameItem(sample, stack)
                : rules.stream().allMatch(rule -> itemRule(sample, stack, rule));
            if (accepts && blacklist) return false;
            if (accepts) matchedWhitelist = true;
        }
        return !hasWhitelist || matchedWhitelist;
    }

    static boolean fluidRule(Fluid sample, Fluid target, CompiledFilterRule compiled) {
        FilterRule rule = compiled.source();
        boolean result;
        if (rule.kind() == FilterRule.Kind.TAG) {
            if (!compiled.tagAvailable()) return false;
            result = compiled.fluidTag().test(target);
        } else result = sample == target;
        return (rule.mode() == FilterRule.Mode.INVERT) != result;
    }

    private static boolean testFluid(FluidStack stack, EndpointConfig config) {
        FilterData filter = data(config);
        Compiled compiled = compiled(config, filter);
        Fluid fluid = stack.getFluid();
        Map<String, Fluid> samples = compiled.fluids();
        if (samples.isEmpty()) return true;
        boolean hasWhitelist = false;
        boolean matchedWhitelist = false;
        for (var entry : samples.entrySet()) {
            boolean blacklist = filter.blacklist().getOrDefault(entry.getKey(), false);
            if (!blacklist) hasWhitelist = true;
            List<CompiledFilterRule> rules = compiled.rules().getOrDefault(entry.getKey(), List.of());
            boolean accepts = rules.isEmpty() ? entry.getValue() == fluid
                : rules.stream().allMatch(rule -> fluidRule(entry.getValue(), fluid, rule));
            if (accepts && blacklist) return false;
            if (accepts) matchedWhitelist = true;
        }
        return !hasWhitelist || matchedWhitelist;
    }

    public static boolean isItemOutputAllowed(ItemStack stack, EndpointConfig config) {
        return config.isGlobalOutputEnabled() && testItem(stack, config);
    }

    public static boolean isItemInputAllowed(ItemStack stack, EndpointConfig config) {
        return config.isGlobalInputEnabled() && testItem(stack, config);
    }

    public static boolean isFluidOutputAllowed(FluidStack stack, EndpointConfig config) {
        return config.isGlobalOutputEnabled() && testFluid(stack, config);
    }

    public static boolean isFluidInputAllowed(FluidStack stack, EndpointConfig config) {
        return config.isGlobalInputEnabled() && testFluid(stack, config);
    }
}
