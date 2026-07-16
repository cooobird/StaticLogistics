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
 */
public final class LogisticsDataMigration {
    public static final int CURRENT_STORAGE_SCHEMA_VERSION = 2;
    public static final int CURRENT_FACE_SCHEMA_VERSION = 2;

    /**
     * 1.20.1 Forge 已发布的位序，不能替换为 1.21.1 的化学品布局。
     */
    private static final String[] LEGACY_TYPE_IDS = {
        "staticlogistics:item",
        "staticlogistics:fluid",
        "staticlogistics:energy",
        "staticlogistics:mek_gas",
        "staticlogistics:mek_infusion",
        "staticlogistics:mek_pigment",
        "staticlogistics:mek_slurry",
        "staticlogistics:mek_heat",
        "staticlogistics:ars_source",
        "staticlogistics:botania_mana",
        "staticlogistics:gtceu_energy"
    };
    private static final int KNOWN_LEGACY_TYPE_MASK = (1 << LEGACY_TYPE_IDS.length) - 1;

    private LogisticsDataMigration() {
    }

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

    public static CompoundTag migrateStorage(CompoundTag source) {
        return migrateStorageWithReport(source).tag();
    }

    /**
     * 始终在副本上迁移，完整成功后调用方才能物化结果。
     */
    public static MigrationResult migrateStorageWithReport(CompoundTag source) {
        CompoundTag migrated = source.copy();
        int version = readVersion(migrated, 0, "logistics storage");
        int sourceVersion = version;
        if (version > CURRENT_STORAGE_SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported logistics storage schema version: " + version);
        }
        List<String> steps = new ArrayList<>();
        if (version == 0) {
            migrated.putInt(ConfigKeys.SCHEMA_VERSION, 1);
            steps.add("storage:0->1");
            version = 1;
        }
        if (version == 1) {
            migrated.putInt(ConfigKeys.SCHEMA_VERSION, 2);
            steps.add("storage:1->2");
            version = 2;
        }
        requireCompoundIfPresent(migrated, "face_configs", "logistics storage");
        requireCompoundIfPresent(migrated, "container_configs", "logistics storage");
        if (!migrated.contains("face_configs")) migrated.put("face_configs", new CompoundTag());
        if (!migrated.contains("container_configs")) migrated.put("container_configs", new CompoundTag());
        return new MigrationResult(migrated, sourceVersion, version, steps);
    }

    public static CompoundTag migrateFace(CompoundTag source) {
        return migrateFaceWithReport(source).tag();
    }

    public static MigrationResult migrateFaceWithReport(CompoundTag source) {
        CompoundTag migrated = source.copy();
        int version = readVersion(migrated, 1, "face config");
        if (version < 1 || version > CURRENT_FACE_SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported face config schema version: " + version);
        }
        int sourceVersion = version;
        validateFaceShape(migrated);
        List<String> steps = new ArrayList<>();
        if (version == 1) {
            normalizeFaceV2(migrated);
            steps.add("face:1->2");
            version = 2;
        } else {
            normalizeFaceV2(migrated);
        }
        migrated.putInt(ConfigKeys.SCHEMA_VERSION, version);
        return new MigrationResult(migrated, sourceVersion, version, steps);
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
        if (version < 0) throw new IllegalStateException("Invalid " + subject + " schema version: " + version);
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
            throw new IllegalStateException("face config field must be a list tag: " + ConfigKeys.SELECTED_TYPES);
        }
    }

    private static void migrateLegacyGroups(CompoundTag tag) {
        if (tag.contains(ConfigKeys.GROUPS, Tag.TAG_COMPOUND)) return;
        Set<String> names = new LinkedHashSet<>();
        String single = tag.getString(ConfigKeys.GROUP_ID);
        if (!single.isEmpty()) names.add(GroupConstraints.normalizeName(single));
        String joined = tag.getString(ConfigKeys.GROUP_IDS);
        if (!joined.isEmpty()) {
            if (joined.length() > GroupConstraints.MAX_GROUPS_PER_OWNER
                * (GroupConstraints.MAX_NAME_LENGTH + 1)) {
                throw new IllegalStateException("Legacy face group list is too large");
            }
            for (String candidate : joined.split(",")) {
                if (candidate.trim().isEmpty()) continue;
                names.add(GroupConstraints.normalizeName(candidate));
                if (names.size() > GroupConstraints.MAX_GROUPS_PER_OWNER) {
                    throw new IllegalStateException("Face group limit exceeded");
                }
            }
        }
        UUID owner = tag.hasUUID(ConfigKeys.OWNER) ? tag.getUUID(ConfigKeys.OWNER) : null;
        CompoundTag groups = new CompoundTag();
        for (String name : names) groups.putString(GroupKey.migrated(owner, name).internalId().toString(), name);
        if (!groups.isEmpty()) tag.put(ConfigKeys.GROUPS, groups);
    }

    private static void migrateLegacyTypeSelection(CompoundTag tag) {
        if (tag.contains(ConfigKeys.SELECTED_TYPES) || !tag.contains(ConfigKeys.SELECTED_TYPES_MASK)) return;
        int mask = tag.getInt(ConfigKeys.SELECTED_TYPES_MASK);
        // 含未知扩展位时保留原 mask，等待运行时注册表解析，不能静默吞掉选择。
        if ((mask & ~KNOWN_LEGACY_TYPE_MASK) != 0) return;
        ListTag ids = new ListTag();
        for (int bit = 0; bit < LEGACY_TYPE_IDS.length; bit++) {
            if ((mask & (1 << bit)) != 0) ids.add(StringTag.valueOf(LEGACY_TYPE_IDS[bit]));
        }
        tag.put(ConfigKeys.SELECTED_TYPES, ids);
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
        UUID owner = tag.hasUUID(ConfigKeys.OWNER) ? tag.getUUID(ConfigKeys.OWNER) : GroupKey.LEGACY_UNOWNED;
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
