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
        // 连接配置器
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

        // ── 速度升级（红石主题）──
        // 铁速度 (×2)：3铁锭 + 4红石
        shaped(output, "iron_", "", ShapedRecipePattern.of(
            Map.of('I', Ingredient.of(Items.IRON_INGOT), 'R', Ingredient.of(Items.REDSTONE)),
            " R ",
            "IRI",
            " R "
        ), SLItems.SPEED_UPGRADE_IRON.toStack());
        // 金速度 (×4)：铁速度 + 4金锭 + 2红石块
        shapeless(output, "", "", SLItems.SPEED_UPGRADE_GOLD.toStack(),
            Ingredient.of(SLItems.SPEED_UPGRADE_IRON.get()),
            Ingredient.of(Items.GOLD_INGOT), Ingredient.of(Items.GOLD_INGOT),
            Ingredient.of(Items.GOLD_INGOT), Ingredient.of(Items.GOLD_INGOT),
            Ingredient.of(Items.REDSTONE_BLOCK), Ingredient.of(Items.REDSTONE_BLOCK));
        // 钻石速度 (×8)：金速度 + 4钻石 + 2红石块
        shapeless(output, "", "", SLItems.SPEED_UPGRADE_DIAMOND.toStack(),
            Ingredient.of(SLItems.SPEED_UPGRADE_GOLD.get()),
            Ingredient.of(Items.DIAMOND), Ingredient.of(Items.DIAMOND),
            Ingredient.of(Items.DIAMOND), Ingredient.of(Items.DIAMOND),
            Ingredient.of(Items.REDSTONE_BLOCK), Ingredient.of(Items.REDSTONE_BLOCK));
        // 下界合金速度 (×16)：钻石速度 + 4下界合金锭 + 2红石块
        shapeless(output, "", "", SLItems.SPEED_UPGRADE_NETHERITE.toStack(),
            Ingredient.of(SLItems.SPEED_UPGRADE_DIAMOND.get()),
            Ingredient.of(Items.NETHERITE_INGOT), Ingredient.of(Items.NETHERITE_INGOT),
            Ingredient.of(Items.NETHERITE_INGOT), Ingredient.of(Items.NETHERITE_INGOT),
            Ingredient.of(Items.REDSTONE_BLOCK), Ingredient.of(Items.REDSTONE_BLOCK));
        // 下界之星速度 (×64)：下界合金速度 + 星 + 4下界合金锭
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

        // ── 范围升级（末影主题）──
        // 铁范围 (×2)：3铁锭 + 2末影珍珠
        shaped(output, "iron_", "", ShapedRecipePattern.of(
            Map.of('I', Ingredient.of(Items.IRON_INGOT), 'E', Ingredient.of(Items.ENDER_PEARL)),
            " E ",
            "IEI",
            " E "
        ), SLItems.RANGE_UPGRADE_IRON.toStack());
        // 金范围 (×4)：铁范围 + 4金锭 + 2末影之眼
        shapeless(output, "", "", SLItems.RANGE_UPGRADE_GOLD.toStack(),
            Ingredient.of(SLItems.RANGE_UPGRADE_IRON.get()),
            Ingredient.of(Items.GOLD_INGOT), Ingredient.of(Items.GOLD_INGOT),
            Ingredient.of(Items.GOLD_INGOT), Ingredient.of(Items.GOLD_INGOT),
            Ingredient.of(Items.ENDER_EYE), Ingredient.of(Items.ENDER_EYE));
        // 钻石范围 (×8)：金范围 + 4钻石 + 2末影之眼
        shapeless(output, "", "", SLItems.RANGE_UPGRADE_DIAMOND.toStack(),
            Ingredient.of(SLItems.RANGE_UPGRADE_GOLD.get()),
            Ingredient.of(Items.DIAMOND), Ingredient.of(Items.DIAMOND),
            Ingredient.of(Items.DIAMOND), Ingredient.of(Items.DIAMOND),
            Ingredient.of(Items.ENDER_EYE), Ingredient.of(Items.ENDER_EYE));
        // 下界合金范围 (×16)：钻石范围 + 4下界合金锭 + 2末影之眼
        shapeless(output, "", "", SLItems.RANGE_UPGRADE_NETHERITE.toStack(),
            Ingredient.of(SLItems.RANGE_UPGRADE_DIAMOND.get()),
            Ingredient.of(Items.NETHERITE_INGOT), Ingredient.of(Items.NETHERITE_INGOT),
            Ingredient.of(Items.NETHERITE_INGOT), Ingredient.of(Items.NETHERITE_INGOT),
            Ingredient.of(Items.ENDER_EYE), Ingredient.of(Items.ENDER_EYE));
        // 下界之星范围 (×64)：下界合金范围 + 星 + 4下界合金锭
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

        // ── 堆叠升级（存储主题）──
        // 铁堆叠 (×2)：2铁锭 + 箱子
        shaped(output, "iron_", "", ShapedRecipePattern.of(
            Map.of('I', Ingredient.of(Items.IRON_INGOT), 'C', Ingredient.of(Tags.Items.CHESTS)),
            " I ",
            "ICI",
            " I "
        ), SLItems.STACK_UPGRADE_IRON.toStack());
        // 金堆叠 (×4)：铁堆叠 + 4金锭 + 2箱子
        shapeless(output, "", "", SLItems.STACK_UPGRADE_GOLD.toStack(),
            Ingredient.of(SLItems.STACK_UPGRADE_IRON.get()),
            Ingredient.of(Items.GOLD_INGOT), Ingredient.of(Items.GOLD_INGOT),
            Ingredient.of(Items.GOLD_INGOT), Ingredient.of(Items.GOLD_INGOT),
            Ingredient.of(Tags.Items.CHESTS), Ingredient.of(Tags.Items.CHESTS));
        // 钻石堆叠 (×8)：金堆叠 + 4钻石 + 2潜影壳
        shapeless(output, "", "", SLItems.STACK_UPGRADE_DIAMOND.toStack(),
            Ingredient.of(SLItems.STACK_UPGRADE_GOLD.get()),
            Ingredient.of(Items.DIAMOND), Ingredient.of(Items.DIAMOND),
            Ingredient.of(Items.DIAMOND), Ingredient.of(Items.DIAMOND),
            Ingredient.of(Items.SHULKER_SHELL), Ingredient.of(Items.SHULKER_SHELL));
        // 下界合金堆叠 (×16)：钻石堆叠 + 4下界合金锭 + 2潜影壳
        shapeless(output, "", "", SLItems.STACK_UPGRADE_NETHERITE.toStack(),
            Ingredient.of(SLItems.STACK_UPGRADE_DIAMOND.get()),
            Ingredient.of(Items.NETHERITE_INGOT), Ingredient.of(Items.NETHERITE_INGOT),
            Ingredient.of(Items.NETHERITE_INGOT), Ingredient.of(Items.NETHERITE_INGOT),
            Ingredient.of(Items.SHULKER_SHELL), Ingredient.of(Items.SHULKER_SHELL));
        // 下界之星堆叠 (×64)：下界合金堆叠 + 星 + 4下界合金锭
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

        // ── 维度升级 ──
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

        // ── 过滤器 ──
        // 基础过滤：4铁锭 + 2纸 + 书 + 2红石
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
        ), SLItems.BASIC_FILTER_UPGRADE.toStack());

        // 标签过滤：基础过滤 + 2纸 + 书（不再用命名牌）
        shapeless(output, "", "", SLItems.TAG_FILTER_UPGRADE.toStack(),
            Ingredient.of(SLItems.BASIC_FILTER_UPGRADE.get()),
            Ingredient.of(Items.PAPER), Ingredient.of(Items.PAPER),
            Ingredient.of(Items.BOOK));

        // NBT 过滤：基础过滤 + 钻石 + 书 + 红石
        shapeless(output, "", "", SLItems.NBT_FILTER_UPGRADE.toStack(),
            Ingredient.of(SLItems.BASIC_FILTER_UPGRADE.get()),
            Ingredient.of(Items.DIAMOND),
            Ingredient.of(Items.BOOK),
            Ingredient.of(Items.REDSTONE));

        // ── 过滤器 NBT 清理（已配置 → 干净）──
        shapelessSingle(output, SLItems.BASIC_FILTER_UPGRADE.toStack());
        shapelessSingle(output, SLItems.TAG_FILTER_UPGRADE.toStack());
        shapelessSingle(output, SLItems.NBT_FILTER_UPGRADE.toStack());
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

    /**
     * 单物品无序配方：用于清理已配置的过滤器
     */
    protected void shapelessSingle(RecipeOutput output, ItemStack result) {
        ResourceLocation id = Staticlogistics.asResource("shapeless/clear_" + getItemName(result.getItem()));
        NonNullList<Ingredient> list = NonNullList.of(Ingredient.EMPTY, Ingredient.of(result.getItem()));
        output.accept(id, new ShapelessRecipe("", CraftingBookCategory.MISC, result, list), null);
    }
}
