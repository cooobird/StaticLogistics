package com.coobird.staticlogistics.logistics.node;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * 玩家主动删除物流配置时的升级物移交策略。
 *
 * <p>先尝试放入玩家主物品栏，剩余部分在玩家当前位置掉落；事务未提交时恢复
 * 玩家物品栏、移除已生成实体并把升级放回原槽位。
 */
public final class PlayerUpgradeHandoff implements NodeLifecycleService.UpgradeHandoff {
    private final ServerPlayer player;

    public PlayerUpgradeHandoff(ServerPlayer player) {
        if (player == null) throw new IllegalArgumentException("Upgrade recipient is required");
        this.player = player;
    }

    @Override
    public NodeLifecycleService.HandoffReceipt begin(
        List<NodeLifecycleService.UpgradeSource> sources
    ) {
        List<ItemStack> inventorySnapshot = player.getInventory().items.stream()
            .map(ItemStack::copy).toList();
        List<ExtractedUpgrade> extracted = new ArrayList<>();
        List<ItemEntity> spawned = new ArrayList<>();
        try {
            extract(sources, extracted);
            for (ExtractedUpgrade upgrade : extracted) {
                ItemStack remainder = upgrade.stack().copy();
                player.getInventory().add(remainder);
                if (remainder.isEmpty()) continue;
                ItemEntity entity = new ItemEntity(
                    player.serverLevel(), player.getX(), player.getY() + 0.25D, player.getZ(),
                    remainder.copy());
                entity.setDefaultPickUpDelay();
                if (!player.serverLevel().addFreshEntity(entity)) {
                    throw new IllegalStateException("Upgrade entity insertion was rejected");
                }
                spawned.add(entity);
            }
            player.getInventory().setChanged();
            return new PlayerReceipt(
                player, inventorySnapshot, extracted, spawned);
        } catch (RuntimeException exception) {
            rollback(player, inventorySnapshot, extracted, spawned, exception);
            throw new IllegalStateException("Failed to hand off upgrades to player", exception);
        }
    }

    private static void extract(
        List<NodeLifecycleService.UpgradeSource> sources,
        List<ExtractedUpgrade> extracted
    ) {
        for (NodeLifecycleService.UpgradeSource source : sources) {
            IItemHandler inventory = source.inventory();
            for (int slot = source.firstSlot(); slot < source.slotLimit(); slot++) {
                ItemStack stack = inventory.extractItem(slot, Integer.MAX_VALUE, false);
                if (!stack.isEmpty()) {
                    extracted.add(new ExtractedUpgrade(inventory, slot, stack));
                }
            }
        }
    }

    private static void rollback(
        ServerPlayer player,
        List<ItemStack> inventorySnapshot,
        List<ExtractedUpgrade> extracted,
        List<ItemEntity> spawned,
        RuntimeException failure
    ) {
        spawned.forEach(ItemEntity::discard);
        for (int slot = 0; slot < inventorySnapshot.size(); slot++) {
            player.getInventory().items.set(slot, inventorySnapshot.get(slot).copy());
        }
        player.getInventory().setChanged();
        for (int index = extracted.size() - 1; index >= 0; index--) {
            ExtractedUpgrade upgrade = extracted.get(index);
            ItemStack remainder = upgrade.inventory()
                .insertItem(upgrade.slot(), upgrade.stack(), false);
            if (!remainder.isEmpty()) {
                failure.addSuppressed(
                    new IllegalStateException("Failed to restore an extracted upgrade"));
            }
        }
    }

    private record ExtractedUpgrade(IItemHandler inventory, int slot, ItemStack stack) {
    }

    private static final class PlayerReceipt implements NodeLifecycleService.HandoffReceipt {
        private final ServerPlayer player;
        private final List<ItemStack> inventorySnapshot;
        private final List<ExtractedUpgrade> extracted;
        private final List<ItemEntity> spawned;
        private boolean committed;

        private PlayerReceipt(
            ServerPlayer player,
            List<ItemStack> inventorySnapshot,
            List<ExtractedUpgrade> extracted,
            List<ItemEntity> spawned
        ) {
            this.player = player;
            this.inventorySnapshot = inventorySnapshot;
            this.extracted = extracted;
            this.spawned = spawned;
        }

        @Override
        public void commit() {
            committed = true;
        }

        @Override
        public void close() {
            if (committed) return;
            IllegalStateException failure =
                new IllegalStateException("Player upgrade handoff was not committed");
            rollback(player, inventorySnapshot, extracted, spawned, failure);
            if (failure.getSuppressed().length > 0) throw failure;
        }
    }
}
