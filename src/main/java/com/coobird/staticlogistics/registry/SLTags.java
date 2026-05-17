package com.coobird.staticlogistics.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * 定义模组用到的物品标签键（引用外部标签，如扳手标签）。
 */
public class SLTags {
    /**
     * 物品标签相关的内部类
     */
    public static class Items {
        /**
         * Common标签：扳手类物品（来自c:wrenches标签）
         */
        public static final TagKey<Item> WRENCHES = c("wrenches");

        /**
         * 快捷方法：创建c命名空间下的物品标签键
         */
        private static TagKey<Item> c(String name) {
            return ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", name));
        }
    }
}
