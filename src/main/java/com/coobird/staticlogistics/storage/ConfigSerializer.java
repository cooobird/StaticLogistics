package com.coobird.staticlogistics.storage;

import com.coobird.staticlogistics.api.type.ExtractionMode;
import com.coobird.staticlogistics.logic.type.TransferTypeSelection;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
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

    public static CompoundTag serializeNBT(FaceConfigComposite config, HolderLookup.Provider p) {
        CompoundTag nbt = new CompoundTag();
        // 始终写 group_ids（哪怕是单组），不再单独写 group_id
        Set<String> allGroups = config.faceConfig.getGroupIds();
        if (!allGroups.isEmpty()) {
            nbt.putString(ConfigKeys.GROUP_IDS, String.join(",", allGroups));
        }

        UUID ownerUuid = config.faceConfig.getOwner();
        if (ownerUuid != null) nbt.putUUID(ConfigKeys.OWNER, ownerUuid);
        nbt.putString(ConfigKeys.OWNER_NAME, config.faceConfig.getOwnerName());

        nbt.putInt(ConfigKeys.INPUT_CHANNEL, config.linkConfig.getInputChannel());
        nbt.putInt(ConfigKeys.OUTPUT_CHANNEL, config.linkConfig.getOutputChannel());
        nbt.putString(ConfigKeys.STRATEGY, config.linkConfig.getStrategy().id().toString());
        nbt.putString(ConfigKeys.EXTRACTION_MODE, config.linkConfig.getExtractionMode().name());
        nbt.putInt(ConfigKeys.PRIORITY, config.linkConfig.getPriority());
        nbt.putInt(ConfigKeys.KEEP_STOCK, config.linkConfig.getKeepStock());

        try {
            nbt.put(ConfigKeys.FILTER_UPGRADES, config.filterConfig.getUpgrades().serializeNBT(p));
        } catch (Exception e) {
            LOGGER.error("Failed to serialize filter upgrades for face config", e);
            nbt.put(ConfigKeys.FILTER_UPGRADES, new CompoundTag());
        }
        TransferTypeSelection.writeIds(nbt, ConfigKeys.SELECTED_TYPES, config.getSelectedTypeIds());
        nbt.putInt(ConfigKeys.SELECTED_TYPES_MASK, config.getSelectedTypesMask());
        return nbt;
    }

    public static void deserializeNBT(FaceConfigComposite config, HolderLookup.Provider p, CompoundTag nbt) {
        // 先读老格式 group_id（单个字符串），再读新格式 group_ids（逗号分隔集合），合并去重
        String oldGroupId = nbt.getString(ConfigKeys.GROUP_ID);
        if (!oldGroupId.isEmpty()) config.faceConfig.addGroupId(oldGroupId);
        String groupIdsStr = nbt.getString(ConfigKeys.GROUP_IDS);
        if (!groupIdsStr.isEmpty()) {
            for (String gid : groupIdsStr.split(",")) {
                String trimmed = gid.trim();
                if (!trimmed.isEmpty()) config.faceConfig.addGroupId(trimmed);
            }
        }

        UUID ownerUuid = nbt.hasUUID(ConfigKeys.OWNER) ? nbt.getUUID(ConfigKeys.OWNER) : null;
        String ownerName = nbt.contains(ConfigKeys.OWNER_NAME) ? nbt.getString(ConfigKeys.OWNER_NAME) : "Unknown";
        if (nbt.contains(ConfigKeys.OWNER_PROFILE))
            config.faceConfig.setOwnerProfileTag(nbt.getCompound(ConfigKeys.OWNER_PROFILE));
        if (ownerUuid != null) config.faceConfig.setOwner(ownerUuid, ownerName);

        config.linkConfig.setInputChannel(nbt.getInt(ConfigKeys.INPUT_CHANNEL));
        config.linkConfig.setOutputChannel(nbt.getInt(ConfigKeys.OUTPUT_CHANNEL));
        try {
            String stratName = nbt.getString(ConfigKeys.STRATEGY);
            // 迁移旧 SLOT_ROUND_ROBIN → ROUND_ROBIN
            if ("SLOT_ROUND_ROBIN".equals(stratName)) {
                config.linkConfig.setStrategy(
                    com.coobird.staticlogistics.logic.DistributionStrategyRegistry.ROUND_ROBIN);
                config.linkConfig.setExtractionMode(ExtractionMode.SLOT_ROUND_ROBIN);
            } else {
                config.linkConfig.setStrategy(
                    com.coobird.staticlogistics.logic.DistributionStrategyRegistry.byName(stratName));
            }
        } catch (Exception e) {
            config.linkConfig.setStrategy(
                com.coobird.staticlogistics.logic.DistributionStrategyRegistry.SEQUENTIAL);
        }
        if (nbt.contains(ConfigKeys.EXTRACTION_MODE)) {
            try {
                config.linkConfig.setExtractionMode(ExtractionMode.valueOf(nbt.getString(ConfigKeys.EXTRACTION_MODE)));
            } catch (Exception e) {
                config.linkConfig.setExtractionMode(ExtractionMode.SEQUENTIAL);
            }
        }
        config.linkConfig.setPriority(nbt.getInt(ConfigKeys.PRIORITY));
        config.linkConfig.setKeepStock(nbt.getInt(ConfigKeys.KEEP_STOCK));

        if (nbt.contains(ConfigKeys.FILTER_UPGRADES)) {
            config.filterConfig.getUpgrades().deserializeNBT(p, nbt.getCompound(ConfigKeys.FILTER_UPGRADES));
        }
        if (nbt.contains(ConfigKeys.SELECTED_TYPES)) {
            config.setSelectedTypeIds(TransferTypeSelection.readIds(nbt, ConfigKeys.SELECTED_TYPES));
        } else if (nbt.contains(ConfigKeys.SELECTED_TYPES_MASK)) {
            config.setSelectedTypesMask(nbt.getInt(ConfigKeys.SELECTED_TYPES_MASK));
        }
    }
}
