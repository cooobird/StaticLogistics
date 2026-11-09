package com.coobird.staticlogistics.datagen;


import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.registry.SLItems;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashSet;
import java.util.Set;

public class SLItemModelProvider extends ItemModelProvider {
    protected final Set<Item> handheld = new HashSet<>();

    public SLItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, StaticLogistics.MODID, existingFileHelper);
        handheld.add(SLItems.LINK_CONFIGURATOR.get());
    }

    @Override
    protected void registerModels() {
        simpleItem(SLItems.ITEMS);
    }

    protected void simpleItem(DeferredRegister<Item> register) {
        for (RegistryObject<? extends Item> item : register.getEntries()) {
            String path = item.getId().getPath();
            String parent = handheld.contains(item.get()) ? "item/handheld" : "item/generated";
            withExistingParent(path, parent)
                .texture("layer0", StaticLogistics.asResource("item/" + path));
        }
    }
}
