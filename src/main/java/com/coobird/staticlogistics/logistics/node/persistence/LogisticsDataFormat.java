package com.coobird.staticlogistics.logistics.node.persistence;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/// 校验物流存档及节点配置的格式版本。
public final class LogisticsDataFormat {
    public static final int CURRENT_STORAGE_SCHEMA_VERSION = 4;
    public static final int CURRENT_FACE_SCHEMA_VERSION = 4;

    private LogisticsDataFormat() {
    }

    public static void validateStorage(CompoundTag tag) {
        requireVersion(tag, CURRENT_STORAGE_SCHEMA_VERSION);
        if (!tag.contains("face_configs", Tag.TAG_COMPOUND) || !tag.contains("container_configs", Tag.TAG_COMPOUND))
            throw new IllegalArgumentException("Invalid logistics storage");
    }

    public static void validateFace(CompoundTag tag) {
        requireVersion(tag, CURRENT_FACE_SCHEMA_VERSION);
        if (!tag.contains(ConfigKeys.PRIORITY, Tag.TAG_LONG) || !tag.contains(ConfigKeys.KEEP_STOCK, Tag.TAG_LONG)
            || !tag.contains("redstone", Tag.TAG_STRING)
            || !tag.contains(ConfigKeys.SELECTED_TYPES, Tag.TAG_LIST))
            throw new IllegalArgumentException("Invalid channel configuration");
    }

    private static void requireVersion(CompoundTag tag, int expected) {
        if (!tag.contains(ConfigKeys.SCHEMA_VERSION, Tag.TAG_INT) || tag.getInt(ConfigKeys.SCHEMA_VERSION) != expected)
            throw new IllegalStateException("Unsupported logistics format");
    }
}
