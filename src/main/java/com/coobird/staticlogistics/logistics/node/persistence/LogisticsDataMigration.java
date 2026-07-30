package com.coobird.staticlogistics.logistics.node.persistence;

import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.api.group.GroupKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/**
 * StaticLogistics 持久化数据的版本迁移入口。
 *
 * <p>迁移始终在原始 NBT 的副本上执行，调用方只有在全部迁移和校验成功后
 * 才能使用返回值物化运行时对象。
 */
public final class LogisticsDataMigration {
    public static final int CURRENT_STORAGE_SCHEMA_VERSION = 2;
    public static final int CURRENT_FACE_SCHEMA_VERSION = 2;
    private static final String[] LEGACY_TYPE_IDS = {
        "staticlogistics:item",
        "staticlogistics:fluid",
        "staticlogistics:energy",
        "staticlogistics:mek_chemicals",
        "staticlogistics:mek_heat",
        "staticlogistics:ars_source",
        "staticlogistics:botania_mana"
    };
    private static final int KNOWN_LEGACY_TYPE_MASK = (1 << LEGACY_TYPE_IDS.length) - 1;

    private LogisticsDataMigration() {
    }

    /**
     * 迁移结果同时保留实际执行的版本步骤，供启动日志和管理员审计使用。
     */
    public record MigrationResult(CompoundTag tag, int sourceVersion, int targetVersion,
                                  List<String> appliedSteps) {
        public MigrationResult {
            tag = tag.copy();
            appliedSteps = List.copyOf(appliedSteps);
        }

        @Override
        public CompoundTag tag() {
            return tag.copy();
        }
    }

    /**
     * 迁移并校验一个维度的完整物流 SavedData。
     */
    public static CompoundTag migrateStorage(CompoundTag source) {
        return migrateStorageWithReport(source).tag();
    }

    public static MigrationResult migrateStorageWithReport(CompoundTag source) {
        CompoundTag migrated = source.copy();
        int version = readVersion(migrated, 0, "logistics storage");
        int sourceVersion = version;
        if (version > CURRENT_STORAGE_SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported logistics storage schema version: " + version);
        }
        List<String> steps = new ArrayList<>();
        while (version < CURRENT_STORAGE_SCHEMA_VERSION) {
            switch (version) {
                case 0 -> {
                    migrateStorageV0ToV1(migrated);
                    steps.add("storage:0->1");
                    version = 1;
                }
                case 1 -> {
                    // 面地址键在首次保存时由运行时仓储重写为“完整位置:方向”。
                    migrated.putInt(ConfigKeys.SCHEMA_VERSION, 2);
                    steps.add("storage:1->2");
                    version = 2;
                }
                default -> throw new IllegalStateException(
                    "Missing logistics storage migration from version: " + version);
            }
        }

        requireCompoundIfPresent(migrated, "face_configs", "logistics storage");
        requireCompoundIfPresent(migrated, "container_configs", "logistics storage");
        // 单条面配置在加载循环中独立迁移，使损坏记录可以隔离而不阻断整个维度。
        if (!migrated.contains("face_configs")) migrated.put("face_configs", new CompoundTag());
        if (!migrated.contains("container_configs")) {
            migrated.put("container_configs", new CompoundTag());
        }
        return new MigrationResult(migrated, sourceVersion, version, steps);
    }

    /**
     * 迁移单个面配置；重复调用不会继续改变结果。
     */
    public static CompoundTag migrateFace(CompoundTag source) {
        return migrateFaceWithReport(source).tag();
    }

    public static MigrationResult migrateFaceWithReport(CompoundTag source) {
        CompoundTag migrated = source.copy();
        int version = readVersion(migrated, 1, "face config");
        if (version > CURRENT_FACE_SCHEMA_VERSION || version < 1) {
            throw new IllegalStateException("Unsupported face config schema version: " + version);
        }
        int sourceVersion = version;
        List<String> steps = new ArrayList<>();
        validateFaceShape(migrated);
        while (version < CURRENT_FACE_SCHEMA_VERSION) {
            switch (version) {
                case 1 -> {
                    migrateFaceV1ToV2(migrated);
                    steps.add("face:1->2");
                    version = 2;
                }
                default -> throw new IllegalStateException(
                    "Missing face config migration from version: " + version);
            }
        }
        // 早期 v2 过渡版可能缺少部分规范字段；只执行幂等修复，不再改变版本。
        normalizeFaceV2(migrated);
        migrated.putInt(ConfigKeys.SCHEMA_VERSION, version);
        return new MigrationResult(migrated, sourceVersion, version, steps);
    }

    private static void migrateStorageV0ToV1(CompoundTag tag) {
        tag.putInt(ConfigKeys.SCHEMA_VERSION, 1);
    }

    private static void migrateFaceV1ToV2(CompoundTag tag) {
        migrateLegacyGroups(tag);
        migrateLegacyTypeSelection(tag);
        migrateLegacyStrategy(tag);
        migrateLegacyLinks(tag);
        tag.putInt(ConfigKeys.SCHEMA_VERSION, 2);
    }

    private static void normalizeFaceV2(CompoundTag tag) {
        migrateLegacyGroups(tag);
        migrateLegacyTypeSelection(tag);
        migrateLegacyStrategy(tag);
        migrateLegacyLinks(tag);
    }

    private static int readVersion(CompoundTag tag, int legacyVersion, String subject) {
        if (tag.contains(ConfigKeys.SCHEMA_VERSION)
            && !tag.contains(ConfigKeys.SCHEMA_VERSION, Tag.TAG_INT)) {
            throw new IllegalStateException(subject + " schema version must be an int");
        }
        int version = tag.contains(ConfigKeys.SCHEMA_VERSION)
            ? tag.getInt(ConfigKeys.SCHEMA_VERSION) : legacyVersion;
        if (version < 0) {
            throw new IllegalStateException("Invalid " + subject + " schema version: " + version);
        }
        return version;
    }

    private static void requireCompoundIfPresent(CompoundTag tag, String key, String subject) {
        if (tag.contains(key) && !tag.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalStateException(subject + " field must be a compound tag: " + key);
        }
    }

    private static void validateFaceShape(CompoundTag tag) {
        requireCompoundIfPresent(tag, ConfigKeys.GROUPS, "face config");
        requireCompoundIfPresent(tag, ConfigKeys.FILTER_UPGRADES, "face config");
        requireCompoundIfPresent(tag, "linkedNodes", "face config");
        requireCompoundIfPresent(tag, "linkedNodesByGroup", "face config");
        if (tag.contains(ConfigKeys.SELECTED_TYPES)
            && !tag.contains(ConfigKeys.SELECTED_TYPES, Tag.TAG_LIST)) {
            throw new IllegalStateException("face config field must be a list tag: "
                + ConfigKeys.SELECTED_TYPES);
        }
    }

    private static void migrateLegacyGroups(CompoundTag tag) {
        if (tag.contains(ConfigKeys.GROUPS, Tag.TAG_COMPOUND)) return;

        Set<String> displayNames = new LinkedHashSet<>();
        String singleGroup = tag.getString(ConfigKeys.GROUP_ID);
        if (!singleGroup.isEmpty()) displayNames.add(GroupConstraints.normalizeName(singleGroup));
        String groupList = tag.getString(ConfigKeys.GROUP_IDS);
        if (!groupList.isEmpty()) {
            if (groupList.length() > GroupConstraints.MAX_GROUPS_PER_OWNER
                * (GroupConstraints.MAX_NAME_LENGTH + 1)) {
                throw new IllegalStateException("Legacy face group list is too large");
            }
            for (String candidate : groupList.split(",")) {
                if (candidate.trim().isEmpty()) continue;
                displayNames.add(GroupConstraints.normalizeName(candidate));
                if (displayNames.size() > GroupConstraints.MAX_GROUPS_PER_OWNER) {
                    throw new IllegalStateException("Face group limit exceeded");
                }
            }
        }

        UUID owner = tag.hasUUID(ConfigKeys.OWNER) ? tag.getUUID(ConfigKeys.OWNER) : null;
        CompoundTag groups = new CompoundTag();
        for (String displayName : displayNames) {
            GroupKey key = GroupKey.migrated(owner, displayName);
            groups.putString(key.internalId().toString(), displayName);
        }
        if (!groups.isEmpty()) tag.put(ConfigKeys.GROUPS, groups);
    }

    private static void migrateLegacyTypeSelection(CompoundTag tag) {
        if (tag.contains(ConfigKeys.SELECTED_TYPES)) return;
        if (!tag.contains(ConfigKeys.SELECTED_TYPES_MASK)) return;
        int mask = tag.getInt(ConfigKeys.SELECTED_TYPES_MASK);
        // 未知扩展位继续保留旧掩码，让运行时注册表负责解析，避免迁移时丢失第三方类型。
        if ((mask & ~KNOWN_LEGACY_TYPE_MASK) != 0) return;

        ListTag typeIds = new ListTag();
        for (int bit = 0; bit < LEGACY_TYPE_IDS.length; bit++) {
            if ((mask & (1 << bit)) != 0) {
                typeIds.add(StringTag.valueOf(LEGACY_TYPE_IDS[bit]));
            }
        }
        tag.put(ConfigKeys.SELECTED_TYPES, typeIds);
    }

    private static void migrateLegacyStrategy(CompoundTag tag) {
        if (!"SLOT_ROUND_ROBIN".equals(tag.getString(ConfigKeys.STRATEGY))) return;
        tag.putString(ConfigKeys.STRATEGY, "staticlogistics:round_robin");
        if (!tag.contains(ConfigKeys.EXTRACTION_MODE)) {
            tag.putString(ConfigKeys.EXTRACTION_MODE, "SLOT_ROUND_ROBIN");
        }
    }

    private static void migrateLegacyLinks(CompoundTag tag) {
        if (tag.contains("linkedNodesByGroup") || !tag.contains("linkedNodes", Tag.TAG_COMPOUND)) return;
        CompoundTag groups = tag.getCompound(ConfigKeys.GROUPS);
        if (groups.isEmpty()) return;

        UUID owner = tag.hasUUID(ConfigKeys.OWNER)
            ? tag.getUUID(ConfigKeys.OWNER) : GroupKey.LEGACY_UNOWNED;
        CompoundTag scopes = new CompoundTag();
        int index = 0;
        for (String internalId : new TreeSet<>(groups.getAllKeys())) {
            UUID groupId;
            try {
                groupId = UUID.fromString(internalId);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Invalid migrated group identity: " + internalId, exception);
            }
            CompoundTag scope = new CompoundTag();
            scope.putUUID("owner", owner);
            scope.putUUID("internal", groupId);
            scope.put("nodes", tag.getCompound("linkedNodes").copy());
            scopes.put(Integer.toString(index++), scope);
        }
        tag.put("linkedNodesByGroup", scopes);
    }
}
