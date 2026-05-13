package com.coobird.staticlogistics.config.serializer;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.storage.config.LinkConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
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
            d.putBoolean("in_en", data.inputEnabled);
            d.putBoolean("out_en", data.outputEnabled);
            d.putInt("in_ch", data.inputChannel);
            d.putInt("out_ch", data.outputChannel);
            d.putString("strat", data.strategy.name());
            d.putInt("prio", data.priority);

            if (!data.linkedInputs.isEmpty()) {
                ListTag linkedList = new ListTag();
                synchronized (data.linkedInputs) {
                    for (LogisticsNode linkedNode : data.linkedInputs) {
                        CompoundTag nodeTag = new CompoundTag();
                        nodeTag.putString("dim", linkedNode.gPos().dimension().location().toString());
                        nodeTag.putLong("pos", linkedNode.gPos().pos().asLong());
                        nodeTag.putInt("face", linkedNode.face().get3DDataValue());
                        linkedList.add(nodeTag);
                    }
                }
                d.put("linked_inputs", linkedList);
            }

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
                TransferType type = new TransferType(rl, 0, 0, "", null, 0);
                LinkConfig.SideData data = config.linkConfig.getSettings(type);

                data.inputEnabled = d.getBoolean("in_en");
                data.outputEnabled = d.getBoolean("out_en");
                data.inputChannel = d.getInt("in_ch");
                data.outputChannel = d.getInt("out_ch");
                try {
                    data.strategy = DistributionStrategy.valueOf(d.getString("strat"));
                } catch (Exception e) {
                    data.strategy = DistributionStrategy.SEQUENTIAL;
                }

                if (d.contains("linked_inputs")) {
                    ListTag linkedList = d.getList("linked_inputs", Tag.TAG_COMPOUND);
                    data.linkedInputs.clear();
                    for (int i = 0; i < linkedList.size(); i++) {
                        CompoundTag nodeTag = linkedList.getCompound(i);
                        try {
                            ResourceLocation dimRl = ResourceLocation.parse(nodeTag.getString("dim"));
                            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, dimRl);
                            BlockPos nodePos = BlockPos.of(nodeTag.getLong("pos"));
                            Direction nodeFace = Direction.from3DDataValue(nodeTag.getInt("face"));
                            data.linkedInputs.add(new LogisticsNode(GlobalPos.of(dim, nodePos), nodeFace));
                        } catch (Exception e) {
                            LOGGER.warn("Failed to deserialize linked node entry in config", e);
                        }
                    }
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