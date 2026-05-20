package com.coobird.staticlogistics.item.handler;

import com.coobird.staticlogistics.gui.menu.ContainerConfiguratorMenu;
import com.coobird.staticlogistics.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.transfer.handler.TransferUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

public class ContainerConfigModeHandler implements ModeHandler {

    private static boolean hasAnyFaceConfig(ServerLevel level, BlockPos pos) {
        LinkManager mgr = LinkManager.get(level);
        for (Direction face : Direction.values()) {
            if (mgr.getFaceConfig(LinkManager.posToKey(pos, face)) != null) return true;
        }
        return false;
    }

    @Override
    public InteractionResult handle(LinkConfiguratorItem item, UseOnContext context, ItemStack stack, LinkConfiguratorItem.ToolSettings settings) {
        var level = context.getLevel();
        var player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            BlockPos pos = context.getClickedPos();
            // 允许容器配置的条件：已有面配置 / 有 BlockEntity / 有 logistics capability
            if (!hasAnyFaceConfig(serverLevel, pos)
                && level.getBlockEntity(pos) == null
                && !TransferUtils.hasLogisticsCapability(level, pos, null)) {
                player.displayClientMessage(Component.translatable("msg.staticlogistics.no_capability"), true);
                return InteractionResult.SUCCESS;
            }
            BlockState state = level.getBlockState(pos);
            var title = state.getBlock().getName().copy();
            serverPlayer.openMenu(new SimpleMenuProvider((id, inv, p) -> new ContainerConfiguratorMenu(id, inv, pos), title),
                buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }
}
