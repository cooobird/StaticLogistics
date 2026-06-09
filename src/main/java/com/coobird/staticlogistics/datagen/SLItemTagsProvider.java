package com.coobird.staticlogistics.datagen;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.registry.SLItems;
import com.coobird.staticlogistics.tags.SLTags;
import mekanism.common.tags.MekanismTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
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
        tag(SLTags.Items.TOOLS_WRENCH).add(SLItems.LINK_CONFIGURATOR.get());
        tag(SLTags.Items.TOOLS_WIRE_CUTTERS).add(SLItems.LINK_CONFIGURATOR.get());
        tag(Tags.Items.TOOLS).add(SLItems.LINK_CONFIGURATOR.get());
        tag(MekanismTags.Items.CONFIGURATORS).add(SLItems.LINK_CONFIGURATOR.get());
    }
}