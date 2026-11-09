package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.logistics.LogisticsUpgrade;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import com.coobird.staticlogistics.transfer.UpgradeType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.function.Consumer;

/// 过滤器配置 —— 每个通道一个过滤槽位，支持基础过滤器/Tag过滤器/NBT过滤器三种类型。
/// 通过 hasFilterUpgrade() 判断是否有任何过滤器生效。
public class FilterConfig {
    private final ItemStackHandler upgrades = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof LogisticsUpgrade upgrade && upgrade.isFilterUpgrade();
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
            if (tag.getInt("Size") != 1) throw new IllegalArgumentException("Invalid channel filter format");
            ItemStackHandler decoded = new ItemStackHandler(1);
            decoded.deserializeNBT(provider, tag);
            ItemStack stack = decoded.getStackInSlot(0);
            if (!stack.isEmpty() && (!isItemValid(0, stack) || stack.getCount() != 1))
                throw new IllegalArgumentException("Invalid channel filter");
            setStackInSlot(0, stack);
            normalizeStoredData();
        }

        @Override
        protected void onLoad() {
            super.onLoad();
            normalizeStoredData();
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public int getStackLimit(int slot, ItemStack stack) {
            if (stack.getItem() instanceof LogisticsUpgrade upgrade) {
                UpgradeType type = upgrade.getType();
                if (type == UpgradeType.FILTER) {
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

    /// 载入旧数据时按升级类型清理不参与匹配的物品组件。
    private void normalizeStoredData() {
        for (int slot = 0; slot < upgrades.getSlots(); slot++) {
            ItemStack stack = upgrades.getStackInSlot(slot);
            if (!(stack.getItem() instanceof LogisticsUpgrade upgrade)
                || !upgrade.isFilterUpgrade()) continue;
            FilterData current = stack.getOrDefault(
                SLDataComponents.FILTER_DATA.get(), FilterData.EMPTY);
            FilterData normalized = current.normalizedFor(upgrade.getType());
            if (normalized != current) {
                stack.set(SLDataComponents.FILTER_DATA.get(), normalized);
            }
        }
    }

    /// 将过滤条件写入权威升级物；升级类型不合法时不产生修改。
    public boolean applyFilterData(ItemStack stack, FilterData data) {
        if (!(stack.getItem() instanceof LogisticsUpgrade upgrade) || !upgrade.isFilterUpgrade()) {
            return false;
        }
        stack.set(SLDataComponents.FILTER_DATA.get(), data.normalizedFor(upgrade.getType()));
        markDirty();
        return true;
    }

    /// 检查是否有任何类型的过滤器升级卡（基础/Tag/NBT）
    public boolean hasFilterUpgrade() {
        for (int i = 0; i < 1; i++) {
            ItemStack stack = upgrades.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof LogisticsUpgrade upgrade) {
                UpgradeType type = upgrade.getType();
                if (type == UpgradeType.FILTER) {
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
        return upgrades.getStackInSlot(0).isEmpty();
    }

    /// 以快照完整替换过滤升级物及其数据组件。
    void restoreSnapshot(HolderLookup.Provider provider, FilterConfig snapshot) {
        if (provider == null || snapshot == null) {
            throw new IllegalArgumentException("Filter snapshot provider and value are required");
        }
        upgrades.deserializeNBT(provider, snapshot.upgrades.serializeNBT(provider));
    }
}
