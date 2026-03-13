package com.coobird.staticlogistics.common.item;

import com.coobird.staticlogistics.client.gui.LinkConfiguratorScreen;
import com.coobird.staticlogistics.common.init.SLDataComponents;
import com.coobird.staticlogistics.core.StaticLink;
import com.coobird.staticlogistics.core.TransferType;
import com.coobird.staticlogistics.storage.LinkManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
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

        if (player.isSecondaryUseActive()) {
            if (stack.has(SLDataComponents.FIRST_POS.get())) {
                if (!level.isClientSide) {
                    resetSource(stack, player, level, player.blockPosition());
                }
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }
        } else if (level.isClientSide) {
            Minecraft.getInstance().setScreen(new LinkConfiguratorScreen(stack));
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        return InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        ItemStack stack = context.getItemInHand();
        ToolMode mode = ToolMode.values()[stack.getOrDefault(SLDataComponents.TOOL_MODE.get(), 0)];
        BlockPos clickedPos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();

        if (player.isSecondaryUseActive()) {
            if (mode == ToolMode.CONNECT) {
                BlockPos sourcePos = stack.get(SLDataComponents.FIRST_POS.get());
                Direction sourceFace = stack.get(SLDataComponents.FIRST_FACE.get());
                if (clickedPos.equals(sourcePos) && clickedFace == sourceFace) {
                    resetSource(stack, player, level, clickedPos);
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
            }

            if (mode == ToolMode.REMOVE) {
                if (!level.isClientSide) {
                    LinkManager manager = LinkManager.get(level);
                    if (manager.smartRemoveLinks(clickedPos, clickedFace)) {
                        manager.syncToAll((ServerLevel) level);
                        player.displayClientMessage(Component.translatable("msg.staticlogistics.links_cleared", clickedPos.toShortString()), true);
                    }
                }
                level.playSound(player, clickedPos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.5f, 1.2f);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }

            if (!isLinkable(level, clickedPos, clickedFace)) {
                if (mode == ToolMode.CONNECT && stack.has(SLDataComponents.FIRST_POS.get())) {
                    resetSource(stack, player, level, clickedPos);
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
                return InteractionResult.PASS;
            }

            if (mode == ToolMode.CONFIGURE) {
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.translatable("msg.staticlogistics.todo.face_gui", clickedFace.getName()).withStyle(ChatFormatting.GOLD), true);
                }
                level.playSound(player, clickedPos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.8f, 1.2f);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }

            if (mode == ToolMode.CONNECT) {
                BlockPos sourcePos = stack.get(SLDataComponents.FIRST_POS.get());
                Direction sourceFace = stack.get(SLDataComponents.FIRST_FACE.get());

                if (sourcePos == null || sourceFace == null) {
                    setSource(stack, clickedPos, clickedFace, player, level);
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }

                if (!level.isClientSide) {
                    LinkManager manager = LinkManager.get(level);
                    TransferType type = stack.getOrDefault(SLDataComponents.SELECTED_TYPE.get(), TransferType.ITEM);
                    int priority = stack.getOrDefault(SLDataComponents.PRIORITY.get(), 0);
                    String groupId = stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "default");

                    manager.addLink(new StaticLink(sourcePos, sourceFace, clickedPos, clickedFace, level.dimension(), (1 << type.ordinal()), priority, groupId, 64, false));
                    manager.syncToAll((ServerLevel) level);
                    player.displayClientMessage(Component.translatable("msg.staticlogistics.link_created_multiple", clickedPos.toShortString()).withStyle(ChatFormatting.AQUA), true);
                }

                level.playSound(player, clickedPos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.4f, 1.8f);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return InteractionResult.PASS;
    }

    private void setSource(ItemStack stack, BlockPos pos, Direction face, Player player, Level level) {
        stack.set(SLDataComponents.FIRST_POS.get(), pos);
        stack.set(SLDataComponents.FIRST_FACE.get(), face);
        player.displayClientMessage(Component.translatable("msg.staticlogistics.source_set", pos.toShortString(), face.getName()).withStyle(ChatFormatting.GREEN), true);
        level.playSound(player, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.8f, 1.2f);
    }

    private void resetSource(ItemStack stack, Player player, Level level, BlockPos pos) {
        stack.remove(SLDataComponents.FIRST_POS.get());
        stack.remove(SLDataComponents.FIRST_FACE.get());
        player.displayClientMessage(Component.translatable("msg.staticlogistics.source_reset").withStyle(ChatFormatting.YELLOW), true);
        level.playSound(player, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.5f, 0.5f);
    }

    private boolean isLinkable(Level level, BlockPos pos, Direction face) {
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, face) != null ||
            level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face) != null ||
            level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, face) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ToolMode mode = ToolMode.values()[stack.getOrDefault(SLDataComponents.TOOL_MODE.get(), 0)];
        tooltip.add(Component.translatable("tooltip.staticlogistics.linker.mode", mode.getDisplayName()).withStyle(ChatFormatting.GRAY));

        BlockPos pos = stack.get(SLDataComponents.FIRST_POS.get());
        Direction face = stack.get(SLDataComponents.FIRST_FACE.get());
        if (pos != null && face != null) {
            tooltip.add(Component.translatable("tooltip.staticlogistics.linker.source", pos.toShortString(), face.getName()).withStyle(ChatFormatting.GREEN));
        }

        TransferType type = stack.getOrDefault(SLDataComponents.SELECTED_TYPE.get(), TransferType.ITEM);
        tooltip.add(Component.translatable("tooltip.staticlogistics.linker.type", Component.translatable("type.staticlogistics." + type.getSerializedName())).withStyle(ChatFormatting.AQUA));
    }

    public enum ToolMode {
        CONNECT("connect", ChatFormatting.AQUA),
        REMOVE("remove", ChatFormatting.RED),
        CONFIGURE("configure", ChatFormatting.GOLD);

        private final String name;
        private final ChatFormatting color;

        ToolMode(String name, ChatFormatting color) {
            this.name = name;
            this.color = color;
        }

        public Component getDisplayName() {
            return Component.translatable("mode.staticlogistics." + name).withStyle(color);
        }
    }
}