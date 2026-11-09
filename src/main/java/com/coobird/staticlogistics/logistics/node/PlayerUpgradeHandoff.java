package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.logistics.group.LogisticsOperationStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/// 删除物流配置时统一把升级物写入持久交付队列；在线时立即尝试放入背包。
public final class PlayerUpgradeHandoff implements NodeLifecycleService.UpgradeHandoff {
    private final MinecraftServer server;
    private final UUID playerId;

    public PlayerUpgradeHandoff(ServerPlayer player) {
        this(player == null ? null : player.server, player == null ? null : player.getUUID());
    }

    public PlayerUpgradeHandoff(MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null) {
            throw new IllegalArgumentException("Upgrade delivery server and player are required");
        }
        this.server = server;
        this.playerId = playerId;
    }

    @Override
    public NodeLifecycleService.HandoffReceipt begin(
        List<NodeLifecycleService.UpgradeSource> sources
    ) {
        List<ExtractedUpgrade> extracted = new ArrayList<>();
        try {
            for (NodeLifecycleService.UpgradeSource source : sources) {
                IItemHandler inventory = source.inventory();
                for (int slot = source.firstSlot(); slot < source.slotLimit(); slot++) {
                    ItemStack stack = inventory.extractItem(slot, Integer.MAX_VALUE, false);
                    if (!stack.isEmpty()) {
                        extracted.add(new ExtractedUpgrade(inventory, slot, stack));
                    }
                }
            }
            List<ItemStack> delivery = extracted.stream()
                .map(upgrade -> upgrade.stack().copy()).toList();
            LogisticsOperationStore store = LogisticsOperationStore.get(server);
            UUID operationId = store.queueItemDelivery(playerId, delivery);
            return new Receipt(server, store, playerId, extracted, operationId);
        } catch (RuntimeException failure) {
            restore(extracted, failure);
            throw new IllegalStateException("Failed to prepare upgrade delivery", failure);
        }
    }

    private static void restore(List<ExtractedUpgrade> extracted,
                                RuntimeException failure) {
        for (int index = extracted.size() - 1; index >= 0; index--) {
            ExtractedUpgrade upgrade = extracted.get(index);
            ItemStack remainder = upgrade.inventory().insertItem(
                upgrade.slot(), upgrade.stack(), false);
            if (!remainder.isEmpty()) {
                failure.addSuppressed(new IllegalStateException(
                    "Failed to restore an extracted upgrade"));
            }
        }
    }

    private record ExtractedUpgrade(IItemHandler inventory, int slot,
                                    ItemStack stack) {
    }

    private static final class Receipt implements NodeLifecycleService.HandoffReceipt {
        private final MinecraftServer server;
        private final LogisticsOperationStore store;
        private final UUID playerId;
        private final List<ExtractedUpgrade> extracted;
        private final UUID operationId;
        private boolean committed;

        private Receipt(MinecraftServer server, LogisticsOperationStore store,
                        UUID playerId, List<ExtractedUpgrade> extracted,
                        UUID operationId) {
            this.server = server;
            this.store = store;
            this.playerId = playerId;
            this.extracted = extracted;
            this.operationId = operationId;
        }

        @Override
        public void commit() {
            /// 配置已提交删除，物品此后归交付队列所有，不能再退回已移除的配置对象。
            committed = true;
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                store.deliverItemDelivery(player, operationId);
            }
        }

        @Override
        public void close() {
            if (committed) return;
            store.cancelItemDelivery(playerId, operationId);
            IllegalStateException failure = new IllegalStateException(
                "Upgrade delivery was not committed");
            restore(extracted, failure);
            if (failure.getSuppressed().length > 0) throw failure;
        }
    }
}
