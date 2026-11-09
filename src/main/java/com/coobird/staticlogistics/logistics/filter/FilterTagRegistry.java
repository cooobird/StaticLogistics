package com.coobird.staticlogistics.logistics.filter;

import com.coobird.staticlogistics.api.filter.FilterTagProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

/// 管理标签类型，统一选项枚举、显示与规则解析。
public final class FilterTagRegistry {
    private static final Map<ResourceLocation, FilterTagProvider> PROVIDERS = new LinkedHashMap<>();
    private static int generation;

    static {
        register(new FilterTagProvider() {
            public ResourceLocation registryId() {
                return Registries.ITEM.location();
            }

            public Component displayName() {
                return Component.translatable("gui.staticlogistics.filter.tag_type.item");
            }

            public Stream<ResourceLocation> tags(ItemStack stack) {
                return stack.getTags().map(TagKey::location);
            }

            public Predicate<ItemStack> compileItems(ResourceLocation id) {
                var tag = TagKey.create(Registries.ITEM, id);
                return stack -> stack.is(tag);
            }
        });
        register(new FilterTagProvider() {
            public ResourceLocation registryId() {
                return Registries.BLOCK.location();
            }

            public Component displayName() {
                return Component.translatable("gui.staticlogistics.filter.tag_type.block");
            }

            public Stream<ResourceLocation> tags(ItemStack stack) {
                return stack.getItem() instanceof BlockItem block ? block.getBlock().defaultBlockState().getTags().map(TagKey::location) : Stream.empty();
            }

            public Predicate<ItemStack> compileItems(ResourceLocation id) {
                var tag = TagKey.create(Registries.BLOCK, id);
                return stack -> stack.getItem() instanceof BlockItem block && block.getBlock().defaultBlockState().is(tag);
            }
        });
        register(new FilterTagProvider() {
            public ResourceLocation registryId() {
                return Registries.FLUID.location();
            }

            public Component displayName() {
                return Component.translatable("gui.staticlogistics.filter.tag_type.fluid");
            }

            public Stream<ResourceLocation> tags(ItemStack stack) {
                return stack.getItem() instanceof BucketItem bucket ? tags(bucket.content) : Stream.empty();
            }

            public Stream<ResourceLocation> tags(Fluid fluid) {
                return BuiltInRegistries.FLUID.wrapAsHolder(fluid).tags().map(TagKey::location);
            }

            public Predicate<Fluid> compileFluids(ResourceLocation id) {
                var tag = TagKey.create(Registries.FLUID, id);
                return fluid -> BuiltInRegistries.FLUID.wrapAsHolder(fluid).is(tag);
            }
        });
    }

    private FilterTagRegistry() {
    }

    public static void register(FilterTagProvider provider) {
        if (PROVIDERS.putIfAbsent(provider.registryId(), provider) != null)
            throw new IllegalArgumentException("Duplicate filter tag registry: " + provider.registryId());
        generation++;
    }

    public static FilterTagProvider get(ResourceLocation registry) {
        return PROVIDERS.get(registry);
    }

    static int generation() {
        return generation;
    }

    public static List<String> options(ItemStack stack, Fluid fluid) {
        List<String> result = new ArrayList<>();
        for (FilterTagProvider provider : PROVIDERS.values()) {
            TreeSet<ResourceLocation> tags = new TreeSet<>();
            if (!stack.isEmpty()) provider.tags(stack).forEach(tags::add);
            if (fluid != null) provider.tags(fluid).forEach(tags::add);
            for (ResourceLocation tag : tags) result.add(provider.registryId() + "|" + tag);
        }
        result.sort(String::compareTo);
        return result;
    }

    public static String display(String key) {
        int separator = key.indexOf('|');
        if (separator < 0) return key;
        ResourceLocation registry = ResourceLocation.parse(key.substring(0, separator));
        FilterTagProvider provider = get(registry);
        return (provider == null ? registry.toString() : provider.displayName().getString()) + ": " + key.substring(separator + 1);
    }

    public static String canonicalKey(String key, ResourceLocation defaultRegistry) {
        int separator = key.indexOf('|');
        return separator < 0 ? defaultRegistry + "|" + key : ResourceLocation.parse(key.substring(0, separator)) + "|" + key.substring(separator + 1);
    }
}
