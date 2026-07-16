package com.coobird.staticlogistics.logistics.blueprint;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 蓝图升级材料的统计、库存检查与扣除规则。
 */
public final class BlueprintUpgradeInventory {
    private BlueprintUpgradeInventory() {
    }

    public static Map<String, Integer> tally(BlueprintData data) {
        Map<String, Integer> needed = new LinkedHashMap<>();
        for (BlueprintData.BlockEntry entry : data.blocks()) {
            tallyFromHandler(needed, entry.containerUpgrades());
            for (BlueprintData.FaceEntry face : entry.faces().values()) {
                tallyFromHandler(needed, face.filterUpgrades());
            }
        }
        return needed;
    }

    public static int count(Player player, String itemId) {
        if (player == null) return 0;
        int count = 0;
        var inventory = player.getInventory();
        for (int index = 0; index < inventory.getContainerSize(); index++) {
            ItemStack stack = inventory.getItem(index);
            if (!stack.isEmpty() && itemId(stack).equals(itemId)) {
                count = saturatedAdd(count, stack.getCount());
            }
        }
        return count;
    }

    public static int consume(Player player, Map<String, Integer> needed) {
        int missing = 0;
        for (Map.Entry<String, Integer> entry : needed.entrySet()) {
            int remaining = entry.getValue();
            var inventory = player.getInventory();
            for (int index = 0; index < inventory.getContainerSize() && remaining > 0; index++) {
                ItemStack stack = inventory.getItem(index);
                if (!stack.isEmpty() && itemId(stack).equals(entry.getKey())) {
                    int take = Math.min(remaining, stack.getCount());
                    stack.shrink(take);
                    remaining -= take;
                }
            }
            missing = saturatedAdd(missing, remaining);
        }
        return missing;
    }

    /** 统计即将被蓝图覆盖的权威升级物。 */
    public static void tallyInventory(Map<String, Integer> tally, IItemHandler inventory) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) mergeCount(tally, itemId(stack), stack.getCount());
        }
    }

    /** 只有目标最终仍包含现有材料时才允许覆盖，避免旧升级物被静默吞掉。 */
    public static boolean isCoveredByDesired(Map<String, Integer> existing,
                                             Map<String, Integer> desired) {
        return existing.entrySet().stream()
            .allMatch(entry -> entry.getValue() <= desired.getOrDefault(entry.getKey(), 0));
    }

    /** 返回在复用现有升级物之后仍需从玩家背包扣除的净增量。 */
    public static Map<String, Integer> requiredDelta(Map<String, Integer> desired,
                                                     Map<String, Integer> existing) {
        Map<String, Integer> result = new LinkedHashMap<>();
        desired.forEach((itemId, count) -> {
            int missing = Math.max(0, count - existing.getOrDefault(itemId, 0));
            if (missing > 0) result.put(itemId, missing);
        });
        return Map.copyOf(result);
    }

    private static void tallyFromHandler(Map<String, Integer> needed, CompoundTag nbt) {
        if (nbt.isEmpty()) return;
        var items = nbt.getList("Items", 10);
        for (int index = 0; index < items.size(); index++) {
            CompoundTag itemTag = items.getCompound(index);
            if (itemTag.isEmpty()) continue;
            mergeCount(needed, itemTag.getString("id"), itemTag.getInt("count"));
        }
    }

    private static void mergeCount(Map<String, Integer> tally, String itemId, int count) {
        if (itemId == null || itemId.isEmpty() || count <= 0) return;
        tally.merge(itemId, count, BlueprintUpgradeInventory::saturatedAdd);
    }

    private static int saturatedAdd(int left, int right) {
        long sum = (long) left + right;
        return sum >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, sum);
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
