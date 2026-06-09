package com.coobird.staticlogistics.tags;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public class SLTags {

    public static void init() {
        Items.init();
    }

    public static class Items {

        private static void init() {
        }

        public static final TagKey<Item> TOOLS_WRENCH = forgeTag("tools/wrench");
        public static final TagKey<Item> TOOLS_WIRE_CUTTERS = forgeTag("tools/wire_cutters");
    }

    private static TagKey<Item> forgeTag(String name) {
        return ItemTags.create(ResourceLocation.fromNamespaceAndPath("forge", name));
    }
}
