package com.coobird.staticlogistics.transfer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/// 传输类型选择集合。
/// 新格式使用资源类型 ID 持久化，不再受旧 int 掩码的 32 位上限影响。
/// 旧掩码仍用于读取老数据和给旧 UI 同步槽提供兼容值。
public final class TransferTypeSelection {
    /// 单个面允许选择的最大资源类型数量。
    public static final int MAX_SELECTED_TYPES = 64;

    private TransferTypeSelection() {
    }

    public static List<ResourceLocation> sanitize(Collection<ResourceLocation> ids) {
        if (ids.size() > MAX_SELECTED_TYPES) {
            throw new IllegalArgumentException("Too many selected transfer types: " + ids.size());
        }
        LinkedHashSet<ResourceLocation> unique = new LinkedHashSet<>();
        for (ResourceLocation id : ids) {
            unique.add(id);
            if (unique.size() > MAX_SELECTED_TYPES) {
                throw new IllegalArgumentException("Too many selected transfer types: " + unique.size());
            }
        }
        return List.copyOf(unique);
    }

    public static List<LogisticsResource<?>> selectedTypes(Collection<ResourceLocation> ids, Collection<LogisticsResource<?>> activeTypes) {
        Set<ResourceLocation> selected = new LinkedHashSet<>(ids);
        return activeTypes.stream()
            .filter(type -> selected.contains(type.typeId()))
            .toList();
    }

    public static void writeIds(CompoundTag tag, String key, Collection<ResourceLocation> ids) {
        ListTag list = new ListTag();
        for (ResourceLocation id : sanitize(ids)) {
            list.add(StringTag.valueOf(id.toString()));
        }
        tag.put(key, list);
    }

    public static List<ResourceLocation> readIds(CompoundTag tag, String key) {
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        if (list.size() > MAX_SELECTED_TYPES) {
            throw new IllegalArgumentException("Too many selected transfer types: " + list.size());
        }
        List<ResourceLocation> ids = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null) {
                ids.add(id);
            }
        }
        return sanitize(ids);
    }
}
