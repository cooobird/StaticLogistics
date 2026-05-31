package com.coobird.staticlogistics.config.serializer;

import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.ExtractionMode;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;

import java.util.Set;
import java.util.UUID;

public class ConfigSerializer {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static CompoundTag serializeNBT(FaceConfigComposite config, HolderLookup.Provider p) {
        CompoundTag nbt = new CompoundTag();
        // 始终写 group_ids（哪怕是单组），不再单独写 group_id
        Set<String> allGroups = config.faceConfig.getGroupIds();
        if (!allGroups.isEmpty()) {
            nbt.putString("group_ids", String.join(",", allGroups));
        }

        UUID ownerUuid = config.faceConfig.getOwner();
        if (ownerUuid != null) nbt.putUUID("owner", ownerUuid);
        nbt.putString("owner_name", config.faceConfig.getOwnerName());

        nbt.putInt("input_channel", config.linkConfig.getInputChannel());
        nbt.putInt("output_channel", config.linkConfig.getOutputChannel());
        nbt.putString("strategy", config.linkConfig.getStrategy().name());
        nbt.putString("extraction_mode", config.linkConfig.getExtractionMode().name());
        nbt.putInt("priority", config.linkConfig.getPriority());
        nbt.putInt("keep_stock", config.linkConfig.getKeepStock());

        try {
            nbt.put("filter_upgrades", config.filterConfig.getUpgrades().serializeNBT(p));
        } catch (Exception e) {
            LOGGER.error("Failed to serialize filter upgrades for face config", e);
            nbt.put("filter_upgrades", new CompoundTag());
        }
        nbt.putInt("selected_types_mask", config.getSelectedTypesMask());
        return nbt;
    }

    public static void deserializeNBT(FaceConfigComposite config, HolderLookup.Provider p, CompoundTag nbt) {
        // 先读老格式 group_id（单个字符串），再读新格式 group_ids（逗号分隔集合），合并去重
        String oldGroupId = nbt.getString("group_id");
        if (!oldGroupId.isEmpty()) config.faceConfig.addGroupId(oldGroupId);
        String groupIdsStr = nbt.getString("group_ids");
        if (!groupIdsStr.isEmpty()) {
            for (String gid : groupIdsStr.split(",")) {
                String trimmed = gid.trim();
                if (!trimmed.isEmpty()) config.faceConfig.addGroupId(trimmed);
            }
        }

        UUID ownerUuid = nbt.hasUUID("owner") ? nbt.getUUID("owner") : null;
        String ownerName = nbt.contains("owner_name") ? nbt.getString("owner_name") : "Unknown";
        if (nbt.contains("owner_profile"))
            config.faceConfig.setOwnerProfileTag(nbt.getCompound("owner_profile"));
        if (ownerUuid != null) config.faceConfig.setOwner(ownerUuid, ownerName);

        config.linkConfig.setInputChannel(nbt.getInt("input_channel"));
        config.linkConfig.setOutputChannel(nbt.getInt("output_channel"));
        try {
            String stratName = nbt.getString("strategy");
            // 迁移旧 SLOT_ROUND_ROBIN → ROUND_ROBIN
            if ("SLOT_ROUND_ROBIN".equals(stratName)) {
                config.linkConfig.setStrategy(DistributionStrategy.ROUND_ROBIN);
                config.linkConfig.setExtractionMode(ExtractionMode.SLOT_ROUND_ROBIN);
            } else {
                config.linkConfig.setStrategy(DistributionStrategy.valueOf(stratName));
            }
        } catch (Exception e) {
            config.linkConfig.setStrategy(DistributionStrategy.SEQUENTIAL);
        }
        if (nbt.contains("extraction_mode")) {
            try {
                config.linkConfig.setExtractionMode(ExtractionMode.valueOf(nbt.getString("extraction_mode")));
            } catch (Exception e) {
                config.linkConfig.setExtractionMode(ExtractionMode.SEQUENTIAL);
            }
        }
        config.linkConfig.setPriority(nbt.getInt("priority"));
        config.linkConfig.setKeepStock(nbt.getInt("keep_stock"));

        if (nbt.contains("filter_upgrades")) {
            config.filterConfig.getUpgrades().deserializeNBT(p, nbt.getCompound("filter_upgrades"));
        }
        if (nbt.contains("selected_types_mask")) {
            config.setSelectedTypesMask(nbt.getInt("selected_types_mask"));
        }
    }
}