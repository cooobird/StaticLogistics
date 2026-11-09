package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.logistics.LogisticsUpgrade;
import com.coobird.staticlogistics.transfer.LogisticsCalculator;
import com.coobird.staticlogistics.transfer.UpgradeTier;
import com.coobird.staticlogistics.transfer.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/// 容器升级配置 —— 管理一张综合升级卡及整个容器共享的倍率缓存。
/// 综合升级由容器共享；下界之星等级提供跨维度能力。
public class ContainerConfig {

    private long cachedSpeedMult = 1;
    private long cachedRangeMult = 1;
    private long cachedStackMult = 1;
    private boolean cachedDimEffective = false;
    private int cachedActualInterval = -1;
    /// 缓存冷却间隔
    private boolean cacheDirty = true;
    private long configGenAtCache = -1;
    /// 追踪配置代数，重载时自动失效
    public static final long INFINITY_MARKER = Long.MAX_VALUE;
    private BlockPos pos = BlockPos.ZERO;
    private long signalTick = Long.MIN_VALUE;
    private boolean powered;

    public boolean allowsRedstone(ServerLevel level, ChannelRedstoneMode mode) {
        if (mode == ChannelRedstoneMode.IGNORE) return true;
        if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return false;
        if (signalTick != level.getGameTime()) {
            signalTick = level.getGameTime();
            powered = level.hasNeighborSignal(pos);
        }
        return mode.allows(powered);
    }

    public static final int UPGRADE_SLOT = 0;
    public static final int UPGRADE_SLOT_COUNT = 1;

    private final ItemStackHandler upgrades = new ItemStackHandler(UPGRADE_SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof LogisticsUpgrade upgrade && upgrade.getType() == UpgradeType.COMBINED && upgrade.getTier() != null;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            if (!stack.isEmpty() && (!isItemValid(slot, stack) || stack.getCount() > 1))
                throw new IllegalArgumentException("Invalid shared container upgrade");
            super.setStackInSlot(slot, stack);
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            if (tag.getInt("Size") != UPGRADE_SLOT_COUNT)
                throw new IllegalArgumentException("Unsupported container upgrade format");
            var entries = tag.getList("Items", Tag.TAG_COMPOUND);
            if (entries.size() > 1 || !entries.isEmpty() && entries.getCompound(0).getInt("Slot") != UPGRADE_SLOT) {
                throw new IllegalArgumentException("Invalid shared upgrade slot");
            }
            ItemStackHandler decoded = new ItemStackHandler(UPGRADE_SLOT_COUNT);
            decoded.deserializeNBT(tag);
            setStackInSlot(UPGRADE_SLOT, decoded.getStackInSlot(UPGRADE_SLOT));
        }
    };

    private Consumer<ContainerConfig> onDirty = (c) -> {
    };
    private final Set<EndpointAddress> linkedFaceKeys = new LinkedHashSet<>();

    public ContainerConfig() {
    }

    public BlockPos getPos() {
        return pos;
    }

    public void setPos(BlockPos pos) {
        this.pos = pos;
        signalTick = Long.MIN_VALUE;
    }

    public Set<EndpointAddress> getLinkedFaceKeys() {
        return Set.copyOf(linkedFaceKeys);
    }

    public void linkFace(EndpointAddress faceKey) {
        linkedFaceKeys.add(faceKey);
    }

    public void unlinkFace(EndpointAddress faceKey) {
        linkedFaceKeys.remove(faceKey);
    }

    public long getSpeedMultiplier() {
        updateCache();
        return cachedSpeedMult;
    }

    public long getRangeMultiplier() {
        updateCache();
        return cachedRangeMult;
    }

    public long getStackMultiplier() {
        updateCache();
        return cachedStackMult;
    }

    public boolean isDimensionEffective() {
        updateCache();
        return cachedDimEffective;
    }

    public int getCachedActualInterval() {
        if (SLConfig.isSimpleMode()) return 1;
        updateCache();
        return cachedActualInterval;
    }

    public ItemStackHandler getUpgrades() {
        return upgrades;
    }

    /// 重新计算所有升级卡的倍率并更新缓存。
    /// cacheDirty 为 true，或配置已重载（configGeneration 变化）时重新计算。
    private void updateCache() {
        if (!cacheDirty && configGenAtCache == SLConfig.configGeneration.get()) return;
        configGenAtCache = SLConfig.configGeneration.get();

        ItemStack installed = upgrades.getStackInSlot(UPGRADE_SLOT);
        UpgradeTier tier = !installed.isEmpty() && installed.getItem() instanceof LogisticsUpgrade upgrade
            && upgrade.getType() == UpgradeType.COMBINED ? upgrade.getTier() : null;
        long multiplier = tier == null ? 1L : Math.max(1L, tier.getMultiplier());
        this.cachedSpeedMult = multiplier;
        this.cachedRangeMult = multiplier;
        this.cachedStackMult = multiplier;
        this.cachedDimEffective = tier == UpgradeTier.NETHER_STAR;
        this.cacheDirty = false;

        /// 预计算冷却间隔
        int baseInterval = SLConfig.getDefaultTickInterval();
        this.cachedActualInterval = LogisticsCalculator.calcSpeedInterval(baseInterval, cachedSpeedMult);

    }

    /// 标记缓存失效并触发变更回调
    public void markDirty() {
        this.cacheDirty = true;
        this.cachedActualInterval = -1;
        if (onDirty != null) onDirty.accept(this);
    }

    public void setOnDirty(Consumer<ContainerConfig> onDirty) {
        this.onDirty = onDirty;
    }

    /// 没有任何升级卡就是默认（空）配置
    public boolean isDefault() {
        for (int i = 0; i < upgrades.getSlots(); i++) {
            if (!upgrades.getStackInSlot(i).isEmpty()) return false;
        }
        return true;
    }
}
