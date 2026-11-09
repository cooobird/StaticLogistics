package com.coobird.staticlogistics.content.item;

import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.blueprint.BlueprintData;
import com.coobird.staticlogistics.logistics.blueprint.BlueprintPasteService;
import com.coobird.staticlogistics.logistics.blueprint.BlueprintUpgradeInventory;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/// 蓝图复制由配置器页面发起；世界交互仅处理独立的放置预览。
public final class BlueprintModeHandler {
    private BlueprintModeHandler() {
    }

    public static boolean isActive(ItemStack stack) {
        return stack.getItem() instanceof LinkConfiguratorItem
            && (ToolMode.fromId(stack.getOrDefault(SLDataComponents.TOOL_MODE.get(), 0)) == ToolMode.BLUEPRINT
            || isPlacing(stack));
    }

    public static boolean isPlacing(ItemStack stack) {
        return stack.getOrDefault(SLDataComponents.BLUEPRINT_PLACING.get(), false);
    }

    public static ItemStack held(Player player) {
        if (isActive(player.getMainHandItem())) return player.getMainHandItem();
        return isActive(player.getOffhandItem()) ? player.getOffhandItem() : ItemStack.EMPTY;
    }

    public static ItemStack tool(Player player, int slot) {
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) return ItemStack.EMPTY;
        ItemStack stack = player.getInventory().getItem(slot);
        return stack.getItem() instanceof LinkConfiguratorItem
            && (slot == player.getInventory().selected || slot == 40) ? stack : ItemStack.EMPTY;
    }

    public static void cancel(ItemStack stack) {
        stack.remove(SLDataComponents.BLUEPRINT_PLACING.get());
        stack.remove(SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get());
        stack.remove(SLDataComponents.BLUEPRINT_PREVIEW_ROTATION.get());
        stack.remove(SLDataComponents.BLUEPRINT_PREVIEW_DIMENSION.get());
    }

    public static void start(Player player, ItemStack stack) {
        if (stack.getOrDefault(SLDataComponents.BLUEPRINT_DATA.get(), BlueprintData.EMPTY).isEmpty()) return;
        cancel(stack);
        stack.set(SLDataComponents.TOOL_MODE.get(), ToolMode.BLUEPRINT.getId());
        stack.set(SLDataComponents.BLUEPRINT_PLACING.get(), true);
    }

    private static boolean hasPreview(Level level, ItemStack stack) {
        return !stack.getOrDefault(SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get(), "").isEmpty()
            && level.dimension().location().toString().equals(stack.getOrDefault(SLDataComponents.BLUEPRINT_PREVIEW_DIMENSION.get(), ""));
    }

    public static InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            if (player.isSecondaryUseActive()) cancel(stack);
            else if (hasPreview(level, stack)) confirm(level, player, stack);
            else
                player.displayClientMessage(Component.translatable("msg.staticlogistics.blueprint.choose_target"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!context.getLevel().isClientSide) {
            if (context.getItemInHand().getOrDefault(SLDataComponents.BLUEPRINT_DATA.get(), BlueprintData.EMPTY).isEmpty()) {
                player.displayClientMessage(Component.translatable("msg.staticlogistics.blueprint.copy_first"), true);
                return InteractionResult.SUCCESS;
            }
            if (player.isSecondaryUseActive()) cancel(context.getItemInHand());
            else if (hasPreview(context.getLevel(), context.getItemInHand()))
                confirm(context.getLevel(), player, context.getItemInHand());
            else {
                ItemStack stack = context.getItemInHand();
                if (!isPlacing(stack)) start(player, stack);
                stack.set(SLDataComponents.BLUEPRINT_PREVIEW_DIMENSION.get(), context.getLevel().dimension().location().toString());
                stack.set(SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get(), context.getClickedPos().toShortString());
                stack.set(SLDataComponents.BLUEPRINT_PREVIEW_ROTATION.get(), 0);
                player.displayClientMessage(Component.translatable("msg.staticlogistics.blueprint.preview_enter",
                    context.getClickedPos().toShortString()), true);
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    private static void confirm(Level level, Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!level.dimension().location().toString().equals(stack.getOrDefault(SLDataComponents.BLUEPRINT_PREVIEW_DIMENSION.get(), ""))) {
            cancel(stack);
            return;
        }
        BlockPos anchor = posFromString(stack.getOrDefault(SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get(), ""));
        if (anchor == null) return;
        boolean pasted = BlueprintPasteService.paste(serverLevel, player,
            stack.getOrDefault(SLDataComponents.BLUEPRINT_DATA.get(), BlueprintData.EMPTY), anchor,
            stack.getOrDefault(SLDataComponents.BLUEPRINT_PREVIEW_ROTATION.get(), 0));
        if (pasted) cancel(stack);
    }

    public static void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        BlueprintData data = stack.getOrDefault(SLDataComponents.BLUEPRINT_DATA.get(), BlueprintData.EMPTY);
        String anchorStr = stack.getOrDefault(SLDataComponents.BLUEPRINT_ANCHOR.get(), "");
        String previewStr = stack.getOrDefault(SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get(), "");

        if (!data.isEmpty()) {
            int faceCount = 0, containerCount = 0;
            for (BlueprintData.BlockEntry entry : data.blocks()) {
                faceCount += entry.faces().size();
                if (!entry.containerUpgrades().isEmpty()) {
                    containerCount++;
                }
            }
            int minRelX = 0, minRelY = 0, minRelZ = 0, maxRelX = 0, maxRelY = 0, maxRelZ = 0;
            for (BlueprintData.BlockEntry e : data.blocks()) {
                BlockPos r = e.relativePos();
                if (r.getX() < minRelX) minRelX = r.getX();
                if (r.getY() < minRelY) minRelY = r.getY();
                if (r.getZ() < minRelZ) minRelZ = r.getZ();
                if (r.getX() > maxRelX) maxRelX = r.getX();
                if (r.getY() > maxRelY) maxRelY = r.getY();
                if (r.getZ() > maxRelZ) maxRelZ = r.getZ();
            }
            BlockPos from = data.anchor().offset(minRelX, minRelY, minRelZ);
            BlockPos to = data.anchor().offset(maxRelX, maxRelY, maxRelZ);

            tooltip.add(Component.translatable("tooltip.staticlogistics.blueprint.region", from.toShortString(), to.toShortString()).withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.translatable("tooltip.staticlogistics.blueprint.info").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("gui.staticlogistics.group_label", data.groupId()).withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.translatable("tooltip.staticlogistics.blueprint.face_count", faceCount).withStyle(ChatFormatting.WHITE));
            tooltip.add(Component.translatable("tooltip.staticlogistics.blueprint.container", Component.translatable(containerCount > 0 ? "gui.staticlogistics.true" : "gui.staticlogistics.false")).withStyle(ChatFormatting.WHITE));
            /// 消耗预览：显示需要多少 vs 背包中有多少
            Map<String, Integer> needed = BlueprintUpgradeInventory.tally(data);
            if (!needed.isEmpty()) {
                tooltip.add(Component.translatable("tooltip.staticlogistics.blueprint.upgrades").withStyle(ChatFormatting.WHITE));
                for (var entry : needed.entrySet()) {
                    String id = entry.getKey();
                    int need = entry.getValue();
                    int have = BlueprintClientHooks.countInventoryItem(id);
                    String itemName = Component.translatable("item." + id.replace(':', '.')).getString();
                    ChatFormatting color = have >= need ? ChatFormatting.GREEN : ChatFormatting.RED;
                    tooltip.add(Component.literal("    " + itemName + " × " + need + " (" + have + "/" + need + ")").withStyle(color));
                }
            }

            if (!previewStr.isEmpty()) {
                tooltip.add(Component.empty());
                int rot = stack.getOrDefault(SLDataComponents.BLUEPRINT_PREVIEW_ROTATION.get(), 0);
                tooltip.add(Component.translatable("tooltip.staticlogistics.blueprint.preview", previewStr, rot * 90).withStyle(ChatFormatting.AQUA));
            }
            tooltip.add(Component.empty());
        } else if (!anchorStr.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.staticlogistics.blueprint.anchor", anchorStr).withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.empty());
        }
        String selectedGroup = stack.getOrDefault(SLDataComponents.BLUEPRINT_GROUP.get(), "");
        if (!selectedGroup.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.staticlogistics.group",
                Component.literal(selectedGroup)).withStyle(ChatFormatting.AQUA));
        }
        BlueprintClientHooks.appendKeyTooltips(stack, tooltip);
    }

    @Nullable
    private static BlockPos posFromString(String s) {
        if (s == null || s.isEmpty()) return null;
        String[] parts = s.split(", ");
        if (parts.length != 3) return null;
        try {
            return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
