package com.coobird.staticlogistics.core;

import com.coobird.staticlogistics.SLConfig;
import com.coobird.staticlogistics.common.item.UpgradeItem;
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
    private int cachedSpeedMult = 1;
    private int cachedStackMult = 1;
    private boolean cachedDimEffective = false;
    private boolean cacheDirty = true;
    private Consumer<FaceConfig> onDirty = (c) -> {};

    private final ItemStackHandler upgrades = new ItemStackHandler(4) {
        @Override
        protected void onContentsChanged(int slot) {
            cacheDirty = true;
            onDirty.accept(FaceConfig.this);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (!(stack.getItem() instanceof UpgradeItem upgrade)) return false;
            return switch (slot) {
                case 0 -> upgrade.getType() == UpgradeItem.UpgradeType.SPEED;
                case 1 -> upgrade.getType() == UpgradeItem.UpgradeType.RANGE;
                case 2 -> upgrade.getType() == UpgradeItem.UpgradeType.STACK;
                case 3 -> upgrade.getType() == UpgradeItem.UpgradeType.DIMENSION;
                default -> false;
            };
        }
    };

    private final Map<TransferType, SideData> typeSettings = new EnumMap<>(TransferType.class);

    public FaceConfig() {
        for (TransferType type : TransferType.values()) {
            typeSettings.put(type, new SideData(this));
        }
    }

    public void setOnDirty(Consumer<FaceConfig> listener) {
        this.onDirty = listener;
    }

    public SideData getSettings(TransferType type) {
        return typeSettings.get(type);
    }

    public ItemStackHandler getUpgrades() {
        return upgrades;
    }

    private void updateCache() {
        if (!cacheDirty) return;

        this.cachedSpeedMult = calculateMultiplier(0, 1);
        this.cachedStackMult = calculateMultiplier(2, 1);

        ItemStack rangeStack = upgrades.getStackInSlot(1);
        ItemStack dimStack = upgrades.getStackInSlot(3);

        boolean hasDimCard = !dimStack.isEmpty() && dimStack.getItem() instanceof UpgradeItem;

        boolean rangeMaxed = false;
        if (!rangeStack.isEmpty() && rangeStack.getItem() instanceof UpgradeItem u) {
            rangeMaxed = rangeStack.getCount() >= 64 || u.getTier() == UpgradeItem.UpgradeTier.CREATIVE;
        }

        this.cachedDimEffective = hasDimCard && rangeMaxed;
        this.cacheDirty = false;
    }

    private int calculateMultiplier(int slot, int defaultValue) {
        ItemStack s = upgrades.getStackInSlot(slot);
        if (s.getItem() instanceof UpgradeItem u && u.getTier() != null) {
            String tierName = u.getTier().getSerializedName();
            if (tierName.equalsIgnoreCase("creative")) return Integer.MAX_VALUE;
            return SLConfig.getMultiplierForTier(tierName);
        }
        return defaultValue;
    }

    public int getStackMultiplier() {
        updateCache();
        return cachedStackMult;
    }

    public int getSpeedMultiplier() {
        updateCache();
        return cachedSpeedMult;
    }

    public boolean isDimensionEffective() {
        updateCache();
        return cachedDimEffective;
    }

    public static class SideData {
        private final FaceConfig parent;
        public ConnectionMode mode = ConnectionMode.DISABLED;
        public int channelColor = -1;
        public boolean isBlacklist = false;
        public final Set<Item> filterItems = new HashSet<>();
        public int priority = 0;
        public int customBulkSize = -1;
        public DistributionStrategy strategy = DistributionStrategy.SEQUENTIAL;
        public final int[] rrCursor = new int[]{0};

        public SideData(FaceConfig parent) {
            this.parent = parent;
        }

        public int getRenderColor(TransferType type) {
            return (channelColor != -1) ? channelColor : type.getRenderColor(mode);
        }
    }

    public CompoundTag serializeNBT(HolderLookup.Provider p) {
        CompoundTag nbt = new CompoundTag();
        nbt.put("upgrades", upgrades.serializeNBT(p));
        CompoundTag typesNbt = new CompoundTag();
        typeSettings.forEach((type, data) -> {
            CompoundTag d = new CompoundTag();
            d.putString("mode", data.mode.name());
            d.putInt("channel", data.channelColor);
            d.putBoolean("blacklist", data.isBlacklist);
            d.putInt("priority", data.priority);
            d.putInt("bulk", data.customBulkSize);
            d.putString("strategy", data.strategy.name());
            d.putInt("rr_cursor", data.rrCursor[0]);
            ListTag list = new ListTag();
            data.filterItems.forEach(item -> list.add(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(item).toString())));
            d.put("filter", list);
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
                try {
                    data.mode = ConnectionMode.valueOf(d.getString("mode"));
                } catch (Exception e) {
                    data.mode = ConnectionMode.DISABLED;
                }
                data.channelColor = d.getInt("channel");
                data.isBlacklist = d.getBoolean("blacklist");
                data.priority = d.getInt("priority");
                data.customBulkSize = d.getInt("bulk");
                try {
                    data.strategy = DistributionStrategy.valueOf(d.getString("strategy"));
                } catch (Exception e) {
                    data.strategy = DistributionStrategy.SEQUENTIAL;
                }
                data.rrCursor[0] = d.getInt("rr_cursor");
                data.filterItems.clear();
                ListTag list = d.getList("filter", Tag.TAG_STRING);
                for (int i = 0; i < list.size(); i++) {
                    ResourceLocation rl = ResourceLocation.parse(list.getString(i));
                    BuiltInRegistries.ITEM.getOptional(rl).ifPresent(data.filterItems::add);
                }
            }
        });
        this.cacheDirty = true;
    }
}