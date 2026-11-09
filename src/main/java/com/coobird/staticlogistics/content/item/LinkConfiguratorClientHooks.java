package com.coobird.staticlogistics.content.item;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.selection.NodeSelectionPolicy;
import com.coobird.staticlogistics.network.s2c.S2CStoredSelectionPage;
import com.google.common.base.Supplier;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.BooleanSupplier;

/// 隔离通用物品逻辑与客户端键位系统，避免服务端加载客户端类。
public final class LinkConfiguratorClientHooks {
    private static final Map<UUID, List<LogisticsNode>> selections = new LinkedHashMap<>();
    private static final Map<UUID, Selection> pendingSelections = new LinkedHashMap<>();
    private static Supplier<Set<UUID>> retainedIds = Set::of;

    public static void installRetainedSelectionIds(Supplier<Set<UUID>> supplier) {
        retainedIds = supplier;
    }

    private static final class Selection {
        int nextPage;
        final List<LogisticsNode> nodes = new ArrayList<>();
    }

    public static void acceptSelectionPage(S2CStoredSelectionPage page) {
        if (page.page() == 0) {
            var retained = retainedIds.get();
            selections.keySet().removeIf(id -> !retained.contains(id));
            pendingSelections.keySet().removeIf(id -> !retained.contains(id));
            selections.remove(page.id());
            pendingSelections.put(page.id(), new Selection());
        }
        Selection selection = pendingSelections.get(page.id());
        if (selection == null || selection.nextPage != page.page()) return;
        if (selection.nodes.size() + page.nodes().size() > NodeSelectionPolicy.MAX_NODES) {
            selections.remove(page.id());
            pendingSelections.remove(page.id());
            return;
        }
        selection.nodes.addAll(page.nodes());
        selection.nextPage++;
        if (page.last()) {
            selections.put(page.id(), List.copyOf(selection.nodes));
            pendingSelections.remove(page.id());
        }
    }

    public static List<LogisticsNode> getStoredNodes(ItemStack stack) {
        return selections.getOrDefault(stack.get(SLDataComponents.STORED_SELECTION_ID.get()), List.of());
    }

    public static void clearSelections() {
        selections.clear();
        pendingSelections.clear();
    }

    private static BooleanSupplier bulkSelectionActive = () -> false;

    private LinkConfiguratorClientHooks() {
    }

    public static void installBulkSelectionKey(BooleanSupplier supplier) {
        bulkSelectionActive = supplier == null ? () -> false : supplier;
    }

    public static boolean isBulkSelectionActive() {
        return bulkSelectionActive.getAsBoolean();
    }
}
