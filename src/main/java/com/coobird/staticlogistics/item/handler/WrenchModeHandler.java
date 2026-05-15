package com.coobird.staticlogistics.item.handler;

import com.coobird.staticlogistics.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.registry.SLDataComponents;
import com.coobird.staticlogistics.transfer.handler.TransferUtils;
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

            if (!TransferUtils.hasLogisticsCapability(level, pos, null)) {
                player.displayClientMessage(Component.translatable("msg.staticlogistics.wrench.not_container"), true);
                return InteractionResult.SUCCESS;
            }

            BlockEntity be = level.getBlockEntity(pos);
            ItemStack dropStack = new ItemStack(state.getBlock().asItem());

            if (be != null) {
                HolderLookup.Provider registries = level.registryAccess();
                CompoundTag beTag = be.saveWithoutMetadata(registries);

                beTag.remove("x");
                beTag.remove("y");
                beTag.remove("z");
                beTag.remove("id");
                beTag.remove("NeoForgeData");

                if (be instanceof Container container) {
                    container.clearContent();
                }

                if (!beTag.isEmpty()) {
                    dropStack.set(SLDataComponents.STORED_BE_NBT.get(), CustomData.of(beTag));
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
}