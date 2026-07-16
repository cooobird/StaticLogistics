package com.coobird.staticlogistics.logistics.node;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import java.util.ArrayList;
import java.util.List;

/**
 * 掉落处理器 —— 方块实体被破坏时，将升级卡和过滤器物品掉落为实体。
 */
public class DropHandler {
    private final ServerLevel level;

    public DropHandler(ServerLevel level) {
        this.level = level;
    }

    /** 同一物理方块的全部升级物作为一个批次移交，任一实体生成失败即整体恢复。 */
    public void handoffUpgrades(List<NodeLifecycleService.UpgradeSource> sources) {
        List<ExtractedUpgrade> extracted = new ArrayList<>();
        List<ItemEntity> spawned = new ArrayList<>();
        try {
            for (NodeLifecycleService.UpgradeSource source : sources) {
                IItemHandler inventory = source.inventory();
                for (int slot = 0; slot < inventory.getSlots(); slot++) {
                    ItemStack stack = inventory.extractItem(slot, Integer.MAX_VALUE, false);
                    if (!stack.isEmpty()) {
                        extracted.add(new ExtractedUpgrade(source.pos(), inventory, slot, stack));
                    }
                }
            }
            for (ExtractedUpgrade upgrade : extracted) {
                BlockPos pos = upgrade.pos();
                ItemEntity entity = new ItemEntity(level,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    upgrade.stack().copy());
                entity.setDefaultPickUpDelay();
                if (!level.addFreshEntity(entity)) {
                    throw new IllegalStateException("Upgrade entity insertion was rejected");
                }
                spawned.add(entity);
            }
        } catch (RuntimeException exception) {
            spawned.forEach(ItemEntity::discard);
            for (int index = extracted.size() - 1; index >= 0; index--) {
                ExtractedUpgrade upgrade = extracted.get(index);
                ItemStack remainder = upgrade.inventory()
                    .insertItem(upgrade.slot(), upgrade.stack(), false);
                if (!remainder.isEmpty()) {
                    exception.addSuppressed(new IllegalStateException(
                        "Failed to restore upgrade after handoff failure at "
                            + upgrade.pos() + " slot " + upgrade.slot()));
                }
            }
            throw new IllegalStateException("Failed to hand off upgrade batch", exception);
        }
    }

    private record ExtractedUpgrade(BlockPos pos, IItemHandler inventory, int slot, ItemStack stack) {
    }
}
