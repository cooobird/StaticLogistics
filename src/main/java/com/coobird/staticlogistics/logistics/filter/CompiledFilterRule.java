package com.coobird.staticlogistics.logistics.filter;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import java.math.BigDecimal;
import java.util.function.Predicate;

/// 预解析规则中的组件、标签和数值，供传输时重复使用。
final class CompiledFilterRule {
    private final FilterRule source;
    private String componentId;
    private DataComponentType<?> componentType;
    private String path;
    private String operator;
    private BigDecimal numericValue;
    private double floatingValue;
    private long durabilityValue;
    private boolean percent;
    private boolean tagAvailable;
    private Predicate<ItemStack> itemTag;
    private Predicate<Fluid> fluidTag;

    CompiledFilterRule(FilterRule source) {
        this.source = source;
        String key = source.key();
        switch (source.kind()) {
            case NUMERIC -> {
                String[] parts = key.split("\\|", -1);
                componentId = parts[0];
                componentType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.parse(componentId));
                operator = parts[1];
                numericValue = new BigDecimal(parts[2]);
                floatingValue = Double.parseDouble(parts[2]);
            }
            case DURABILITY -> {
                String[] parts = key.split("\\|", -1);
                operator = parts[0];
                percent = parts[1].equals("percent");
                durabilityValue = Long.parseLong(parts[2]);
            }
            case COMPONENT -> {
                if (source.mode() == FilterRule.Mode.MATCH) break;
                int separator = key.indexOf('#');
                componentId = separator < 0 ? key : key.substring(0, separator);
                componentType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.parse(componentId));
                path = separator < 0 ? null : key.substring(separator + 1);
            }
            case TAG -> {
                int separator = key.indexOf('|');
                ResourceLocation id = ResourceLocation.parse(separator < 0 ? key : key.substring(separator + 1));
                var itemProvider = FilterTagRegistry.get(separator < 0 ? Registries.ITEM.location()
                    : ResourceLocation.parse(key.substring(0, separator)));
                var fluidProvider = separator < 0 ? FilterTagRegistry.get(Registries.FLUID.location()) : itemProvider;
                tagAvailable = itemProvider != null && fluidProvider != null;
                if (tagAvailable) {
                    itemTag = itemProvider.compileItems(id);
                    fluidTag = fluidProvider.compileFluids(id);
                }
            }
            case NORMAL -> {
            }
        }
    }

    FilterRule source() {
        return source;
    }

    String componentId() {
        return componentId;
    }

    DataComponentType<?> componentType() {
        return componentType;
    }

    String path() {
        return path;
    }

    String operator() {
        return operator;
    }

    BigDecimal numericValue() {
        return numericValue;
    }

    double floatingValue() {
        return floatingValue;
    }

    long durabilityValue() {
        return durabilityValue;
    }

    boolean percent() {
        return percent;
    }

    boolean tagAvailable() {
        return tagAvailable;
    }

    Predicate<ItemStack> itemTag() {
        return itemTag;
    }

    Predicate<Fluid> fluidTag() {
        return fluidTag;
    }
}
