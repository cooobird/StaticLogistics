package com.coobird.staticlogistics.core;

import com.coobird.staticlogistics.common.item.UpgradeItem;
import com.coobird.staticlogistics.transfer.TransferType;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class FaceConfig {
    private int cachedSpeedMult = 1, cachedRangeMult = 1, cachedStackMult = 1;
    private boolean cachedDimEffective = false, cacheDirty = true;
    private Consumer<FaceConfig> onDirty = (c) -> {
    };

    private final ItemStackHandler upgrades = new ItemStackHandler(4) {
        @Override
        protected void onContentsChanged(int slot) {
            cacheDirty = true;
            onDirty.accept(FaceConfig.this);
        }
    };

    private final Map<TransferType, SideData> typeSettings = new EnumMap<>(TransferType.class);

    public FaceConfig() {
        for (TransferType type : TransferType.values()) {
            typeSettings.put(type, new SideData());
        }
    }

    public boolean isDefault() {
        for (int i = 0; i < upgrades.getSlots(); i++) {
            if (!upgrades.getStackInSlot(i).isEmpty()) return false;
        }
        for (SideData data : typeSettings.values()) {
            if (!data.isDefault()) return false;
        }
        return true;
    }

    public static class SideData {
        public ConnectionMode mode = ConnectionMode.DISABLED;
        public DistributionStrategy strategy = DistributionStrategy.SEQUENTIAL;
        public int channelColor = 0xFFFFFFFF;
        public int priority = 0, customBulkSize = 0;
        public boolean isBlacklist = true;
        public final Set<Item> filterItems = new HashSet<>();
        public final int[] rrCursor = new int[1];

        public boolean isDefault() {
            return mode == ConnectionMode.DISABLED && strategy == DistributionStrategy.SEQUENTIAL &&
                channelColor == 0xFFFFFFFF && priority == 0 && customBulkSize == 0 &&
                isBlacklist && filterItems.isEmpty();
        }

        public boolean allowsOutput() {
            return mode == ConnectionMode.OUTPUT || mode == ConnectionMode.BOTH;
        }

        public boolean allowsInput() {
            return mode == ConnectionMode.INPUT || mode == ConnectionMode.BOTH;
        }

        public int getRenderColor(TransferType type) {
            return (channelColor == 0xFFFFFFFF || (channelColor >> 24) == 0) ? type.getColor() : channelColor;
        }
    }

    private void updateCache() {
        if (!cacheDirty) return;
        cachedSpeedMult = 1;
        cachedRangeMult = 1;
        cachedStackMult = 1;
        cachedDimEffective = false;

        for (int i = 0; i < upgrades.getSlots(); i++) {
            ItemStack stack = upgrades.getStackInSlot(i);
            if (stack.getItem() instanceof UpgradeItem upgrade) {
                int bonus = (upgrade.getTier() != null) ? upgrade.getTier().getMultiplier() : 0;
                switch (upgrade.getType()) {
                    case SPEED -> cachedSpeedMult += bonus;
                    case RANGE -> cachedRangeMult += bonus;
                    case STACK -> cachedStackMult += bonus;
                    case DIMENSION -> cachedDimEffective = true;
                }
            }
        }
        cachedSpeedMult = Math.max(1, cachedSpeedMult);
        cacheDirty = false;
    }

    public void setOnDirty(Consumer<FaceConfig> onDirty) {
        this.onDirty = onDirty;
    }

    public void markDirty() {
        this.cacheDirty = true;
        this.onDirty.accept(this);
    }

    public int getSpeedMultiplier() {
        updateCache();
        return cachedSpeedMult;
    }

    public int getMaxRangeMultiplier() {
        updateCache();
        return cachedRangeMult;
    }

    public int getStackMultiplier() {
        updateCache();
        return cachedStackMult;
    }

    public boolean isDimensionEffective() {
        updateCache();
        return cachedDimEffective;
    }

    public SideData getSettings(TransferType type) {
        return typeSettings.get(type);
    }

    public CompoundTag serializeNBT(HolderLookup.Provider p) {
        CompoundTag nbt = new CompoundTag();
        nbt.put("upgrades", upgrades.serializeNBT(p));
        CompoundTag typesNbt = new CompoundTag();
        typeSettings.forEach((type, data) -> {
            if (data.isDefault()) return;
            CompoundTag d = new CompoundTag();
            d.putString("mode", data.mode.name());
            d.putString("strategy", data.strategy.name());
            d.putInt("channel", data.channelColor);
            d.putBoolean("blacklist", data.isBlacklist);
            d.putInt("priority", data.priority);
            d.putInt("bulk", data.customBulkSize);
            d.putInt("rr_cursor", data.rrCursor[0]);
            if (!data.filterItems.isEmpty()) {
                ListTag list = new ListTag();
                data.filterItems.forEach(item -> list.add(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(item).toString())));
                d.put("filter", list);
            }
            typesNbt.put(type.getSerializedName(), d);
        });
        nbt.put("types", typesNbt);
        return nbt;
    }

    public void deserializeNBT(HolderLookup.Provider p, CompoundTag nbt) {
        if (nbt.contains("upgrades")) upgrades.deserializeNBT(p, nbt.getCompound("upgrades"));
        CompoundTag typesNbt = nbt.getCompound("types");
        typeSettings.forEach((type, data) -> {
            String key = type.getSerializedName();
            if (typesNbt.contains(key)) {
                CompoundTag d = typesNbt.getCompound(key);
                data.mode = parseEnum(ConnectionMode.class, d.getString("mode"), ConnectionMode.DISABLED);
                data.strategy = parseEnum(DistributionStrategy.class, d.getString("strategy"), DistributionStrategy.SEQUENTIAL);
                data.channelColor = d.getInt("channel");
                data.isBlacklist = d.getBoolean("blacklist");
                data.priority = d.getInt("priority");
                data.customBulkSize = d.getInt("bulk");
                data.rrCursor[0] = d.getInt("rr_cursor");
                data.filterItems.clear();
                ListTag list = d.getList("filter", Tag.TAG_STRING);
                for (int i = 0; i < list.size(); i++) {
                    ResourceLocation rl = ResourceLocation.tryParse(list.getString(i));
                    if (rl != null) BuiltInRegistries.ITEM.getOptional(rl).ifPresent(data.filterItems::add);
                }
            } else {
                data.mode = ConnectionMode.DISABLED;
                data.strategy = DistributionStrategy.SEQUENTIAL;
                data.channelColor = 0xFFFFFFFF;
                data.filterItems.clear();
            }
        });
        this.cacheDirty = true;
    }

    private <E extends Enum<E>> E parseEnum(Class<E> clazz, String name, E defaultValue) {
        try {
            return Enum.valueOf(clazz, name);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public ItemStackHandler getUpgrades() {
        return upgrades;
    }
}