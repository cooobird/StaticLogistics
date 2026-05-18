package com.coobird.staticlogistics.item.handler;

import com.coobird.staticlogistics.integration.ModCompat;
import com.coobird.staticlogistics.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.storage.LinkManager;
import mekanism.additions.common.AdditionsTags;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 扳手模式：旋转 + 拆卸 Mek 塑料方块。其他模组的拆卸由标签和自身逻辑处理。
 */
public class WrenchModeHandler implements ModeHandler {

    @Override
    public InteractionResult handle(LinkConfiguratorItem item, UseOnContext context, ItemStack stack, LinkConfiguratorItem.ToolSettings settings) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return InteractionResult.SUCCESS;
        if (!level.isClientSide && player.isSecondaryUseActive()) return dismantle(level, player, pos, state);
        if (!level.isClientSide && !player.isSecondaryUseActive()) return rotateBlock(level, pos, state);
        return InteractionResult.SUCCESS;
    }

    private InteractionResult rotateBlock(Level level, BlockPos pos, BlockState state) {
        BlockState rotated = state.rotate(level, pos, Rotation.CLOCKWISE_90);
        if (rotated != state) {
            level.setBlock(pos, rotated, Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.5f, 1.0f);
            return InteractionResult.SUCCESS;
        }
        if (state.hasProperty(BlockStateProperties.FACING)) {
            level.setBlock(pos, state.setValue(BlockStateProperties.FACING, state.getValue(BlockStateProperties.FACING).getClockWise()), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.5f, 1.0f);
            return InteractionResult.SUCCESS;
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            level.setBlock(pos, state.setValue(BlockStateProperties.HORIZONTAL_FACING, state.getValue(BlockStateProperties.HORIZONTAL_FACING).getClockWise()), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.5f, 1.0f);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private InteractionResult dismantle(Level level, Player player, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;
        if (!player.mayBuild()) {
            player.displayClientMessage(Component.translatable("msg.staticlogistics.wrench.no_permission"), true);
            return InteractionResult.FAIL;
        }
        if (isMekanismPlastic(state)) {
            LinkManager mgr = LinkManager.get(serverLevel);
            mgr.onBlockRemoved(pos);
            ItemStack dropStack = new ItemStack(state.getBlock().asItem());
            level.destroyBlock(pos, false);
            if (!player.addItem(dropStack)) player.drop(dropStack, false);
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5f, 1.5f);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private static boolean isMekanismPlastic(BlockState state) {
        if (!ModCompat.isMekanismAdditionsLoaded()) return false;
        return state.is(AdditionsTags.Blocks.PLASTIC_BLOCKS)
            || state.is(AdditionsTags.Blocks.PLASTIC_BLOCKS_GLOW)
            || state.is(AdditionsTags.Blocks.PLASTIC_BLOCKS_PLASTIC)
            || state.is(AdditionsTags.Blocks.PLASTIC_BLOCKS_REINFORCED)
            || state.is(AdditionsTags.Blocks.PLASTIC_BLOCKS_ROAD)
            || state.is(AdditionsTags.Blocks.PLASTIC_BLOCKS_SLICK)
            || state.is(AdditionsTags.Blocks.PLASTIC_BLOCKS_TRANSPARENT)
            || state.is(AdditionsTags.Blocks.FENCES_PLASTIC)
            || state.is(AdditionsTags.Blocks.FENCE_GATES_PLASTIC)
            || state.is(AdditionsTags.Blocks.STAIRS_PLASTIC)
            || state.is(AdditionsTags.Blocks.SLABS_PLASTIC)
            || state.is(AdditionsTags.Blocks.STAIRS_PLASTIC_GLOW)
            || state.is(AdditionsTags.Blocks.SLABS_PLASTIC_GLOW)
            || state.is(AdditionsTags.Blocks.STAIRS_PLASTIC_TRANSPARENT)
            || state.is(AdditionsTags.Blocks.SLABS_PLASTIC_TRANSPARENT);
    }
}
