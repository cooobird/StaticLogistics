package com.coobird.staticlogistics.logistics.filter;

import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/// 枚举和读取物品的原生 NBT 路径，不把 PortLib 数据组件当作 1.20 的筛选模型。
public final class NbtRuleValues {
    private NbtRuleValues() {
    }

    public static List<String> keys(ItemStack stack, boolean numericOnly) {
        List<String> result = keys(stack.getTag(), numericOnly);
        if (stack.isDamageableItem() && !result.contains("Damage")) {
            result.add("Damage");
            result.sort(String::compareTo);
        }
        return result;
    }

    static List<String> keys(CompoundTag tag, boolean numericOnly) {
        List<String> result = new ArrayList<>();
        if (tag != null) collect(tag, "", numericOnly, result);
        result.sort(String::compareTo);
        return result;
    }

    private static void collect(CompoundTag tag, String prefix, boolean numericOnly, List<String> result) {
        for (String key : tag.getAllKeys()) {
            String path = prefix + key.replace("~", "~0").replace("/", "~1").replace("|", "~2");
            Tag value = tag.get(key);
            if (!numericOnly || value instanceof NumericTag) result.add(path);
            if (value instanceof CompoundTag child) collect(child, path + "/", numericOnly, result);
        }
    }

    public static Tag get(ItemStack stack, String path) {
        if (path.equals("Damage") && stack.isDamageableItem()) return IntTag.valueOf(stack.getDamageValue());
        return get(stack.getTag(), path);
    }

    static Tag get(CompoundTag tag, String path) {
        Tag value = tag;
        for (String part : path.split("/", -1)) {
            if (!(value instanceof CompoundTag compound)) return null;
            value = compound.get(part.replace("~2", "|").replace("~1", "/").replace("~0", "~"));
        }
        return value;
    }

    static boolean matchesSelected(CompoundTag sample, CompoundTag target, String path) {
        Tag expected = get(sample, path);
        Tag actual = get(target, path);
        return expected != null && actual != null && NbtUtils.compareNbt(expected, actual, true);
    }

    static boolean matchesSelected(ItemStack sample, ItemStack target, String path) {
        Tag expected = get(sample, path);
        Tag actual = get(target, path);
        return expected != null && actual != null && NbtUtils.compareNbt(expected, actual, true);
    }

    public static boolean validPath(String path) {
        return !path.isEmpty() && !path.contains("|");
    }
}
