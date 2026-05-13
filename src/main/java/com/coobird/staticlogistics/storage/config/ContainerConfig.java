package com.coobird.staticlogistics.storage.config;

import com.coobird.staticlogistics.api.type.UpgradeTier;
import com.coobird.staticlogistics.api.type.UpgradeType;
import com.coobird.staticlogistics.item.UpgradeItem;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.function.Consumer;

public class ContainerConfig {
    private int cachedSpeedMult = 1;
    private int cachedRangeMult = 1;
    private int cachedStackMult = 1;
    private boolean cachedDimEffective = false;
    private boolean cacheDirty = true;
    public static final int INFINITY_MARKER = 1000000;

    private final ItemStackHandler upgrades = new ItemStackHandler(3) {
        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
        }

        @Override
        public int getStackLimit(int slot, ItemStack stack) {
            if (stack.getItem() instanceof UpgradeItem upgrade) {
                if (upgrade.getTier() == UpgradeTier.NETHER_STAR) return 1;
                if (upgrade.getType() == UpgradeType.DIMENSION) return 1;
            }
            return super.getSlotLimit(slot);
        }
    };

    private Consumer<ContainerConfig> onDirty = (c) -> {
    };
    private final LongSet linkedFaceKeys = new LongOpenHashSet();

    public ContainerConfig() {
    }

    public LongSet getLinkedFaceKeys() {
        return linkedFaceKeys;
    }

    public void linkFace(long faceKey) {
        linkedFaceKeys.add(faceKey);
    }

    public void unlinkFace(long faceKey) {
        linkedFaceKeys.remove(faceKey);
    }

    public int getSpeedMultiplier() {
        updateCache();
        return cachedSpeedMult;
    }

    public int getRangeMultiplier() {
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

    public ItemStackHandler getUpgrades() {
        return upgrades;
    }

    private void updateCache() {
        if (!cacheDirty) return;

        long speed = 1, range = 1, stack = 1;
        boolean dim = false;

        for (int i = 0; i < upgrades.getSlots(); i++) {
            ItemStack stackInSlot = upgrades.getStackInSlot(i);
            if (stackInSlot.isEmpty() || !(stackInSlot.getItem() instanceof UpgradeItem upgrade)) continue;

            UpgradeType type = upgrade.getType();
            UpgradeTier tier = upgrade.getTier();

            if (tier != null) {
                long multiplier = tier.getMultiplier();
                long count = stackInSlot.getCount();
                long totalValue = multiplier * count;

                switch (type) {
                    case SPEED -> speed += totalValue;
                    case RANGE -> range += totalValue;
                    case STACK -> stack += totalValue;
                }
            } else if (type == UpgradeType.DIMENSION) {
                dim = true;
            }
        }

        this.cachedSpeedMult = (int) Math.min(speed, 2048);
        this.cachedRangeMult = (int) Math.min(range, INFINITY_MARKER);
        this.cachedStackMult = (int) Math.min(stack, Integer.MAX_VALUE);

        this.cachedDimEffective = dim;
        this.cacheDirty = false;
    }

    public void markDirty() {
        this.cacheDirty = true;
        if (onDirty != null) onDirty.accept(this);
    }

    public void setOnDirty(Consumer<ContainerConfig> onDirty) {
        this.onDirty = onDirty;
    }

    public boolean isDefault() {
        for (int i = 0; i < upgrades.getSlots(); i++) {
            if (!upgrades.getStackInSlot(i).isEmpty()) return false;
        }
        return true;
    }
}