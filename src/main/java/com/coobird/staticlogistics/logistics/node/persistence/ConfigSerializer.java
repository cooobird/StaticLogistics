package com.coobird.staticlogistics.logistics.node.persistence;

import com.coobird.staticlogistics.api.type.ExtractionMode;
import com.coobird.staticlogistics.transfer.TransferTypeSelection;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;

import java.util.Set;
import java.util.UUID;

/**
 * 配置序列化器 —— 将 {@link FaceConfigComposite} 的子配置序列化/反序列化为 NBT。
 *
 * <p>处理的字段见 {@link ConfigKeys}。
 *
 * <p>版本兼容：反序列化时自动迁移旧格式（group_id → group_ids，SLOT_ROUND_ROBIN → ROUND_ROBIN）。
 */
public class ConfigSerializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CURRENT_SCHEMA_VERSION = LogisticsDataMigration.CURRENT_FACE_SCHEMA_VERSION;

    public static CompoundTag serializeNBT(FaceConfigComposite config, HolderLookup.Provider p) {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt(ConfigKeys.SCHEMA_VERSION, CURRENT_SCHEMA_VERSION);
        CompoundTag groupsTag = new CompoundTag();
        for (GroupRef group : config.faceConfig.getGroups()) {
            groupsTag.putString(group.key().internalId().toString(), group.displayName());
        }
        if (!groupsTag.isEmpty()) nbt.put(ConfigKeys.GROUPS, groupsTag);

        // 迁移窗口内保留显示名称列表，兼容旧版本读取。
        Set<String> allGroups = config.faceConfig.getGroupIds();
        if (!allGroups.isEmpty()) {
            nbt.putString(ConfigKeys.GROUP_IDS, String.join(",", allGroups));
        }

        UUID ownerUuid = config.faceConfig.getOwner();
        if (ownerUuid != null) nbt.putUUID(ConfigKeys.OWNER, ownerUuid);
        nbt.putString(ConfigKeys.OWNER_NAME, config.faceConfig.getOwnerName());
        if (!config.faceConfig.getOwnerProfileTag().isEmpty()) {
            nbt.put(ConfigKeys.OWNER_PROFILE, config.faceConfig.getOwnerProfileTag().copy());
        }

        nbt.putInt(ConfigKeys.INPUT_CHANNEL, config.linkConfig.getInputChannel());
        nbt.putInt(ConfigKeys.OUTPUT_CHANNEL, config.linkConfig.getOutputChannel());
        nbt.putString(ConfigKeys.STRATEGY, config.linkConfig.getStrategy().id().toString());
        nbt.putString(ConfigKeys.EXTRACTION_MODE, config.linkConfig.getExtractionMode().name());
        nbt.putInt(ConfigKeys.PRIORITY, config.linkConfig.getPriority());
        nbt.putInt(ConfigKeys.KEEP_STOCK, config.linkConfig.getKeepStock());

        nbt.put(ConfigKeys.FILTER_UPGRADES, config.filterConfig.getUpgrades().serializeNBT(p));
        TransferTypeSelection.writeIds(nbt, ConfigKeys.SELECTED_TYPES, config.getSelectedTypeIds());
        nbt.putInt(ConfigKeys.SELECTED_TYPES_MASK, config.getLegacySelectedTypesMask());
        return nbt;
    }

    /** 解码已经由迁移入口提升到当前版本的面配置。 */
    public static void deserializeMigratedNBT(Object permit, FaceConfigComposite config,
                                              HolderLookup.Provider p, CompoundTag nbt) {
        int schemaVersion = nbt.contains(ConfigKeys.SCHEMA_VERSION)
            ? nbt.getInt(ConfigKeys.SCHEMA_VERSION) : 1;
        if (schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported face config schema version: " + schemaVersion);
        }
        UUID ownerUuid = nbt.hasUUID(ConfigKeys.OWNER) ? nbt.getUUID(ConfigKeys.OWNER) : null;
        String ownerName = nbt.contains(ConfigKeys.OWNER_NAME) ? nbt.getString(ConfigKeys.OWNER_NAME) : "Unknown";
        if (ownerUuid != null) config.setOwner(permit, ownerUuid, ownerName, null);
        if (nbt.contains(ConfigKeys.OWNER_PROFILE)) {
            config.setOwnerProfileTag(permit, nbt.getCompound(ConfigKeys.OWNER_PROFILE).copy());
        }

        if (nbt.contains(ConfigKeys.GROUPS)) {
            CompoundTag groupsTag = nbt.getCompound(ConfigKeys.GROUPS);
            UUID effectiveOwner = ownerUuid == null ? GroupKey.LEGACY_UNOWNED : ownerUuid;
            for (String internalId : groupsTag.getAllKeys()) {
                try {
                    String displayName = groupsTag.getString(internalId);
                    if (!displayName.isEmpty()) {
                        config.addGroup(permit, new GroupRef(
                            new GroupKey(effectiveOwner, UUID.fromString(internalId)), displayName));
                    }
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Ignoring invalid group identity {}", internalId);
                }
            }
        } else {
            // 旧版结构：读取所有者后，再按显示名称执行确定性迁移。
            String oldGroupId = nbt.getString(ConfigKeys.GROUP_ID);
            if (!oldGroupId.isEmpty()) config.addLegacyGroup(permit, oldGroupId);
            String groupIdsStr = nbt.getString(ConfigKeys.GROUP_IDS);
            if (!groupIdsStr.isEmpty()) {
                for (String gid : groupIdsStr.split(",")) {
                    String trimmed = gid.trim();
                    if (!trimmed.isEmpty()) config.addLegacyGroup(permit, trimmed);
                }
            }
        }

        config.setInputChannel(nbt.getInt(ConfigKeys.INPUT_CHANNEL));
        config.setOutputChannel(nbt.getInt(ConfigKeys.OUTPUT_CHANNEL));
        try {
            String stratName = nbt.getString(ConfigKeys.STRATEGY);
            // 迁移旧 SLOT_ROUND_ROBIN → ROUND_ROBIN
            if ("SLOT_ROUND_ROBIN".equals(stratName)) {
                config.setDistributionStrategy(
                    com.coobird.staticlogistics.transfer.DistributionStrategyRegistry.ROUND_ROBIN);
                config.setExtractionMode(ExtractionMode.SLOT_ROUND_ROBIN);
            } else {
                config.setDistributionStrategy(
                    com.coobird.staticlogistics.transfer.DistributionStrategyRegistry.byName(stratName));
            }
        } catch (Exception e) {
            config.setDistributionStrategy(
                com.coobird.staticlogistics.transfer.DistributionStrategyRegistry.SEQUENTIAL);
        }
        if (nbt.contains(ConfigKeys.EXTRACTION_MODE)) {
            try {
                config.setExtractionMode(ExtractionMode.valueOf(nbt.getString(ConfigKeys.EXTRACTION_MODE)));
            } catch (Exception e) {
                config.setExtractionMode(ExtractionMode.SEQUENTIAL);
            }
        }
        config.setPriority(nbt.getInt(ConfigKeys.PRIORITY));
        config.setKeepStock(nbt.getInt(ConfigKeys.KEEP_STOCK));

        if (nbt.contains(ConfigKeys.FILTER_UPGRADES)) {
            config.filterConfig.getUpgrades().deserializeNBT(p, nbt.getCompound(ConfigKeys.FILTER_UPGRADES));
        }
        if (nbt.contains(ConfigKeys.SELECTED_TYPES)) {
            config.setSelectedTypeIds(TransferTypeSelection.readIds(nbt, ConfigKeys.SELECTED_TYPES));
            if (nbt.contains(ConfigKeys.SELECTED_TYPES_MASK)) {
                config.loadUnresolvedLegacySelectedTypesMask(
                    nbt.getInt(ConfigKeys.SELECTED_TYPES_MASK));
            }
        } else if (nbt.contains(ConfigKeys.SELECTED_TYPES_MASK)) {
            config.loadLegacySelectedTypesMask(nbt.getInt(ConfigKeys.SELECTED_TYPES_MASK));
        }
    }
}
