package com.coobird.staticlogistics.content.item;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.network.s2c.S2CStoredSelectionPage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

public final class StoredSelectionSync {
    private StoredSelectionSync() {
    }

    public static void publish(ServerPlayer player, ItemStack stack) {
        List<LogisticsNode> nodes = stack.getOrDefault(SLDataComponents.STORED_NODES.get(), List.of());
        if (nodes.isEmpty()) {
            stack.remove(SLDataComponents.STORED_SELECTION_ID.get());
            return;
        }
        UUID id = UUID.randomUUID();
        stack.set(SLDataComponents.STORED_SELECTION_ID.get(), id);
        BulkWorkScheduler.replace(player, stack, new BulkWorkScheduler.Step() {
            private int offset;

            public boolean run(ServerPlayer current, long deadline) {
                if (!id.equals(stack.get(SLDataComponents.STORED_SELECTION_ID.get()))) return true;
                int end = Math.min(nodes.size(), offset + S2CStoredSelectionPage.PAGE_SIZE);
                PacketDistributor.sendToPlayer(current, new S2CStoredSelectionPage(id,
                    offset / S2CStoredSelectionPage.PAGE_SIZE, end == nodes.size(), nodes.subList(offset, end)));
                offset = end;
                return offset == nodes.size();
            }
        });
    }
}
