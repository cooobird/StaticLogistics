package com.coobird.staticlogistics.item.handler;

import com.coobird.staticlogistics.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.registry.SLDataComponents;
import com.coobird.staticlogistics.storage.LinkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 扳手模式：潜行右键搬运方块。
 * 通用对比法——两次 saveWithoutMetadata（中间清空容器）。
 * 不同 = 有内容 → 完整保存（含燃烧进度）。相同 = 默认态 → 不保存（堆叠）。
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

        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            if (!player.isSecondaryUseActive()) {
                player.displayClientMessage(Component.translatable("msg.staticlogistics.wrench.sneak_required"), true);
                return InteractionResult.SUCCESS;
            }
            if (!player.mayBuild()) {
                player.displayClientMessage(Component.translatable("msg.staticlogistics.wrench.no_permission"), true);
                return InteractionResult.SUCCESS;
            }

            if (level.getBlockEntity(pos) == null) {
                player.displayClientMessage(Component.translatable("msg.staticlogistics.wrench.no_block_entity"), true);
                return InteractionResult.SUCCESS;
            }

            LinkManager mgr = LinkManager.get(serverLevel);
            mgr.onBlockRemoved(pos);

            BlockEntity be = level.getBlockEntity(pos);
            ItemStack dropStack = new ItemStack(state.getBlock().asItem());

            if (be != null) {
                HolderLookup.Provider registries = level.registryAccess();

                CompoundTag tag1 = be.saveWithoutMetadata(registries);
                stripPosition(tag1);

                if (be instanceof Container c) {
                    c.clearContent();
                }

                CompoundTag tag2 = be.saveWithoutMetadata(registries);
                stripPosition(tag2);

                if (!tag1.equals(tag2)) {
                    dropStack.set(SLDataComponents.STORED_BE_NBT.get(), CustomData.of(tag1));
                }
            }

            level.removeBlock(pos, false);

            if (!player.addItem(dropStack)) {
                player.drop(dropStack, false);
            }

            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5f, 1.5f);
            player.displayClientMessage(Component.translatable("msg.staticlogistics.wrench.removed", state.getBlock().getName()), true);
        }
        return InteractionResult.SUCCESS;
    }

    private static void stripPosition(CompoundTag tag) {
        tag.remove("x");
        tag.remove("y");
        tag.remove("z");
        tag.remove("id");
        tag.remove("NeoForgeData");
    }
}
