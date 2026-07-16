package com.coobird.staticlogistics.content.item;

import PortLib.extensions.net.minecraft.world.item.ItemStack.PortItemStackExtension;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.blueprint.BlueprintData;
import com.coobird.staticlogistics.logistics.blueprint.BlueprintUpgradeInventory;
import com.coobird.staticlogistics.logistics.group.GroupService;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

public class BlueprintItem extends Item {
    public BlueprintItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            if (!level.isClientSide) {
                boolean had = !PortItemStackExtension.getDataOrDefault(stack, SLDataComponents.BLUEPRINT_DATA.get(), BlueprintData.EMPTY).isEmpty()
                    || !PortItemStackExtension.getDataOrDefault(stack, SLDataComponents.BLUEPRINT_ANCHOR.get(), "").isEmpty()
                    || !PortItemStackExtension.getDataOrDefault(stack, SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get(), "").isEmpty();
                if (had) {
                    PortItemStackExtension.removeData(stack, SLDataComponents.BLUEPRINT_DATA.get());
                    PortItemStackExtension.removeData(stack, SLDataComponents.BLUEPRINT_ANCHOR.get());
                    PortItemStackExtension.removeData(stack, SLDataComponents.SELECTED_GROUP.get());
                    PortItemStackExtension.removeData(stack, SLDataComponents.SELECTED_GROUP_KEY.get());
                    PortItemStackExtension.removeData(stack, SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get());
                    PortItemStackExtension.removeData(stack, SLDataComponents.BLUEPRINT_PREVIEW_ROTATION.get());
                    player.displayClientMessage(Component.translatable("msg.staticlogistics.blueprint.cleared")
                        .withStyle(ChatFormatting.YELLOW), true);
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        if (level.isClientSide) {
            BlueprintClientHooks.openScreen(stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }


    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!player.isSecondaryUseActive()) return InteractionResult.PASS;

        ItemStack stack = context.getItemInHand();
        BlockPos clickedPos = context.getClickedPos();

        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;

        BlueprintData data = PortItemStackExtension.getDataOrDefault(stack, SLDataComponents.BLUEPRINT_DATA.get(), BlueprintData.EMPTY);
        String previewStr = PortItemStackExtension.getDataOrDefault(stack, SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get(), "");

        if (!data.isEmpty()) {
            if (!previewStr.isEmpty()) {
                BlockPos previewAnchor = posFromString(previewStr);
                if (previewAnchor != null && previewAnchor.equals(clickedPos)) {
                    int rotation = PortItemStackExtension.getDataOrDefault(stack, SLDataComponents.BLUEPRINT_PREVIEW_ROTATION.get(), 0);
                    doPaste(serverLevel, player, stack, clickedPos, rotation);
                    PortItemStackExtension.removeData(stack, SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get());
                    PortItemStackExtension.removeData(stack, SLDataComponents.BLUEPRINT_PREVIEW_ROTATION.get());
                } else {
                    // 移动预览到新位置
                    PortItemStackExtension.setData(stack, SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get(), clickedPos.toShortString());
                    player.displayClientMessage(Component.translatable(
                            "msg.staticlogistics.blueprint.preview_moved", clickedPos.toShortString())
                        .withStyle(ChatFormatting.AQUA), true);
                }
                return InteractionResult.SUCCESS;
            }

            // 首次进入预览：从右键点击位置开始
            PortItemStackExtension.setData(stack, SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get(), clickedPos.toShortString());
            PortItemStackExtension.setData(stack, SLDataComponents.BLUEPRINT_PREVIEW_ROTATION.get(), 0);
            player.displayClientMessage(Component.translatable(
                    "msg.staticlogistics.blueprint.preview_enter", clickedPos.toShortString())
                .withStyle(ChatFormatting.AQUA), true);
            serverLevel.playSound(null, clickedPos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.8f, 1.5f);
            return InteractionResult.SUCCESS;
        }

        return selectOrCopy(serverLevel, player, stack, clickedPos);
    }

    private InteractionResult selectOrCopy(ServerLevel level, Player player, ItemStack stack, BlockPos clickedPos) {
        GroupRef group = findPlayerGroup(player);
        if (group == null) {
            player.displayClientMessage(
                Component.translatable("msg.staticlogistics.blueprint.select_group").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.SUCCESS;
        }

        String anchorStr = PortItemStackExtension.getDataOrDefault(stack, SLDataComponents.BLUEPRINT_ANCHOR.get(), "");

        if (anchorStr.isEmpty()) {
            PortItemStackExtension.setData(stack, SLDataComponents.BLUEPRINT_ANCHOR.get(), clickedPos.toShortString());
            PortItemStackExtension.setData(stack, SLDataComponents.SELECTED_GROUP.get(), group.displayName());
            PortItemStackExtension.setData(stack, SLDataComponents.SELECTED_GROUP_KEY.get(), group.key());
            player.displayClientMessage(
                Component.translatable("msg.staticlogistics.blueprint.anchor_set", clickedPos.toShortString())
                    .withStyle(ChatFormatting.GREEN), true);
            level.playSound(null, clickedPos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.5f, 1.0f);
        } else {
            BlockPos anchor = posFromString(anchorStr);
            if (anchor == null) {
                PortItemStackExtension.removeData(stack, SLDataComponents.BLUEPRINT_ANCHOR.get());
                return InteractionResult.SUCCESS;
            }
            copyRegion(level, player, stack, anchor, clickedPos, group);
        }
        return InteractionResult.SUCCESS;
    }

    private void copyRegion(ServerLevel level, Player player, ItemStack stack,
                            BlockPos anchor, BlockPos corner, GroupRef selectedGroup) {
        var result = com.coobird.staticlogistics.logistics.blueprint.BlueprintCaptureService.capture(
            level, player, anchor, corner, selectedGroup);
        switch (result.status()) {
            case TOO_LARGE -> player.displayClientMessage(
                Component.translatable("msg.staticlogistics.blueprint.too_large", result.volume())
                    .withStyle(ChatFormatting.RED), true);
            case NO_PERMISSION -> player.displayClientMessage(
                Component.translatable("msg.staticlogistics.no_permission")
                    .withStyle(ChatFormatting.RED), true);
            case EMPTY -> player.displayClientMessage(
                Component.translatable("msg.staticlogistics.blueprint.empty")
                    .withStyle(ChatFormatting.YELLOW), true);
            case SUCCESS -> {
                BlueprintData data = result.data();
                PortItemStackExtension.setData(stack, SLDataComponents.BLUEPRINT_DATA.get(), data);
                PortItemStackExtension.removeData(stack, SLDataComponents.BLUEPRINT_ANCHOR.get());
                player.displayClientMessage(Component.translatable(
                        "msg.staticlogistics.blueprint.copied", data.blocks().size(), anchor.toShortString())
                    .withStyle(ChatFormatting.GREEN), true);
                level.playSound(null, corner, SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.BLOCKS, 1.0f, 1.0f);
            }
        }
    }

    private void doPaste(ServerLevel level, Player player, ItemStack stack,
                         BlockPos newAnchor, int rotation) {
        BlueprintData data = PortItemStackExtension.getDataOrDefault(stack,
            SLDataComponents.BLUEPRINT_DATA.get(), BlueprintData.EMPTY);
        com.coobird.staticlogistics.logistics.blueprint.BlueprintPasteService.paste(
            level, player, data, newAnchor, rotation);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        BlueprintData data = PortItemStackExtension.getDataOrDefault(stack, SLDataComponents.BLUEPRINT_DATA.get(), BlueprintData.EMPTY);
        String anchorStr = PortItemStackExtension.getDataOrDefault(stack, SLDataComponents.BLUEPRINT_ANCHOR.get(), "");
        String previewStr = PortItemStackExtension.getDataOrDefault(stack, SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get(), "");

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

            tooltip.add(Component.translatable("tooltip.staticlogistics.blueprint.region",
                from.toShortString(), to.toShortString()).withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.translatable("tooltip.staticlogistics.blueprint.info").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip.staticlogistics.blueprint.group", data.groupId()).withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.translatable("tooltip.staticlogistics.blueprint.face_count", faceCount).withStyle(ChatFormatting.WHITE));
            tooltip.add(Component.translatable("tooltip.staticlogistics.blueprint.container",
                Component.translatable(containerCount > 0 ? "gui.staticlogistics.true" : "gui.staticlogistics.false")).withStyle(ChatFormatting.WHITE));
            // 消耗预览：显示需要多少 vs 背包中有多少
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
                int rot = PortItemStackExtension.getDataOrDefault(stack, SLDataComponents.BLUEPRINT_PREVIEW_ROTATION.get(), 0);
                tooltip.add(Component.translatable("tooltip.staticlogistics.blueprint.preview", previewStr,
                    rot * 90).withStyle(ChatFormatting.AQUA));
            }
            tooltip.add(Component.empty());
        } else if (!anchorStr.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.staticlogistics.blueprint.anchor", anchorStr).withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.empty());
        }
        String selectedGroup = PortItemStackExtension.getDataOrDefault(stack, SLDataComponents.SELECTED_GROUP.get(), "");
        if (!selectedGroup.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.staticlogistics.group",
                Component.literal(selectedGroup)).withStyle(ChatFormatting.AQUA));
        }
        BlueprintClientHooks.appendKeyTooltips(stack, tooltip);
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Nullable
    private static GroupRef findPlayerGroup(Player player) {
        for (var hand : InteractionHand.values()) {
            GroupRef group = resolveSelectedGroup(player, player.getItemInHand(hand));
            if (group != null) return group;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            GroupRef group = resolveSelectedGroup(player, player.getInventory().getItem(i));
            if (group != null) return group;
        }
        return null;
    }

    @Nullable
    private static GroupRef resolveSelectedGroup(Player player, ItemStack stack) {
        if (player.getServer() == null) return null;
        PlayerGroupStore store = PlayerGroupStore.get(player.getServer());
        GroupKey key = PortItemStackExtension.getData(stack, SLDataComponents.SELECTED_GROUP_KEY.get());
        String displayName = PortItemStackExtension.getDataOrDefault(stack, SLDataComponents.SELECTED_GROUP.get(), "");
        GroupRef group = key == null ? store.findGroup(player.getUUID(), displayName) : store.findGroup(key);
        return group != null && group.displayName().equals(displayName)
            && GroupService.canAccess(group.key().ownerId(), player) ? group : null;
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

