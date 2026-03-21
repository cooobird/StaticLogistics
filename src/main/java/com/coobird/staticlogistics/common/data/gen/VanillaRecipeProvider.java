package com.coobird.staticlogistics.common.data.gen;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.common.init.SLItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;

import java.util.concurrent.CompletableFuture;

public class VanillaRecipeProvider extends RecipeProvider {

    public VanillaRecipeProvider(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> provider) {
        super(packOutput, provider);
    }

    @Override
    protected void buildRecipes(RecipeOutput output, HolderLookup.Provider holderLookup) {
        shaped(output, "", "", ShapedRecipePattern.of(
            java.util.Map.of(
                'N', Ingredient.of(Items.NETHERITE_INGOT),
                'S', Ingredient.of(Items.NETHER_STAR),
                'U', Ingredient.of(SLItems.RANGE_UPGRADE_NETHER_STAR.get())
            ),
            "NSN",
            "NUN",
            "NNN"
        ), SLItems.DIMENSION_UPGRADE.toStack());
    }

    protected void shaped(RecipeOutput output, String prefix, String suffix, ShapedRecipePattern pattern, ItemStack result) {
        ResourceLocation id = Staticlogistics.asResource("shaped/" + prefix + getItemName(result.getItem()) + suffix);
        output.accept(id, new ShapedRecipe("", CraftingBookCategory.MISC, pattern, result, true), null
        );
    }

    protected void shapeless(RecipeOutput output, String prefix, String suffix, ItemStack result, Ingredient... ingredients) {
        ResourceLocation id = Staticlogistics.asResource("shapeless/" + prefix + getItemName(result.getItem()) + suffix);
        NonNullList<Ingredient> zingredients = NonNullList.of(Ingredient.EMPTY, ingredients);
        output.accept(id, new ShapelessRecipe("", CraftingBookCategory.MISC, result, zingredients), null);
    }

}
