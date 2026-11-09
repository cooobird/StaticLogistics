package com.coobird.staticlogistics.api.filter;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import java.util.function.Predicate;
import java.util.stream.Stream;

/// 为过滤器提供某个注册表的标签选项和匹配逻辑。
public interface FilterTagProvider {
    ResourceLocation registryId();

    default Component displayName() {
        return Component.literal(registryId().toString());
    }

    default Stream<ResourceLocation> tags(ItemStack stack) {
        return Stream.empty();
    }

    default Stream<ResourceLocation> tags(Fluid fluid) {
        return Stream.empty();
    }

    default Predicate<ItemStack> compileItems(ResourceLocation tag) {
        return stack -> false;
    }

    default Predicate<Fluid> compileFluids(ResourceLocation tag) {
        return fluid -> false;
    }
}
