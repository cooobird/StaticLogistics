package com.coobird.staticlogistics.logistics.blueprint;

import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.logistics.LogisticsUpgrade;
import com.coobird.staticlogistics.transfer.UpgradeTier;
import com.coobird.staticlogistics.transfer.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import java.util.HashSet;
import java.util.Set;

/**
 * 对来自物品组件或旧存档的蓝图数据执行边界与内容校验。
 */
public final class BlueprintDataValidator {
    public static final int MAX_BLUEPRINT_VOLUME = 4096;
    public static final int MAX_UNDO_WORK_ITEMS = 65_536;

    private BlueprintDataValidator() {
    }

    public static boolean isValid(BlueprintData data) {
        if (data.schemaVersion() != BlueprintData.CURRENT_SCHEMA_VERSION) return false;
        try {
            GroupConstraints.normalizeName(data.groupId());
        } catch (IllegalArgumentException exception) {
            return false;
        }
        if (data.blocks().isEmpty() || data.blocks().size() > BlueprintData.MAX_BLOCKS) return false;

        Set<BlockPos> positions = new HashSet<>();
        int totalFaces = 0;
        int totalLinks = 0;
        for (BlueprintData.BlockEntry entry : data.blocks()) {
            if (entry == null || !positions.add(entry.relativePos()) || entry.faces().size() > 6) return false;
            if (entry.linkedTo().size() > BlueprintData.MAX_LINKS_PER_FACE) return false;
            if (!isUpgradeDataValid(entry.containerUpgrades(), false)) return false;
            BlockPos relative = entry.relativePos();
            if (Math.abs((long) relative.getX()) > MAX_BLUEPRINT_VOLUME
                || Math.abs((long) relative.getY()) > MAX_BLUEPRINT_VOLUME
                || Math.abs((long) relative.getZ()) > MAX_BLUEPRINT_VOLUME) return false;
            totalFaces += entry.faces().size();
            if (totalFaces > BlueprintData.MAX_BLOCKS * 6) return false;
            for (BlueprintData.FaceEntry face : entry.faces().values()) {
                if (face == null || face.linkedTo().size() > BlueprintData.MAX_LINKS_PER_FACE) return false;
                if (!isUpgradeDataValid(face.filterUpgrades(), true)) return false;
                totalLinks += face.linkedTo().isEmpty()
                    ? entry.linkedTo().size() : face.linkedTo().size();
                if (totalLinks > BlueprintData.MAX_BLOCKS * 6) return false;
            }
        }
        long estimatedUndoWork = (long) data.blocks().size() * 2L
            + (long) totalFaces * 3L + (long) totalLinks * 3L;
        return estimatedUndoWork <= MAX_UNDO_WORK_ITEMS;
    }

    private static boolean isUpgradeDataValid(CompoundTag tag, boolean filter) {
        if (tag == null || tag.isEmpty()) return true;
        int slotCount = filter ? 2 : 3;
        ListTag rawItems = tag.getList("Items", Tag.TAG_COMPOUND);
        if (rawItems.size() > slotCount) return false;
        boolean[] seenSlots = new boolean[slotCount];
        for (int index = 0; index < rawItems.size(); index++) {
            int slot = rawItems.getCompound(index).getInt("Slot");
            if (slot < 0 || slot >= slotCount || seenSlots[slot]) return false;
            seenSlots[slot] = true;
        }

        ItemStackHandler handler = new ItemStackHandler(slotCount);
        try {
            handler.deserializeNBT(tag);
        } catch (RuntimeException exception) {
            return false;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof LogisticsUpgrade upgrade)
                || stack.getCount() <= 0 || stack.getCount() > stack.getMaxStackSize()) return false;
            if (filter) {
                if (!upgrade.isFilterUpgrade() || stack.getCount() > 1) return false;
            } else if (slot == 0 && upgrade.getType() != UpgradeType.SPEED
                || slot == 1 && upgrade.getType() != UpgradeType.RANGE
                && upgrade.getType() != UpgradeType.DIMENSION
                || slot == 2 && upgrade.getType() != UpgradeType.STACK) {
                return false;
            }
            if (!filter && (upgrade.getTier() == UpgradeTier.NETHER_STAR
                || upgrade.getType() == UpgradeType.DIMENSION) && stack.getCount() > 1) return false;
        }
        return true;
    }
}
