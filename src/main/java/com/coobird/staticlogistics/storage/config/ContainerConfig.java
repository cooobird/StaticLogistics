package com.coobird.staticlogistics.storage.config;

import com.coobird.staticlogistics.api.type.UpgradeTier;
import com.coobird.staticlogistics.api.type.UpgradeType;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.item.UpgradeItem;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.slf4j.Logger;

import java.util.function.Consumer;

/**
 * 容器升级配置 —— 管理速度/范围/堆叠三种升级的倍率计算和缓存。
 * 也记录是否装了跨维度升级卡，以及该容器关联了哪些面。
 */
public class ContainerConfig {
    private static final Logger LOGGER = LogUtils.getLogger();

    private int cachedSpeedMult = 1;
    private int cachedRangeMult = 1;
    private int cachedStackMult = 1;
    private boolean cachedDimEffective = false;
    private boolean cacheDirty = true;
    private long configGenAtCache = -1; // 追踪配置代数，重载时自动失效
    public static final int INFINITY_MARKER = Integer.MAX_VALUE;
    private BlockPos pos = BlockPos.ZERO;

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

    public BlockPos getPos() {
        return pos;
    }

    public void setPos(BlockPos pos) {
        this.pos = pos;
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

    /**
     * 重新计算所有升级卡的倍率并更新缓存。
     * cacheDirty 为 true，或配置已重载（configGeneration 变化）时重新计算。
     */
    private void updateCache() {
        if (!cacheDirty && configGenAtCache == SLConfig.configGeneration) return;
        configGenAtCache = SLConfig.configGeneration;

        long speed = 1L, range = 1L, stack = 1L;
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
                    case SPEED -> {
                        speed = multiplyWithOverflowCheck(speed, totalValue);
                    }
                    case RANGE -> {
                        range = multiplyWithOverflowCheck(range, totalValue);
                    }
                    case STACK -> {
                        stack = multiplyWithOverflowCheck(stack, totalValue);
                    }
                }
                LOGGER.debug("Upgrade: type={}, tier={}, count={}, totalValue={}, range now={}",
                    type, tier, count, totalValue, range);
            } else if (type == UpgradeType.DIMENSION) {
                dim = true;
                LOGGER.debug("Dimension upgrade found");
            }
        }

        this.cachedSpeedMult = (int) Math.min(speed, INFINITY_MARKER);
        this.cachedRangeMult = (int) Math.min(range, INFINITY_MARKER);
        this.cachedStackMult = (int) Math.min(stack, INFINITY_MARKER);
        this.cachedDimEffective = dim;
        this.cacheDirty = false;

        LOGGER.debug("ContainerConfig cache updated: speed={}, range={}, stack={}, dim={}",
            cachedSpeedMult, cachedRangeMult, cachedStackMult, cachedDimEffective);
    }

    /**
     * 带溢出检测的乘法：结果超过 INFINITY_MARKER 就返回 INFINITY_MARKER
     */
    private long multiplyWithOverflowCheck(long a, long b) {
        if (a == 0 || b == 0) return 0;
        long result = a * b;
        if (result / b != a || result >= INFINITY_MARKER) {
            return INFINITY_MARKER;
        }
        return result;
    }

    /**
     * 标记缓存失效并触发变更回调
     */
    public void markDirty() {
        this.cacheDirty = true;
        if (onDirty != null) onDirty.accept(this);
    }

    public void setOnDirty(Consumer<ContainerConfig> onDirty) {
        this.onDirty = onDirty;
    }

    /**
     * 没有任何升级卡就是默认（空）配置
     */
    public boolean isDefault() {
        for (int i = 0; i < upgrades.getSlots(); i++) {
            if (!upgrades.getStackInSlot(i).isEmpty()) return false;
        }
        return true;
    }
}