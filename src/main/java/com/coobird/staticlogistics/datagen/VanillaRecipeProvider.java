package com.coobird.staticlogistics.datagen;

import com.coobird.staticlogistics.registry.SLItems;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.Tags;

import java.util.function.Consumer;

public class VanillaRecipeProvider extends RecipeProvider {
    public VanillaRecipeProvider(PackOutput output) {
        super(output);
    }

    private static Ingredient tagIngredient(TagKey<Item> tag) {
        return Ingredient.of(tag);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> writer) {
        // 连接配置器
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, SLItems.LINK_CONFIGURATOR.get())
            .pattern("IRI")
            .pattern("EDE")
            .pattern("IRI")
            .define('I', Items.IRON_INGOT)
            .define('R', Items.REDSTONE)
            .define('E', Items.ENDER_PEARL)
            .define('D', Items.DIAMOND)
            .unlockedBy("has_diamond", has(Items.DIAMOND))
            .save(writer);

        // 速度升级
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, SLItems.SPEED_UPGRADE_IRON.get())
            .pattern(" R ")
            .pattern("IRI")
            .pattern(" R ")
            .define('I', Items.IRON_INGOT)
            .define('R', Items.REDSTONE)
            .unlockedBy("has_iron", has(Items.IRON_INGOT))
            .save(writer);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, SLItems.SPEED_UPGRADE_GOLD.get())
            .requires(SLItems.SPEED_UPGRADE_IRON.get())
            .requires(Items.GOLD_INGOT, 4)
            .requires(Items.REDSTONE_BLOCK, 2)
            .unlockedBy("has_gold", has(Items.GOLD_INGOT))
            .save(writer);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, SLItems.SPEED_UPGRADE_DIAMOND.get())
            .requires(SLItems.SPEED_UPGRADE_GOLD.get())
            .requires(Items.DIAMOND, 4)
            .requires(Items.REDSTONE_BLOCK, 2)
            .unlockedBy("has_diamond", has(Items.DIAMOND))
            .save(writer);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, SLItems.SPEED_UPGRADE_NETHERITE.get())
            .requires(SLItems.SPEED_UPGRADE_DIAMOND.get())
            .requires(Items.NETHERITE_INGOT, 4)
            .requires(Items.REDSTONE_BLOCK, 2)
            .unlockedBy("has_netherite", has(Items.NETHERITE_INGOT))
            .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, SLItems.SPEED_UPGRADE_NETHER_STAR.get())
            .pattern("NSN")
            .pattern("NUN")
            .pattern("NNN")
            .define('N', Items.NETHERITE_INGOT)
            .define('S', Items.NETHER_STAR)
            .define('U', SLItems.SPEED_UPGRADE_NETHERITE.get())
            .unlockedBy("has_nether_star", has(Items.NETHER_STAR))
            .save(writer);

        // 范围升级
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, SLItems.RANGE_UPGRADE_IRON.get())
            .pattern(" E ")
            .pattern("IEI")
            .pattern(" E ")
            .define('I', Items.IRON_INGOT)
            .define('E', Items.ENDER_PEARL)
            .unlockedBy("has_ender_pearl", has(Items.ENDER_PEARL))
            .save(writer);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, SLItems.RANGE_UPGRADE_GOLD.get())
            .requires(SLItems.RANGE_UPGRADE_IRON.get())
            .requires(Items.GOLD_INGOT, 4)
            .requires(Items.ENDER_EYE, 2)
            .unlockedBy("has_gold", has(Items.GOLD_INGOT))
            .save(writer);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, SLItems.RANGE_UPGRADE_DIAMOND.get())
            .requires(SLItems.RANGE_UPGRADE_GOLD.get())
            .requires(Items.DIAMOND, 4)
            .requires(Items.ENDER_EYE, 2)
            .unlockedBy("has_diamond", has(Items.DIAMOND))
            .save(writer);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, SLItems.RANGE_UPGRADE_NETHERITE.get())
            .requires(SLItems.RANGE_UPGRADE_DIAMOND.get())
            .requires(Items.NETHERITE_INGOT, 4)
            .requires(Items.ENDER_EYE, 2)
            .unlockedBy("has_netherite", has(Items.NETHERITE_INGOT))
            .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, SLItems.RANGE_UPGRADE_NETHER_STAR.get())
            .pattern("NSN")
            .pattern("NUN")
            .pattern("NNN")
            .define('N', Items.NETHERITE_INGOT)
            .define('S', Items.NETHER_STAR)
            .define('U', SLItems.RANGE_UPGRADE_NETHERITE.get())
            .unlockedBy("has_nether_star", has(Items.NETHER_STAR))
            .save(writer);

        // 堆叠升级
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, SLItems.STACK_UPGRADE_IRON.get())
            .pattern(" I ")
            .pattern("ICI")
            .pattern(" I ")
            .define('I', Items.IRON_INGOT)
            .define('C', tagIngredient(Tags.Items.CHESTS))
            .unlockedBy("has_iron", has(Items.IRON_INGOT))
            .save(writer);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, SLItems.STACK_UPGRADE_GOLD.get())
            .requires(SLItems.STACK_UPGRADE_IRON.get())
            .requires(Items.GOLD_INGOT, 4)
            .requires(tagIngredient(Tags.Items.CHESTS), 2)
            .unlockedBy("has_gold", has(Items.GOLD_INGOT))
            .save(writer);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, SLItems.STACK_UPGRADE_DIAMOND.get())
            .requires(SLItems.STACK_UPGRADE_GOLD.get())
            .requires(Items.DIAMOND, 4)
            .requires(Items.SHULKER_SHELL, 2)
            .unlockedBy("has_diamond", has(Items.DIAMOND))
            .save(writer);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, SLItems.STACK_UPGRADE_NETHERITE.get())
            .requires(SLItems.STACK_UPGRADE_DIAMOND.get())
            .requires(Items.NETHERITE_INGOT, 4)
            .requires(Items.SHULKER_SHELL, 2)
            .unlockedBy("has_netherite", has(Items.NETHERITE_INGOT))
            .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, SLItems.STACK_UPGRADE_NETHER_STAR.get())
            .pattern("NSN")
            .pattern("NUN")
            .pattern("NNN")
            .define('N', Items.NETHERITE_INGOT)
            .define('S', Items.NETHER_STAR)
            .define('U', SLItems.STACK_UPGRADE_NETHERITE.get())
            .unlockedBy("has_nether_star", has(Items.NETHER_STAR))
            .save(writer);

        // 维度升级
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, SLItems.DIMENSION_UPGRADE.get())
            .pattern("NEN")
            .pattern("ESE")
            .pattern("NEN")
            .define('N', Items.NETHERITE_INGOT)
            .define('E', Items.ENDER_EYE)
            .define('S', Items.NETHER_STAR)
            .unlockedBy("has_nether_star", has(Items.NETHER_STAR))
            .save(writer);

        // 过滤器
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, SLItems.BASIC_FILTER_UPGRADE.get())
            .pattern("PIP")
            .pattern("RBR")
            .pattern("PIP")
            .define('I', Items.IRON_INGOT)
            .define('P', Items.PAPER)
            .define('B', Items.BOOK)
            .define('R', Items.REDSTONE)
            .unlockedBy("has_iron", has(Items.IRON_INGOT))
            .save(writer);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, SLItems.TAG_FILTER_UPGRADE.get())
            .requires(SLItems.BASIC_FILTER_UPGRADE.get())
            .requires(Items.PAPER, 2)
            .requires(Items.BOOK)
            .unlockedBy("has_basic_filter", has(SLItems.BASIC_FILTER_UPGRADE.get()))
            .save(writer);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, SLItems.NBT_FILTER_UPGRADE.get())
            .requires(SLItems.BASIC_FILTER_UPGRADE.get())
            .requires(Items.DIAMOND)
            .requires(Items.BOOK)
            .requires(Items.REDSTONE)
            .unlockedBy("has_basic_filter", has(SLItems.BASIC_FILTER_UPGRADE.get()))
            .save(writer);

        // 蓝图
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, SLItems.BLUEPRINT.get())
            .pattern("PPP")
            .pattern("ILI")
            .define('P', Items.PAPER)
            .define('I', Items.IRON_INGOT)
            .define('L', Items.LAPIS_LAZULI)
            .unlockedBy("has_paper", has(Items.PAPER))
            .save(writer);

        // 过滤器清理
        if (SLItems.BASIC_FILTER_UPGRADE.getId() != null) {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, SLItems.BASIC_FILTER_UPGRADE.get())
                .requires(SLItems.BASIC_FILTER_UPGRADE.get())
                .unlockedBy("has_basic_filter", has(SLItems.BASIC_FILTER_UPGRADE.get()))
                .save(writer, new ResourceLocation(SLItems.BASIC_FILTER_UPGRADE.getId().getNamespace(),
                    "clear_" + SLItems.BASIC_FILTER_UPGRADE.getId().getPath()));
        }

        if (SLItems.TAG_FILTER_UPGRADE.getId() != null) {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, SLItems.TAG_FILTER_UPGRADE.get())
                .requires(SLItems.TAG_FILTER_UPGRADE.get())
                .unlockedBy("has_tag_filter", has(SLItems.TAG_FILTER_UPGRADE.get()))
                .save(writer, new ResourceLocation(SLItems.TAG_FILTER_UPGRADE.getId().getNamespace(),
                    "clear_" + SLItems.TAG_FILTER_UPGRADE.getId().getPath()));
        }

        if (SLItems.NBT_FILTER_UPGRADE.getId() != null) {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, SLItems.NBT_FILTER_UPGRADE.get())
                .requires(SLItems.NBT_FILTER_UPGRADE.get())
                .unlockedBy("has_nbt_filter", has(SLItems.NBT_FILTER_UPGRADE.get()))
                .save(writer, new ResourceLocation(SLItems.NBT_FILTER_UPGRADE.getId().getNamespace(),
                    "clear_" + SLItems.NBT_FILTER_UPGRADE.getId().getPath()));
        }
    }
}
