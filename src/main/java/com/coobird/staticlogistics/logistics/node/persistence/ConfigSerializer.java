package com.coobird.staticlogistics.logistics.node.persistence;

import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.api.type.ExtractionMode;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.DistributionStrategyRegistry;
import com.coobird.staticlogistics.transfer.TransferTypeSelection;
import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.slf4j.Logger;

import java.util.Set;
import java.util.UUID;

/**
 * 面配置 schema v2 的唯一序列化入口。
 */
public final class ConfigSerializer {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ConfigSerializer() {
    }

    public static CompoundTag serializeNBT(FaceConfigComposite config, HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(ConfigKeys.SCHEMA_VERSION, LogisticsDataMigration.CURRENT_FACE_SCHEMA_VERSION);

        CompoundTag groups = new CompoundTag();
        for (GroupRef group : config.faceConfig.getGroups()) {
            groups.putString(group.key().internalId().toString(), group.displayName());
        }
        if (!groups.isEmpty()) tag.put(ConfigKeys.GROUPS, groups);

        // 迁移窗口保留旧显示名称投影，旧版本仍可识别分组。
        Set<String> displayNames = config.faceConfig.getGroupIds();
        if (!displayNames.isEmpty()) tag.putString(ConfigKeys.GROUP_IDS, String.join(",", displayNames));

        UUID owner = config.faceConfig.getOwner();
        if (owner != null) tag.putUUID(ConfigKeys.OWNER, owner);
        tag.putString(ConfigKeys.OWNER_NAME, config.faceConfig.getOwnerName());
        CompoundTag ownerProfile = config.faceConfig.getOwnerProfileTag();
        if (!ownerProfile.isEmpty()) tag.put(ConfigKeys.OWNER_PROFILE, ownerProfile);

        tag.putInt(ConfigKeys.INPUT_CHANNEL, config.linkConfig.getInputChannel());
        tag.putInt(ConfigKeys.OUTPUT_CHANNEL, config.linkConfig.getOutputChannel());
        tag.putString(ConfigKeys.STRATEGY, config.linkConfig.getStrategy().id().toString());
        tag.putString(ConfigKeys.EXTRACTION_MODE, config.linkConfig.getExtractionMode().name());
        tag.putInt(ConfigKeys.PRIORITY, config.linkConfig.getPriority());
        tag.putInt(ConfigKeys.KEEP_STOCK, config.linkConfig.getKeepStock());

        try {
            tag.put(ConfigKeys.FILTER_UPGRADES, config.filterConfig.getUpgrades().serializeNBT());
        } catch (Exception exception) {
            LOGGER.error("Failed to serialize filter upgrades for face config", exception);
            tag.put(ConfigKeys.FILTER_UPGRADES, new CompoundTag());
        }
        TransferTypeSelection.writeIds(tag, ConfigKeys.SELECTED_TYPES, config.getSelectedTypeIds());
        tag.putInt(ConfigKeys.SELECTED_TYPES_MASK, config.getLegacySelectedTypesMask());
        return tag;
    }

    /**
     * 输入必须先经过 {@link LogisticsDataMigration#migrateFace(CompoundTag)}。
     */
    public static void deserializeMigratedNBT(
        Object permit,
        FaceConfigComposite config,
        HolderLookup.Provider provider,
        CompoundTag tag
    ) {
        if (tag.getInt(ConfigKeys.SCHEMA_VERSION) > LogisticsDataMigration.CURRENT_FACE_SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported face config schema version: "
                + tag.getInt(ConfigKeys.SCHEMA_VERSION));
        }

        UUID owner = tag.hasUUID(ConfigKeys.OWNER) ? tag.getUUID(ConfigKeys.OWNER) : null;
        String ownerName = tag.contains(ConfigKeys.OWNER_NAME, Tag.TAG_STRING)
            ? tag.getString(ConfigKeys.OWNER_NAME) : "Unknown";
        if (owner != null) config.setOwner(permit, owner, ownerName, null);
        if (tag.contains(ConfigKeys.OWNER_PROFILE, Tag.TAG_COMPOUND)) {
            config.setOwnerProfileTag(permit, tag.getCompound(ConfigKeys.OWNER_PROFILE));
        }

        if (tag.contains(ConfigKeys.GROUPS, Tag.TAG_COMPOUND)) {
            UUID effectiveOwner = owner == null ? GroupKey.LEGACY_UNOWNED : owner;
            CompoundTag groups = tag.getCompound(ConfigKeys.GROUPS);
            for (String internalId : groups.getAllKeys()) {
                if (!groups.contains(internalId, Tag.TAG_STRING)) {
                    throw new IllegalStateException("Group display name must be a string: " + internalId);
                }
                config.addGroup(permit, new GroupRef(
                    new GroupKey(effectiveOwner, UUID.fromString(internalId)),
                    groups.getString(internalId)));
            }
        }

        config.setInputChannel(tag.getInt(ConfigKeys.INPUT_CHANNEL));
        config.setOutputChannel(tag.getInt(ConfigKeys.OUTPUT_CHANNEL));
        try {
            config.setDistributionStrategy(DistributionStrategyRegistry.byName(tag.getString(ConfigKeys.STRATEGY)));
        } catch (Exception exception) {
            config.setDistributionStrategy(DistributionStrategyRegistry.SEQUENTIAL);
        }
        if (tag.contains(ConfigKeys.EXTRACTION_MODE, Tag.TAG_STRING)) {
            try {
                config.setExtractionMode(
                    ExtractionMode.valueOf(tag.getString(ConfigKeys.EXTRACTION_MODE)));
            } catch (IllegalArgumentException exception) {
                config.setExtractionMode(ExtractionMode.SEQUENTIAL);
            }
        }
        config.setPriority(tag.getInt(ConfigKeys.PRIORITY));
        config.setKeepStock(tag.getInt(ConfigKeys.KEEP_STOCK));

        if (tag.contains(ConfigKeys.FILTER_UPGRADES, Tag.TAG_COMPOUND)) {
            config.filterConfig.getUpgrades().deserializeNBT(tag.getCompound(ConfigKeys.FILTER_UPGRADES));
        }
        if (tag.contains(ConfigKeys.SELECTED_TYPES, Tag.TAG_LIST)) {
            config.setSelectedTypeIds(TransferTypeSelection.readIds(tag, ConfigKeys.SELECTED_TYPES));
            if (tag.contains(ConfigKeys.SELECTED_TYPES_MASK, Tag.TAG_INT)) {
                config.loadUnresolvedLegacySelectedTypesMask(tag.getInt(ConfigKeys.SELECTED_TYPES_MASK));
            }
        } else if (tag.contains(ConfigKeys.SELECTED_TYPES_MASK, Tag.TAG_INT)) {
            config.loadLegacySelectedTypesMask(tag.getInt(ConfigKeys.SELECTED_TYPES_MASK));
        }
    }
}
