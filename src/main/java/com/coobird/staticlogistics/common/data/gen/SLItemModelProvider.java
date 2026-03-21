package com.coobird.staticlogistics.common.data.gen;


import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.common.init.SLItems;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashSet;
import java.util.Set;

public class SLItemModelProvider extends ItemModelProvider {
    protected final Set<Item> skip = new HashSet<>();

    public SLItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, Staticlogistics.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        skip.add(SLItems.LINK_CONFIGURATOR.get());
        simpleItem(SLItems.ITEMS);
    }

    protected void simpleItem(DeferredRegister.Items register) {
        for (DeferredHolder<Item, ? extends Item> item : register.getEntries()) {
            if (skip.contains(item.get())) continue;

            String path = item.getId().getPath();
            withExistingParent(path, "item/generated")
                .texture("layer0", ResourceLocation.fromNamespaceAndPath(modid, "item/" + path));
        }
    }
}