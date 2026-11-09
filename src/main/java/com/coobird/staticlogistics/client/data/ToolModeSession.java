package com.coobird.staticlogistics.client.data;

import com.coobird.staticlogistics.content.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.content.item.ToolMode;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.network.c2s.C2SUpdateToolModePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.mesdag.portlib.network.PortPacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/// 配置器模式交互会话：维护选择意图、服务端回执及确认后继续的操作。
/// 客户端只保存待确认的选择意图，实际物品与完成提示以服务端结果为准。
public final class ToolModeSession {
    private record Pending(long requestId, UUID toolId, ToolMode mode, Runnable continuation) {
    }

    private static final AtomicLong REQUEST_IDS = new AtomicLong();
    private static final Map<Integer, Pending> PENDING = new HashMap<>();

    public static void clear() {
        PENDING.clear();
    }

    /// 页签高亮跟随最后一次选择意图，不把未确认模式写进工具。
    public static ToolMode currentMode(ItemStack stack) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            int slot = stack == player.getMainHandItem() ? player.getInventory().selected
                : stack == player.getOffhandItem() ? 40 : -1;
            Pending pending = pendingFor(stack, slot);
            if (pending != null) return pending.mode();
        }
        return ToolMode.fromId(stack.getOrDefault(SLDataComponents.TOOL_MODE.get(), 0));
    }

    public static int heldSlot(ItemStack stack) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return -1;
        return stack == player.getMainHandItem() ? player.getInventory().selected
            : stack == player.getOffhandItem() ? 40 : -1;
    }

    public static boolean awaiting(ItemStack stack) {
        return pendingFor(stack, heldSlot(stack)) != null;
    }

    private static Pending pendingFor(ItemStack stack, int slot) {
        Pending pending = PENDING.get(slot);
        if (pending != null && !pending.toolId().equals(stack.get(SLDataComponents.TOOL_SESSION.get()))) {
            PENDING.remove(slot);
            return null;
        }
        return pending;
    }

    /// 后续操作绑定当前模式请求；再次切换模式会丢弃旧操作。
    public static void afterConfirmation(ItemStack stack, Runnable action) {
        int slot = heldSlot(stack);
        if (slot < 0) return;
        Pending pending = pendingFor(stack, slot);
        if (pending == null) action.run();
        else PENDING.put(slot, new Pending(pending.requestId(), pending.toolId(), pending.mode(), action));
    }

    private ToolModeSession() {
    }

    /// 页签与滚轮共用模式提交入口，交互音效由调用方播放一次。
    public static void select(ItemStack stack, ToolMode mode) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !(stack.getItem() instanceof LinkConfiguratorItem)) return;
        int slot;
        if (stack == minecraft.player.getMainHandItem()) slot = minecraft.player.getInventory().selected;
        else if (stack == minecraft.player.getOffhandItem()) slot = 40;
        else return;
        UUID toolId = stack.get(SLDataComponents.TOOL_SESSION.get());
        if (toolId == null) return;
        long requestId = REQUEST_IDS.incrementAndGet();
        PENDING.put(slot, new Pending(requestId, toolId, mode, null));
        PortPacketDistributor.sendToServer(new C2SUpdateToolModePayload(slot, mode.getId(), requestId, toolId));
    }

    /// 只接受该槽位最新请求的回执；旧回执不能覆盖后续选择意图。
    public static void acknowledge(int slot, long requestId, int mode, boolean accepted) {
        Pending pending = PENDING.get(slot);
        if (pending == null || pending.requestId() != requestId) return;
        PENDING.remove(slot);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack stack = player.getInventory().getItem(slot);
        if (!pending.toolId().equals(stack.get(SLDataComponents.TOOL_SESSION.get()))) return;
        /// 工具模式只由前置的原版槽位同步更新，回执不再重复写物品。
        if (!accepted) {
            player.displayClientMessage(Component.translatable("gui.staticlogistics.configuration_rejected"), true);
        } else if (stack.getItem() instanceof LinkConfiguratorItem
            && (slot == player.getInventory().selected || slot == 40)) {
            show(player, stack, ToolMode.fromId(mode));
            if (mode == pending.mode().getId() && pending.continuation() != null
                && ToolMode.fromId(stack.getOrDefault(SLDataComponents.TOOL_MODE.get(), 0)) == pending.mode())
                pending.continuation().run();
        }
    }

    public static void show(LocalPlayer player, ItemStack stack, ToolMode mode) {
        int storedNodeCount = stack.getItem() instanceof LinkConfiguratorItem item ? item.getSettings(stack).storedNodes().size() : 0;
        String translationKey = storedNodeCount == 0 ? "msg.staticlogistics.mode_switched" : "msg.staticlogistics.mode_switched_with_nodes";
        Component message = storedNodeCount == 0 ? Component.translatable(translationKey, mode.getDisplayName()) : Component.translatable(translationKey, mode.getDisplayName(), storedNodeCount);
        player.displayClientMessage(message, true);
    }
}
