package com.coobird.staticlogistics.filter.core;

import com.coobird.staticlogistics.api.type.NbtMatchMode;
import com.coobird.staticlogistics.filter.registry.ComponentMatcherRegistry;
import com.coobird.staticlogistics.filter.util.ComponentValueMatcher;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.*;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.PotDecorations;

import java.util.ArrayList;
import java.util.List;

public class NbtLogisticsFilter extends AbstractLogisticsFilter {
    private final ItemStack template;
    private final NbtMatchMode mode;
    private final boolean ignoreDamage;

    // 匹配结果缓存：传输时同种物品频繁被检查，缓存避免反复做 NBT 序列化
    private int lastItemHash;
    private boolean lastItemResult;

    public NbtLogisticsFilter(ItemStack template, NbtMatchMode mode, boolean hasUpgrade, boolean ignoreDamage) {
        super(hasUpgrade);
        this.template = template;
        this.mode = mode;
        this.ignoreDamage = ignoreDamage;
        this.lastItemHash = 0;
    }


    @Override
    protected boolean testItem(ItemStack stack) {
        if (template.isEmpty()) return false;
        if (!ItemStack.isSameItem(stack, template)) return false;
        // 同 hash 说明同种同 NBT，命中缓存直接返回
        int h = stack.hashCode();
        if (h == lastItemHash) return lastItemResult;
        boolean result = switch (mode) {
            case PARTIAL -> matchesPartialMode(stack, template, ignoreDamage);
            case FULL -> matchesFullMode(stack, template, ignoreDamage);
        };
        lastItemHash = h;
        lastItemResult = result;
        return result;
    }

    private static DataComponentMap withoutDamage(DataComponentMap original) {
        if (!original.has(DataComponents.DAMAGE)) {
            return original;
        }
        return original.filter(type -> type != DataComponents.DAMAGE);
    }

    private static boolean matchesFullMode(ItemStack target, ItemStack source, boolean ignoreDamage) {
        var targetComponents = target.getComponents();
        var sourceComponents = source.getComponents();
        if (ignoreDamage) {
            targetComponents = withoutDamage(targetComponents);
            sourceComponents = withoutDamage(sourceComponents);
        }
        return ComponentValueMatcher.componentsFullMatch(targetComponents, sourceComponents, NbtMatchMode.FULL);
    }

    private static boolean matchesPartialMode(ItemStack target, ItemStack source, boolean ignoreDamage) {
        var targetComponents = target.getComponents();
        var sourceComponents = source.getComponents();
        if (ignoreDamage) {
            targetComponents = withoutDamage(targetComponents);
            sourceComponents = withoutDamage(sourceComponents);
        }
        return ComponentValueMatcher.componentsPartialMatch(targetComponents, sourceComponents);
    }

    @Override
    public boolean isActive() {
        return hasUpgrade && !template.isEmpty();
    }

    static {
        // 附魔：模板的附魔要求目标必须完全包含，且等级一致
        ComponentMatcherRegistry.register(ItemEnchantments.class,
            (s, t, mode) -> ComponentValueMatcher.isValueContainedIn(
                s.entrySet(), t.entrySet(), mode
            ));

        // 属性修饰符：相同逻辑
        ComponentMatcherRegistry.register(ItemAttributeModifiers.class,
            (s, t, mode) -> ComponentValueMatcher.isValueContainedIn(
                s.modifiers().stream().map(ItemAttributeModifiers.Entry::modifier).toList(),
                t.modifiers().stream().map(ItemAttributeModifiers.Entry::modifier).toList(),
                mode
            ));

        // 药水效果
        ComponentMatcherRegistry.register(PotionContents.class,
            (s, t, mode) -> {
                if (s.potion().isPresent()) {
                    if (t.potion().isEmpty() || !s.potion().get().equals(t.potion().get())) {
                        return false;
                    }
                }
                return ComponentValueMatcher.isValueContainedIn(s.customEffects(), t.customEffects(), mode);
            });

        // 迷之炖菜
        ComponentMatcherRegistry.register(SuspiciousStewEffects.class,
            (s, t, mode) -> ComponentValueMatcher.isValueContainedIn(s.effects(), t.effects(), mode));

        // 旗帜图案
        ComponentMatcherRegistry.register(BannerPatternLayers.class,
            (s, t, mode) -> ComponentValueMatcher.isValueContainedIn(s.layers(), t.layers(), mode));

        // 弩装填物
        ComponentMatcherRegistry.register(ChargedProjectiles.class,
            (s, t, mode) -> ComponentValueMatcher.isValueContainedIn(s.getItems(), t.getItems(), mode));

        // 陶罐纹饰
        ComponentMatcherRegistry.register(PotDecorations.class,
            (s, t, mode) ->
                ComponentValueMatcher.optionalItemMatches(s.back(), t.back()) &&
                    ComponentValueMatcher.optionalItemMatches(s.left(), t.left()) &&
                    ComponentValueMatcher.optionalItemMatches(s.right(), t.right()) &&
                    ComponentValueMatcher.optionalItemMatches(s.front(), t.front())
        );

        // 烟花火箭
        ComponentMatcherRegistry.register(Fireworks.class,
            (s, t, mode) -> {
                if (s.flightDuration() != t.flightDuration()) return false;
                return ComponentValueMatcher.isValueContainedIn(s.explosions(), t.explosions(), mode);
            });

        // 烟火之星
        ComponentMatcherRegistry.register(FireworkExplosion.class,
            (s, t, mode) -> {
                if (s.shape() != t.shape()) return false;
                if (!ComponentValueMatcher.isValueContainedIn(s.colors(), t.colors(), mode)) return false;
                if (!ComponentValueMatcher.isValueContainedIn(s.fadeColors(), t.fadeColors(), mode)) return false;
                return s.hasTrail() == t.hasTrail() && s.hasTwinkle() == t.hasTwinkle();
            });

        // 收纳袋内容
        ComponentMatcherRegistry.register(BundleContents.class,
            (s, t, mode) -> ComponentValueMatcher.isValueContainedIn(s.items(), t.items(), mode));

        // 容器内容（潜影盒等）
        ComponentMatcherRegistry.register(ItemContainerContents.class,
            (s, t, mode) -> {
                List<ItemStack> sItems = new ArrayList<>();
                s.nonEmptyItemsCopy().forEach(sItems::add);
                List<ItemStack> tItems = new ArrayList<>();
                t.nonEmptyItemsCopy().forEach(tItems::add);
                return ComponentValueMatcher.isValueContainedIn(sItems, tItems, mode);
            });
    }
}