package com.coobird.staticlogistics.storage.config;

import com.coobird.staticlogistics.api.type.UpgradeType;
import com.coobird.staticlogistics.item.UpgradeItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.function.Consumer;

/**
 * 过滤器配置 —— 两个升级槽位，支持基础过滤器/Tag过滤器/NBT过滤器三种类型。
 * 通过 {@code hasFilterUpgrade()} 判断是否有任何过滤器生效。
 */
public class FilterConfig {
    private final ItemStackHandler upgrades = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public int getStackLimit(int slot, ItemStack stack) {
            if (stack.getItem() instanceof UpgradeItem upgrade) {
                UpgradeType type = upgrade.getType();
                if (type == UpgradeType.BASIC_FILTER || type == UpgradeType.TAG_FILTER || type == UpgradeType.NBT_FILTER) {
                    return 1;
                }
            }
            return super.getStackLimit(slot, stack);
        }
    };

    public FilterConfig() {
    }

    public ItemStackHandler getUpgrades() {
        return upgrades;
    }

    /**
     * 检查是否有任何类型的过滤器升级卡（基础/Tag/NBT）
     */
    public boolean hasFilterUpgrade() {
        for (int i = 0; i < 2; i++) {
            ItemStack stack = upgrades.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof UpgradeItem upgrade) {
                UpgradeType type = upgrade.getType();
                if (type == UpgradeType.BASIC_FILTER || type == UpgradeType.TAG_FILTER || type == UpgradeType.NBT_FILTER) {
                    return true;
                }
            }
        }
        return false;
    }

    private void markDirty() {
        if (onDirty != null) onDirty.accept(this);
    }

    private Consumer<FilterConfig> onDirty = (c) -> {
    };

    public void setOnDirty(Consumer<FilterConfig> onDirty) {
        this.onDirty = onDirty;
    }

    public boolean isDefault() {
        return upgrades.getStackInSlot(0).isEmpty() && upgrades.getStackInSlot(1).isEmpty();
    }
}