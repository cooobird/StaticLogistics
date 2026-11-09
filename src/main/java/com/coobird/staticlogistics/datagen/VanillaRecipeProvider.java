package com.coobird.staticlogistics.datagen;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.registry.SLItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class VanillaRecipeProvider extends RecipeProvider {

    public VanillaRecipeProvider(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> provider) {
        super(packOutput, provider);
    }

    @Override
    protected void buildRecipes(RecipeOutput output, HolderLookup.Provider holderLookup) {
        /// 连接配置器
        shaped(output, "", "", ShapedRecipePattern.of(
            Map.of(
                'I', Ingredient.of(Items.IRON_INGOT),
                'R', Ingredient.of(Items.REDSTONE),
                'E', Ingredient.of(Items.ENDER_PEARL),
                'D', Ingredient.of(Items.DIAMOND)
            ),
            "IRI",
            "EDE",
            "IRI"
        ), SLItems.LINK_CONFIGURATOR.toStack());

        /// 综合升级（红石主题）
        /// 铁综合 (×2)：3铁锭 + 4红石
        shaped(output, "iron_", "", ShapedRecipePattern.of(
            Map.of('I', Ingredient.of(Items.IRON_INGOT), 'R', Ingredient.of(Items.REDSTONE)),
            " R ",
            "IRI",
            " R "
        ), SLItems.COMBINED_UPGRADE_IRON.toStack());
        /// 金综合 (×4)：铁综合 + 4金锭 + 2红石块
        shapeless(output, "", "", SLItems.COMBINED_UPGRADE_GOLDEN.toStack(),
            Ingredient.of(SLItems.COMBINED_UPGRADE_IRON.get()),
            Ingredient.of(Items.GOLD_INGOT), Ingredient.of(Items.GOLD_INGOT),
            Ingredient.of(Items.GOLD_INGOT), Ingredient.of(Items.GOLD_INGOT),
            Ingredient.of(Items.REDSTONE_BLOCK), Ingredient.of(Items.REDSTONE_BLOCK));
        /// 钻石综合 (×8)：金综合 + 4钻石 + 2红石块
        shapeless(output, "", "", SLItems.COMBINED_UPGRADE_DIAMOND.toStack(),
            Ingredient.of(SLItems.COMBINED_UPGRADE_GOLDEN.get()),
            Ingredient.of(Items.DIAMOND), Ingredient.of(Items.DIAMOND),
            Ingredient.of(Items.DIAMOND), Ingredient.of(Items.DIAMOND),
            Ingredient.of(Items.REDSTONE_BLOCK), Ingredient.of(Items.REDSTONE_BLOCK));
        /// 下界合金综合 (×16)：钻石综合 + 4下界合金锭 + 2红石块
        shapeless(output, "", "", SLItems.COMBINED_UPGRADE_NETHERITE.toStack(),
            Ingredient.of(SLItems.COMBINED_UPGRADE_DIAMOND.get()),
            Ingredient.of(Items.NETHERITE_INGOT), Ingredient.of(Items.NETHERITE_INGOT),
            Ingredient.of(Items.NETHERITE_INGOT), Ingredient.of(Items.NETHERITE_INGOT),
            Ingredient.of(Items.REDSTONE_BLOCK), Ingredient.of(Items.REDSTONE_BLOCK));
        /// 下界之星综合 (×64)：下界合金综合 + 星 + 4下界合金锭
        shaped(output, "nether_star_", "", ShapedRecipePattern.of(
            Map.of(
                'N', Ingredient.of(Items.NETHERITE_INGOT),
                'S', Ingredient.of(Items.NETHER_STAR),
                'U', Ingredient.of(SLItems.COMBINED_UPGRADE_NETHERITE.get())
            ),
            "NSN",
            "NUN",
            "NNN"
        ), SLItems.COMBINED_UPGRADE_NETHER_STAR.toStack());

        /// 过滤器
        /// 基础过滤：4铁锭 + 2纸 + 书 + 2红石
        shaped(output, "", "", ShapedRecipePattern.of(
            Map.of(
                'I', Ingredient.of(Items.IRON_INGOT),
                'P', Ingredient.of(Items.PAPER),
                'B', Ingredient.of(Items.BOOK),
                'R', Ingredient.of(Items.REDSTONE)
            ),
            "PIP",
            "RBR",
            "PIP"
        ), SLItems.COMBINED_FILTER_UPGRADE.toStack());

        /// 将已配置的过滤器恢复为干净状态。
        shapelessSingle(output, SLItems.COMBINED_FILTER_UPGRADE.toStack());
    }

    protected void shaped(RecipeOutput output, String prefix, String suffix, ShapedRecipePattern pattern, ItemStack result) {
        ResourceLocation id = StaticLogistics.asResource("shaped/" + prefix + getItemName(result.getItem()) + suffix);
        output.accept(id, new ShapedRecipe("", CraftingBookCategory.MISC, pattern, result, true), null);
    }

    protected void shapeless(RecipeOutput output, String prefix, String suffix, ItemStack result, Ingredient... ingredients) {
        ResourceLocation id = StaticLogistics.asResource("shapeless/" + prefix + getItemName(result.getItem()) + suffix);
        NonNullList<Ingredient> zingredients = NonNullList.of(Ingredient.EMPTY, ingredients);
        output.accept(id, new ShapelessRecipe("", CraftingBookCategory.MISC, result, zingredients), null);
    }

    /// 单物品无序配方：用于清理已配置的过滤器
    protected void shapelessSingle(RecipeOutput output, ItemStack result) {
        ResourceLocation id = StaticLogistics.asResource("shapeless/clear_" + getItemName(result.getItem()));
        NonNullList<Ingredient> list = NonNullList.of(Ingredient.EMPTY, Ingredient.of(result.getItem()));
        output.accept(id, new ShapelessRecipe("", CraftingBookCategory.MISC, result, list), null);
    }
}
