package com.coobird.staticlogistics.logistics.node.persistence;

import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.api.type.ExtractionMode;
import com.coobird.staticlogistics.logistics.node.ChannelRedstoneMode;
import com.coobird.staticlogistics.logistics.node.EndpointConfig;
import com.coobird.staticlogistics.transfer.DistributionStrategyRegistry;
import com.coobird.staticlogistics.transfer.TransferTypeSelection;
import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;

import java.util.UUID;

/// 读写端点配置。
public class ConfigSerializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CURRENT_SCHEMA_VERSION = LogisticsDataFormat.CURRENT_FACE_SCHEMA_VERSION;

    public static CompoundTag serializeNBT(EndpointConfig config, HolderLookup.Provider p) {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt(ConfigKeys.SCHEMA_VERSION, CURRENT_SCHEMA_VERSION);
        CompoundTag groupsTag = new CompoundTag();
        for (GroupRef group : config.faceConfig.getGroups()) {
            groupsTag.putString(group.key().internalId().toString(), group.displayName());
        }
        if (!groupsTag.isEmpty()) nbt.put(ConfigKeys.GROUPS, groupsTag);

        UUID ownerUuid = config.faceConfig.getOwner();
        if (ownerUuid != null) nbt.putUUID(ConfigKeys.OWNER, ownerUuid);
        nbt.putString(ConfigKeys.OWNER_NAME, config.faceConfig.getOwnerName());
        if (!config.faceConfig.getOwnerProfileTag().isEmpty()) {
            nbt.put(ConfigKeys.OWNER_PROFILE, config.faceConfig.getOwnerProfileTag().copy());
        }

        nbt.putString(ConfigKeys.STRATEGY, config.linkConfig.getStrategy().id().toString());
        nbt.putString(ConfigKeys.EXTRACTION_MODE, config.linkConfig.getExtractionMode().name());
        nbt.putLong(ConfigKeys.PRIORITY, config.linkConfig.getPriority());
        nbt.putLong(ConfigKeys.KEEP_STOCK, config.linkConfig.getKeepStock());
        nbt.putString("redstone", config.linkConfig.getRedstoneMode().name());
        nbt.putString("channel_name", config.linkConfig.getChannelName());
        nbt.putBoolean("channel_enabled", config.linkConfig.isEnabled());

        nbt.put(ConfigKeys.FILTER_UPGRADES, config.filterConfig.getUpgrades().serializeNBT());
        TransferTypeSelection.writeIds(nbt, ConfigKeys.SELECTED_TYPES, config.getSelectedTypeIds());

        return nbt;
    }

    /// 解码当前格式的面配置。
    public static void deserializeNBT(Object permit, EndpointConfig config,
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
        }

        try {
            String stratName = nbt.getString(ConfigKeys.STRATEGY);
            config.setDistributionStrategy(DistributionStrategyRegistry.byName(stratName));
        } catch (Exception e) {
            config.setDistributionStrategy(
                DistributionStrategyRegistry.SEQUENTIAL);
        }
        if (nbt.contains(ConfigKeys.EXTRACTION_MODE)) {
            try {
                config.setExtractionMode(ExtractionMode.valueOf(nbt.getString(ConfigKeys.EXTRACTION_MODE)));
            } catch (Exception e) {
                config.setExtractionMode(ExtractionMode.SEQUENTIAL);
            }
        }
        config.setPriority(nbt.getLong(ConfigKeys.PRIORITY));
        config.setKeepStock(nbt.getLong(ConfigKeys.KEEP_STOCK));
        config.setRedstoneMode(ChannelRedstoneMode.valueOf(nbt.getString("redstone")));
        config.setChannelName(nbt.getString("channel_name"));
        config.setChannelEnabled(nbt.getBoolean("channel_enabled"));

        if (nbt.contains(ConfigKeys.FILTER_UPGRADES)) {
            config.filterConfig.getUpgrades().deserializeNBT(nbt.getCompound(ConfigKeys.FILTER_UPGRADES));
        }
        config.setSelectedTypeIds(TransferTypeSelection.readIds(nbt, ConfigKeys.SELECTED_TYPES));
    }
}
