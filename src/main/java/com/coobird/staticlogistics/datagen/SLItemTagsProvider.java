package com.coobird.staticlogistics.datagen;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.registry.SLItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class SLItemTagsProvider extends ItemTagsProvider {

    public SLItemTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                              @Nullable ExistingFileHelper helper) {
        this(output, lookupProvider, CompletableFuture.completedFuture(TagLookup.empty()), helper);
    }

    public SLItemTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                              CompletableFuture<TagLookup<Block>> blockTags,
                              @Nullable ExistingFileHelper helper) {
        super(output, lookupProvider, blockTags, StaticLogistics.MODID, helper);
    }

    @Override
    protected void addTags(HolderLookup.@NotNull Provider provider) {
        tag(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("forge", "tools/wrench"))).add(SLItems.LINK_CONFIGURATOR.get());
        tag(Tags.Items.TOOLS).add(SLItems.LINK_CONFIGURATOR.get());
        tag(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("mekanism", "configurators"))).add(SLItems.LINK_CONFIGURATOR.get());
    }
}
