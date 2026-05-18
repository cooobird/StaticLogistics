package com.coobird.staticlogistics.datagen;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.integration.ModCompat;
import com.coobird.staticlogistics.registry.SLItems;
import com.simibubi.create.AllTags;
import mekanism.common.tags.MekanismTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
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
        super(output, lookupProvider, blockTags, Staticlogistics.MODID, helper);
    }

    @Override
    protected void addTags(HolderLookup.@NotNull Provider provider) {
        tag(Tags.Items.TOOLS_WRENCH).add(SLItems.LINK_CONFIGURATOR.get());
        tag(Tags.Items.TOOLS).add(SLItems.LINK_CONFIGURATOR.get());
        if (ModCompat.isCreateLoaded())
            tag(AllTags.AllItemTags.CHAIN_RIDEABLE.tag).add(SLItems.LINK_CONFIGURATOR.get());
        if (ModCompat.isMekanismLoaded()) tag(MekanismTags.Items.CONFIGURATORS).add(SLItems.LINK_CONFIGURATOR.get());
    }
}