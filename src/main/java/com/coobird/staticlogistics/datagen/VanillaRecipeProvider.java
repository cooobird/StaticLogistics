package com.coobird.staticlogistics.datagen;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.registry.SLItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.common.Tags;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class VanillaRecipeProvider extends RecipeProvider {

    public VanillaRecipeProvider(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> provider) {
        super(packOutput, provider);
    }

    @Override
    protected void buildRecipes(RecipeOutput output, HolderLookup.Provider holderLookup) {
        // 1. 连接配置器
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

        // 铁速度 (倍率2) 需要 2 红石
        shaped(output, "iron_", "", ShapedRecipePattern.of(
            Map.of('I', Ingredient.of(Items.IRON_INGOT), 'R', Ingredient.of(Items.REDSTONE)),
            " R ",
            "IRI",
            " R "
        ), SLItems.SPEED_UPGRADE_IRON.toStack());
        // 金速度 (倍率3) 需要铁速度 + 3 金锭
        shapeless(output, "", "", SLItems.SPEED_UPGRADE_GOLD.toStack(),
            Ingredient.of(SLItems.SPEED_UPGRADE_IRON.get()),
            Ingredient.of(Items.GOLD_INGOT), Ingredient.of(Items.GOLD_INGOT), Ingredient.of(Items.GOLD_INGOT));
        // 钻石速度 (倍率5) 需要金速度 + 5 钻石
        shapeless(output, "", "", SLItems.SPEED_UPGRADE_DIAMOND.toStack(),
            Ingredient.of(SLItems.SPEED_UPGRADE_GOLD.get()),
            Ingredient.of(Items.DIAMOND), Ingredient.of(Items.DIAMOND), Ingredient.of(Items.DIAMOND),
            Ingredient.of(Items.DIAMOND), Ingredient.of(Items.DIAMOND));
        // 下界合金速度 (倍率8) 需要钻石速度 + 8 下界合金锭
        shapeless(output, "", "", SLItems.SPEED_UPGRADE_NETHERITE.toStack(),
            Ingredient.of(SLItems.SPEED_UPGRADE_DIAMOND.get()),
            Ingredient.of(Items.NETHERITE_INGOT), Ingredient.of(Items.NETHERITE_INGOT),
            Ingredient.of(Items.NETHERITE_INGOT), Ingredient.of(Items.NETHERITE_INGOT),
            Ingredient.of(Items.NETHERITE_INGOT), Ingredient.of(Items.NETHERITE_INGOT),
            Ingredient.of(Items.NETHERITE_INGOT), Ingredient.of(Items.NETHERITE_INGOT));
        // 下界之星速度 需要下界合金速度 + 下界之星 + 4 下界合金锭 (原有形状配方)
        shaped(output, "nether_star_", "", ShapedRecipePattern.of(
            Map.of(
                'N', Ingredient.of(Items.NETHERITE_INGOT),
                'S', Ingredient.of(Items.NETHER_STAR),
                'U', Ingredient.of(SLItems.SPEED_UPGRADE_NETHERITE.get())
            ),
            "NSN",
            "NUN",
            "NNN"
        ), SLItems.SPEED_UPGRADE_NETHER_STAR.toStack());

        // 铁范围 (倍率2) 需要 2 末影珍珠
        shaped(output, "iron_", "", ShapedRecipePattern.of(
            Map.of('I', Ingredient.of(Items.IRON_INGOT), 'E', Ingredient.of(Items.ENDER_PEARL)),
            " E ",
            "IEI",
            " E "
        ), SLItems.RANGE_UPGRADE_IRON.toStack());
        // 金范围 (倍率3) 需要铁范围 + 3 金锭
        shapeless(output, "", "", SLItems.RANGE_UPGRADE_GOLD.toStack(),
            Ingredient.of(SLItems.RANGE_UPGRADE_IRON.get()),
            Ingredient.of(Items.GOLD_INGOT), Ingredient.of(Items.GOLD_INGOT), Ingredient.of(Items.GOLD_INGOT));
        // 钻石范围 (倍率5) 需要金范围 + 5 钻石
        shapeless(output, "", "", SLItems.RANGE_UPGRADE_DIAMOND.toStack(),
            Ingredient.of(SLItems.RANGE_UPGRADE_GOLD.get()),
            Ingredient.of(Items.DIAMOND), Ingredient.of(Items.DIAMOND), Ingredient.of(Items.DIAMOND),
            Ingredient.of(Items.DIAMOND), Ingredient.of(Items.DIAMOND));
        // 下界合金范围 (倍率8) 需要钻石范围 + 8 下界合金锭
        shapeless(output, "", "", SLItems.RANGE_UPGRADE_NETHERITE.toStack(),
            Ingredient.of(SLItems.RANGE_UPGRADE_DIAMOND.get()),
            Ingredient.of(Items.NETHERITE_INGOT), Ingredient.of(Items.NETHERITE_INGOT),
            Ingredient.of(Items.NETHERITE_INGOT), Ingredient.of(Items.NETHERITE_INGOT),
            Ingredient.of(Items.NETHERITE_INGOT), Ingredient.of(Items.NETHERITE_INGOT),
            Ingredient.of(Items.NETHERITE_INGOT), Ingredient.of(Items.NETHERITE_INGOT));
        // 下界之星范围 需要下界合金范围 + 下界之星 + 4 下界合金锭
        shaped(output, "nether_star_", "", ShapedRecipePattern.of(
            Map.of(
                'N', Ingredient.of(Items.NETHERITE_INGOT),
                'S', Ingredient.of(Items.NETHER_STAR),
                'U', Ingredient.of(SLItems.RANGE_UPGRADE_NETHERITE.get())
            ),
            "NSN",
            "NUN",
            "NNN"
        ), SLItems.RANGE_UPGRADE_NETHER_STAR.toStack());

        // 铁堆叠 (倍率2) 需要 1 箱子 + 2 铁锭
        shaped(output, "iron_", "", ShapedRecipePattern.of(
            Map.of('I', Ingredient.of(Items.IRON_INGOT), 'C', Ingredient.of(Tags.Items.CHESTS)),
            " I ",
            "ICI",
            " I "
        ), SLItems.STACK_UPGRADE_IRON.toStack());
        // 金堆叠 (倍率3) 需要铁堆叠 + 3 金锭
        shapeless(output, "", "", SLItems.STACK_UPGRADE_GOLD.toStack(),
            Ingredient.of(SLItems.STACK_UPGRADE_IRON.get()),
            Ingredient.of(Items.GOLD_INGOT), Ingredient.of(Items.GOLD_INGOT), Ingredient.of(Items.GOLD_INGOT));
        // 钻石堆叠 (倍率5) 需要金堆叠 + 5 钻石
        shapeless(output, "", "", SLItems.STACK_UPGRADE_DIAMOND.toStack(),
            Ingredient.of(SLItems.STACK_UPGRADE_GOLD.get()),
            Ingredient.of(Items.DIAMOND), Ingredient.of(Items.DIAMOND), Ingredient.of(Items.DIAMOND),
            Ingredient.of(Items.DIAMOND), Ingredient.of(Items.DIAMOND));
        // 下界合金堆叠 (倍率8) 需要钻石堆叠 + 8 下界合金锭
        shapeless(output, "", "", SLItems.STACK_UPGRADE_NETHERITE.toStack(),
            Ingredient.of(SLItems.STACK_UPGRADE_DIAMOND.get()),
            Ingredient.of(Items.NETHERITE_INGOT), Ingredient.of(Items.NETHERITE_INGOT),
            Ingredient.of(Items.NETHERITE_INGOT), Ingredient.of(Items.NETHERITE_INGOT),
            Ingredient.of(Items.NETHERITE_INGOT), Ingredient.of(Items.NETHERITE_INGOT),
            Ingredient.of(Items.NETHERITE_INGOT), Ingredient.of(Items.NETHERITE_INGOT));
        // 下界之星堆叠 需要下界合金堆叠 + 下界之星 + 4 下界合金锭
        shaped(output, "nether_star_", "", ShapedRecipePattern.of(
            Map.of(
                'N', Ingredient.of(Items.NETHERITE_INGOT),
                'S', Ingredient.of(Items.NETHER_STAR),
                'U', Ingredient.of(SLItems.STACK_UPGRADE_NETHERITE.get())
            ),
            "NSN",
            "NUN",
            "NNN"
        ), SLItems.STACK_UPGRADE_NETHER_STAR.toStack());

        // ==================== 维度升级 ====================
        // 保持原样
        shaped(output, "", "", ShapedRecipePattern.of(
            Map.of(
                'N', Ingredient.of(Items.NETHERITE_INGOT),
                'E', Ingredient.of(Items.ENDER_EYE),
                'S', Ingredient.of(Items.NETHER_STAR)
            ),
            "NEN",
            "ESE",
            "NEN"
        ), SLItems.DIMENSION_UPGRADE.toStack());

        // 基础过滤升级
        shaped(output, "", "", ShapedRecipePattern.of(
            Map.of(
                'I', Ingredient.of(Items.IRON_INGOT),
                'P', Ingredient.of(Items.PAPER),
                'R', Ingredient.of(Items.REDSTONE)
            ),
            "PIP",
            "IRI",
            "PIP"
        ), SLItems.BASIC_FILTER_UPGRADE.toStack());

        // 标签过滤升级：基础过滤 + 命名牌 + 2 纸
        shapeless(output, "", "", SLItems.TAG_FILTER_UPGRADE.toStack(),
            Ingredient.of(SLItems.BASIC_FILTER_UPGRADE.get()),
            Ingredient.of(Items.NAME_TAG),
            Ingredient.of(Items.PAPER), Ingredient.of(Items.PAPER));

        // NBT 过滤升级：基础过滤 + 钻石 + 书
        shapeless(output, "", "", SLItems.NBT_FILTER_UPGRADE.toStack(),
            Ingredient.of(SLItems.BASIC_FILTER_UPGRADE.get()),
            Ingredient.of(Items.DIAMOND),
            Ingredient.of(Items.BOOK));
    }

    protected void shaped(RecipeOutput output, String prefix, String suffix, ShapedRecipePattern pattern, ItemStack result) {
        ResourceLocation id = Staticlogistics.asResource("shaped/" + prefix + getItemName(result.getItem()) + suffix);
        output.accept(id, new ShapedRecipe("", CraftingBookCategory.MISC, pattern, result, true), null);
    }

    protected void shapeless(RecipeOutput output, String prefix, String suffix, ItemStack result, Ingredient... ingredients) {
        ResourceLocation id = Staticlogistics.asResource("shapeless/" + prefix + getItemName(result.getItem()) + suffix);
        NonNullList<Ingredient> zingredients = NonNullList.of(Ingredient.EMPTY, ingredients);
        output.accept(id, new ShapelessRecipe("", CraftingBookCategory.MISC, result, zingredients), null);
    }
}