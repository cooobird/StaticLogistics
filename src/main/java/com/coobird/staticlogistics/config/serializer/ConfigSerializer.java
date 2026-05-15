package com.coobird.staticlogistics.config.serializer;

import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.storage.config.LinkConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ConfigSerializer {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static CompoundTag serializeNBT(FaceConfigComposite config, HolderLookup.Provider p) {
        CompoundTag nbt = new CompoundTag();

        nbt.putString("group_id", config.faceConfig.getGroupId());

        UUID ownerUuid = config.faceConfig.getOwner();
        if (ownerUuid != null) {
            nbt.putUUID("owner", ownerUuid);
        }
        nbt.putString("owner_name", config.faceConfig.getOwnerName());

        CompoundTag typesNbt = new CompoundTag();

        Map<ResourceLocation, LinkConfig.SideData> settingsSnapshot;
        synchronized (config.linkConfig.getAllSettings()) {
            settingsSnapshot = new HashMap<>(config.linkConfig.getAllSettings());
        }

        for (Map.Entry<ResourceLocation, LinkConfig.SideData> entry : settingsSnapshot.entrySet()) {
            ResourceLocation id = entry.getKey();
            LinkConfig.SideData data = entry.getValue();
            if (data.isDefault()) continue;

            CompoundTag d = new CompoundTag();
            d.putInt("in_ch", data.inputChannel);
            d.putInt("out_ch", data.outputChannel);
            d.putString("strat", data.strategy.name());
            d.putInt("prio", data.priority);

            typesNbt.put(id.toString(), d);
        }
        nbt.put("types", typesNbt);
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
        config.faceConfig.setGroupId(nbt.getString("group_id"));

        UUID ownerUuid = nbt.hasUUID("owner") ? nbt.getUUID("owner") : null;
        String ownerName = nbt.contains("owner_name") ? nbt.getString("owner_name") : "Unknown";
        if (ownerUuid != null) {
            config.faceConfig.setOwner(ownerUuid, ownerName);
        }

        CompoundTag typesNbt = nbt.getCompound("types");

        synchronized (config.linkConfig.getAllSettings()) {
            config.linkConfig.getAllSettings().clear();

            for (String key : typesNbt.getAllKeys()) {
                ResourceLocation rl = ResourceLocation.tryParse(key);
                if (rl == null) continue;

                CompoundTag d = typesNbt.getCompound(key);
                TransferType type = TransferRegistries.get(rl);
                if (type == null) {
                    LOGGER.warn("Unknown transfer type '{}' in saved config, skipping", rl);
                    continue;
                }

                LinkConfig.SideData data = config.linkConfig.getSettings(type);

                data.inputChannel = d.getInt("in_ch");
                data.outputChannel = d.getInt("out_ch");
                try {
                    data.strategy = DistributionStrategy.valueOf(d.getString("strat"));
                } catch (Exception e) {
                    data.strategy = DistributionStrategy.SEQUENTIAL;
                }
                if (d.contains("prio")) {
                    data.priority = d.getInt("prio");
                }
            }
        }
        if (nbt.contains("filter_upgrades")) {
            config.filterConfig.getUpgrades().deserializeNBT(p, nbt.getCompound("filter_upgrades"));
        }
        if (nbt.contains("selected_types_mask")) {
            config.setSelectedTypesMask(nbt.getInt("selected_types_mask"));
        }
    }
}