package com.coobird.staticlogistics.common.item;

import com.coobird.staticlogistics.common.init.ModDataComponents;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.core.StaticLink;
import com.coobird.staticlogistics.core.TransferType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.List;

public class LinkConfiguratorItem extends Item {
    public LinkConfiguratorItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 潜行右键空气：如果有坐标记录则清除，否则可以考虑切换模式
        if (player.isShiftKeyDown()) {
            if (stack.has(ModDataComponents.FIRST_POS.get())) {
                stack.remove(ModDataComponents.FIRST_POS.get());
                stack.remove(ModDataComponents.FIRST_FACE.get());
                if (level.isClientSide) {
                    player.displayClientMessage(Component.translatable("msg.staticlogistics.record_cleared"), true);
                }
                level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5f, 0.5f);
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }
        } else {
            // 普通右键空气：循环切换传输类型 (ITEM -> FLUID -> ENERGY)
            if (!level.isClientSide) {
                TransferType current = stack.getOrDefault(ModDataComponents.SELECTED_TYPE.get(), TransferType.ITEM);
                TransferType next = TransferType.values()[(current.ordinal() + 1) % TransferType.values().length];
                stack.set(ModDataComponents.SELECTED_TYPE.get(), next);
                player.displayClientMessage(Component.translatable("tooltip.staticlogistics.linker.type",
                    Component.translatable("type.staticlogistics." + next.getSerializedName())).withStyle(ChatFormatting.AQUA), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        return InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;

        BlockPos clickedPos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();
        
        if (!isLinkable(level, clickedPos, clickedFace)) {
            if (level.isClientSide) {
                player.displayClientMessage(Component.translatable("msg.staticlogistics.invalid_target").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }

        ItemStack stack = context.getItemInHand();
        BlockPos sourcePos = stack.get(ModDataComponents.FIRST_POS.get());
        Direction sourceFace = stack.get(ModDataComponents.FIRST_FACE.get());

        if (sourcePos == null || sourceFace == null) {
            return setSource(level, player, stack, clickedPos, clickedFace);
        }

        if (clickedPos.equals(sourcePos) && clickedFace == sourceFace) {
            stack.remove(ModDataComponents.FIRST_POS.get());
            stack.remove(ModDataComponents.FIRST_FACE.get());
            level.playSound(player, clickedPos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.5f, 0.5f);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        TransferType type = stack.getOrDefault(ModDataComponents.SELECTED_TYPE.get(), TransferType.ITEM);
        int priority = stack.getOrDefault(ModDataComponents.PRIORITY.get(), 0);
        int groupId = stack.getOrDefault(ModDataComponents.SELECTED_GROUP.get(), 0);

        return establishOrUpdateLink(level, player, stack, sourcePos, sourceFace, clickedPos, clickedFace, type, priority, groupId);
    }

    private boolean isLinkable(Level level, BlockPos pos, Direction face) {
        if (level.getCapability(Capabilities.ItemHandler.BLOCK, pos, face) != null) return true;
        if (level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face) != null) return true;
        if (level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, face) != null) return true;
        return false;
    }

    private InteractionResult setSource(Level level, Player player, ItemStack stack, BlockPos pos, Direction face) {
        stack.set(ModDataComponents.FIRST_POS.get(), pos);
        stack.set(ModDataComponents.FIRST_FACE.get(), face);

        if (level.isClientSide) {
            player.displayClientMessage(Component.translatable("msg.staticlogistics.source_set", pos.toShortString(), face.getName()).withStyle(ChatFormatting.GREEN), true);
        }

        level.playSound(player, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.8f, 1.2f);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private InteractionResult establishOrUpdateLink(Level level, Player player, ItemStack stack, BlockPos sPos, Direction sFace, BlockPos dPos, Direction dFace, TransferType type, int priority, int groupId) {
        if (!level.isClientSide) {
            LinkManager manager = LinkManager.get(level);
            int typeBit = (1 << type.ordinal());
            StaticLink existing = manager.getLinkBetween(sPos, sFace, dPos, dFace, groupId);

            if (existing != null) {
                if ((existing.transferFlags() & typeBit) != 0) {
                    int newFlags = existing.transferFlags() & ~typeBit;
                    if (newFlags == 0) {
                        manager.removeLink(existing);
                        player.displayClientMessage(Component.translatable("msg.staticlogistics.link_removed").withStyle(ChatFormatting.RED), true);
                    } else {
                        StaticLink updated = new StaticLink(sPos, sFace, dPos, dFace, newFlags, existing.priority(), groupId);
                        manager.addLink(updated);
                        player.displayClientMessage(Component.translatable("msg.staticlogistics.type_removed").withStyle(ChatFormatting.YELLOW), true);
                    }
                } else {
                    // 添加新类型到现有链路
                    StaticLink updated = new StaticLink(sPos, sFace, dPos, dFace, existing.transferFlags() | typeBit, priority, groupId);
                    manager.addLink(updated);
                    player.displayClientMessage(Component.translatable("msg.staticlogistics.link_updated").withStyle(ChatFormatting.AQUA), true);
                }
            } else {
                // 创建全新链路
                StaticLink link = new StaticLink(sPos, sFace, dPos, dFace, typeBit, priority, groupId);
                manager.addLink(link);
                player.displayClientMessage(Component.translatable("msg.staticlogistics.link_created", dPos.toShortString()).withStyle(ChatFormatting.AQUA), true);
            }

            manager.syncToAll((ServerLevel) level);
        }
        level.playSound(player, dPos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.4f, 1.8f);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        BlockPos pos = stack.get(ModDataComponents.FIRST_POS.get());
        Direction face = stack.get(ModDataComponents.FIRST_FACE.get());

        if (pos != null && face != null) {
            tooltip.add(Component.translatable("tooltip.staticlogistics.linker.source", pos.toShortString(), face.getName()).withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.translatable("tooltip.staticlogistics.linker.no_source").withStyle(ChatFormatting.GRAY));
        }

        int groupId = stack.getOrDefault(ModDataComponents.SELECTED_GROUP.get(), 0);
        tooltip.add(Component.translatable("tooltip.staticlogistics.linker.group", groupId).withStyle(ChatFormatting.WHITE));

        TransferType type = stack.getOrDefault(ModDataComponents.SELECTED_TYPE.get(), TransferType.ITEM);
        Component typeName = Component.translatable("type.staticlogistics." + type.getSerializedName());
        tooltip.add(Component.translatable("tooltip.staticlogistics.linker.type", typeName).withStyle(ChatFormatting.AQUA));

        int priority = stack.getOrDefault(ModDataComponents.PRIORITY.get(), 0);
        tooltip.add(Component.translatable("tooltip.staticlogistics.linker.priority", priority).withStyle(ChatFormatting.GOLD));

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.staticlogistics.linker.desc_link").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.staticlogistics.linker.desc_gui").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.staticlogistics.linker.desc_clear").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.staticlogistics.linker.desc_remove").withStyle(ChatFormatting.DARK_RED));
    }
}