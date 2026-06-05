package com.coobird.staticlogistics.filter.core;

import com.coobird.staticlogistics.filter.NbtMatchMode;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/**
 * NBT 过滤器 —— 按模板物品的 NBT / DataComponent 匹配目标。
 * <p>
 * 两种模式：
 * <ul>
 *   <li>{@link NbtMatchMode#PARTIAL 部分匹配}：目标必须包含模板的所有组件（值相等），可包含额外组件。</li>
 *   <li>{@link NbtMatchMode#FULL 完全匹配}：目标与模板的所有非耐久组件必须完全一致。</li>
 * </ul>
 * ignoreDamage 为 true 时，两种模式均跳过耐久值比较。
 */
public class NbtLogisticsFilter extends AbstractLogisticsFilter {
    private final ItemStack template;
    private final NbtMatchMode mode;
    private final boolean ignoreDamage;

    public NbtLogisticsFilter(ItemStack template, NbtMatchMode mode, boolean hasUpgrade, boolean ignoreDamage) {
        super(hasUpgrade);
        this.template = template;
        this.mode = mode;
        this.ignoreDamage = ignoreDamage;
    }

    @Override
    protected boolean testItem(ItemStack stack) {
        if (template.isEmpty()) return false;
        if (!ItemStack.isSameItem(stack, template)) return false;

        return switch (mode) {
            case PARTIAL -> matchesPartial(stack);
            case FULL -> matchesFull(stack);
        };
    }

    private boolean matchesPartial(ItemStack target) {
        DataComponentMap src = template.getComponents();
        DataComponentMap tgt = target.getComponents();
        for (var typed : src) {
            var type = typed.type();
            if (ignoreDamage && type == DataComponents.DAMAGE) continue;
            // 自定义数据 / 实体数据 / 方块实体 —— 序列化后很难语义匹配，跳过
            if (type == DataComponents.CUSTOM_DATA
                || type == DataComponents.ENTITY_DATA
                || type == DataComponents.BLOCK_ENTITY_DATA
                || type == DataComponents.BUCKET_ENTITY_DATA) continue;
            Object tgtVal = tgt.get(type);
            if (tgtVal == null) return false;
            if (!typed.value().equals(tgtVal)) return false;
        }
        return true;
    }

    private boolean matchesFull(ItemStack target) {
        DataComponentMap src = template.getComponents();
        DataComponentMap tgt = target.getComponents();

        // 检查模板的每个组件在目标中是否一致
        for (var typed : src) {
            var type = typed.type();
            if (ignoreDamage && type == DataComponents.DAMAGE) continue;
            if (type == DataComponents.CUSTOM_DATA
                || type == DataComponents.ENTITY_DATA
                || type == DataComponents.BLOCK_ENTITY_DATA
                || type == DataComponents.BUCKET_ENTITY_DATA) continue;
            Object tgtVal = tgt.get(type);
            if (!typed.value().equals(tgtVal)) return false;
        }
        // 检查目标是否有模板中没有的额外组件
        for (var typed : tgt) {
            var type = typed.type();
            if (ignoreDamage && type == DataComponents.DAMAGE) continue;
            if (type == DataComponents.CUSTOM_DATA
                || type == DataComponents.ENTITY_DATA
                || type == DataComponents.BLOCK_ENTITY_DATA
                || type == DataComponents.BUCKET_ENTITY_DATA) continue;
            if (src.get(type) == null) return false;
        }
        return true;
    }

    @Override
    public boolean isActive() {
        return hasUpgrade && !template.isEmpty();
    }
}
