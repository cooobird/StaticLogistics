package com.coobird.staticlogistics.filter.util;

import com.coobird.staticlogistics.api.filter.MatchStrategy;
import com.coobird.staticlogistics.api.type.NbtMatchMode;
import com.coobird.staticlogistics.filter.registry.ComponentMatchStrategyRegistry;
import com.coobird.staticlogistics.filter.registry.ComponentMatcherRegistry;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public final class ComponentValueMatcher {

    private ComponentValueMatcher() {
    }

    /**
     * 通用递归智能包含引擎。
     */
    public static boolean isValueContainedIn(Object source, Object target, NbtMatchMode mode) {
        if (source == null) return target == null;
        if (target == null) return false;
        if (source.equals(target)) return true;

        // 嵌套 ItemStack 特殊处理
        if (source instanceof ItemStack srcStack && target instanceof ItemStack tgtStack) {
            if (!ItemStack.isSameItem(srcStack, tgtStack)) return false;
            DataComponentMap srcComps = srcStack.getComponents();
            DataComponentMap tgtComps = tgtStack.getComponents();
            return switch (mode) {
                case FULL -> componentsFullMatch(tgtComps, srcComps, mode);
                case PARTIAL -> componentsPartialMatch(tgtComps, srcComps);
            };
        }

        // 通用 Map 比较
        if (source instanceof Map<?, ?> srcMap && target instanceof Map<?, ?> tgtMap) {
            if (mode == NbtMatchMode.FULL && srcMap.size() != tgtMap.size()) return false;
            for (var entry : srcMap.entrySet()) {
                if (!tgtMap.containsKey(entry.getKey()) ||
                    !isValueContainedIn(entry.getValue(), tgtMap.get(entry.getKey()), mode)) {
                    return false;
                }
            }
            return true;
        }

        // 通用 Collection 比较
        if (source instanceof Collection<?> srcColl && target instanceof Collection<?> tgtColl) {
            if (mode == NbtMatchMode.FULL && srcColl.size() != tgtColl.size()) return false;
            for (Object elem : srcColl) {
                boolean found = false;
                for (Object tElem : tgtColl) {
                    if (isValueContainedIn(elem, tElem, mode)) {
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }
            return true;
        }

        // 查询特殊组件注册表
        return ComponentMatcherRegistry.getMatcher(source.getClass())
            .map(matcher -> matcher.test(source, target, mode))
            .orElse(source.equals(target));
    }

    /**
     * 部分匹配：检查 source 中的组件在 target 中是否都存在且匹配。
     */
    public static boolean componentsPartialMatch(DataComponentMap targetComponents, DataComponentMap sourceComponents) {
        for (TypedDataComponent<?> typedComponent : sourceComponents) {
            var type = typedComponent.type();
            var strategy = ComponentMatchStrategyRegistry.getStrategy(type);
            if (strategy == MatchStrategy.IGNORE) continue;

            Object sourceValue = typedComponent.value();
            Object targetValue = targetComponents.get(type);
            if (targetValue == null) return false;

            boolean matched = switch (strategy) {
                case EXACT -> sourceValue.equals(targetValue);
                case CONTAINS, SMART_CONTAINS -> isValueContainedIn(sourceValue, targetValue, NbtMatchMode.PARTIAL);
                default -> false;
            };
            if (!matched) return false;
        }
        return true;
    }

    /**
     * 全匹配：比较两个 DataComponentMap，要求所有非忽略组件完全一致。
     */
    public static boolean componentsFullMatch(DataComponentMap targetComponents, DataComponentMap sourceComponents, NbtMatchMode mode) {
        Set<DataComponentType<?>> allKeys = new HashSet<>();
        sourceComponents.keySet().forEach(k -> {
            if (ComponentMatchStrategyRegistry.getStrategy(k) != MatchStrategy.IGNORE) allKeys.add(k);
        });
        targetComponents.keySet().forEach(k -> {
            if (ComponentMatchStrategyRegistry.getStrategy(k) != MatchStrategy.IGNORE) allKeys.add(k);
        });

        for (DataComponentType<?> key : allKeys) {
            Object srcVal = sourceComponents.get(key);
            Object tgtVal = targetComponents.get(key);
            if (srcVal == null || tgtVal == null) return false;
            if (!isValueContainedIn(srcVal, tgtVal, mode)) return false;
        }
        return true;
    }

    /**
     * 检查陶罐纹饰的某一方向是否不匹配。
     */
    public static boolean optionalItemMatches(Optional<Item> src, Optional<Item> tgt) {
        return src.isEmpty() || (tgt.isPresent() && src.get().equals(tgt.get()));
    }
}