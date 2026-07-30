package com.coobird.staticlogistics.transfer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * 传输类型选择集合。
 *
 * <p>新格式使用资源类型 ID 持久化，不再受旧 int 掩码的 32 位上限影响。
 * 旧掩码仍用于读取老数据和给旧 UI 同步槽提供兼容值。
 */
public final class TransferTypeSelection {
    /**
     * 单个面允许选择的最大资源类型数量。
     */
    public static final int MAX_SELECTED_TYPES = 64;

    private TransferTypeSelection() {
    }

    public static List<ResourceLocation> sanitize(Collection<ResourceLocation> ids) {
        Objects.requireNonNull(ids, "Transfer type ids must not be null");
        if (ids.size() > MAX_SELECTED_TYPES) {
            throw new IllegalArgumentException("Too many selected transfer types: " + ids.size());
        }
        LinkedHashSet<ResourceLocation> unique = new LinkedHashSet<>();
        for (ResourceLocation id : ids) {
            unique.add(Objects.requireNonNull(id, "Transfer type id must not be null"));
            if (unique.size() > MAX_SELECTED_TYPES) {
                throw new IllegalArgumentException("Too many selected transfer types: " + unique.size());
            }
        }
        return List.copyOf(unique);
    }

    public static List<ResourceLocation> fromMask(int mask, Collection<LogisticsResource<?>> activeTypes) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (LogisticsResource<?> type : activeTypes) {
            if (TransferTypeMask.isSelected(mask, type)) {
                ids.add(type.typeId());
            }
        }
        return ids;
    }

    /**
     * 将现代 ID 与仍在物品上的旧位合并；适配器恢复后可在首次读取时重新得到 ID。
     */
    public static List<ResourceLocation> mergeIdsWithMask(
        Collection<ResourceLocation> ids, int mask,
        Collection<LogisticsResource<?>> activeTypes
    ) {
        LinkedHashSet<ResourceLocation> merged = new LinkedHashSet<>(sanitize(ids));
        merged.addAll(fromMask(mask, activeTypes));
        return sanitize(merged);
    }

    public static int toMask(Collection<ResourceLocation> ids, Collection<LogisticsResource<?>> activeTypes) {
        Set<ResourceLocation> selected = new LinkedHashSet<>(ids);
        int mask = 0;
        for (LogisticsResource<?> type : activeTypes) {
            if (TransferTypeMask.hasLegacyBit(type) && selected.contains(type.typeId())) {
                mask |= TransferTypeMask.flag(type);
            }
        }
        return mask;
    }

    /**
     * 返回当前已注册类型占用的全部旧 int 位，用于保留尚未注册的历史位。
     */
    public static int activeLegacyMask(Collection<LogisticsResource<?>> activeTypes) {
        int mask = 0;
        for (LogisticsResource<?> type : activeTypes) {
            if (TransferTypeMask.hasLegacyBit(type)) mask |= TransferTypeMask.flag(type);
        }
        return mask;
    }

    public static boolean isSelected(Collection<ResourceLocation> ids, LogisticsResource<?> type) {
        return ids.contains(type.typeId());
    }

    public static List<LogisticsResource<?>> selectedTypes(Collection<ResourceLocation> ids, Collection<LogisticsResource<?>> activeTypes) {
        Set<ResourceLocation> selected = new LinkedHashSet<>(ids);
        return activeTypes.stream()
            .filter(type -> selected.contains(type.typeId()))
            .toList();
    }

    public static List<ResourceLocation> toggle(Collection<ResourceLocation> ids, LogisticsResource<?> type) {
        LinkedHashSet<ResourceLocation> selected = new LinkedHashSet<>(ids);
        if (!selected.remove(type.typeId())) {
            selected.add(type.typeId());
        }
        return List.copyOf(selected);
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
