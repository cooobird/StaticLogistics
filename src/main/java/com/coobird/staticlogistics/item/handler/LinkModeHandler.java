package com.coobird.staticlogistics.item.handler;

import com.coobird.staticlogistics.core.service.GroupService;
import com.coobird.staticlogistics.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.item.util.LinkOperationHelper;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

public class LinkModeHandler implements ModeHandler {
    @Override
    public InteractionResult handle(LinkConfiguratorItem item, UseOnContext context, ItemStack stack, LinkConfiguratorItem.ToolSettings settings) {
        var level = context.getLevel();
        var player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            BlockPos pos = context.getClickedPos();
            Direction face = context.getClickedFace();

            if (settings.storedMode() != null && settings.storedMode() != settings.mode()) {
                String newGroupId = GroupService.getNextGroupIdForPlayer(player);
                LinkOperationHelper.executeBatchLink(stack, newGroupId, settings, pos, face, serverLevel, player);
                return InteractionResult.SUCCESS;
            }

            LinkManager mgr = LinkManager.get(serverLevel);
            FaceConfigComposite config = mgr.getFaceConfig(LinkManager.posToKey(pos, face));
            if (config == null || config.canPlayerAccess(player)) {
                LinkOperationHelper.addNode(stack, GlobalPos.of(level.dimension(), pos), face, settings.mode(), player, level);
            } else {
                player.displayClientMessage(Component.translatable("msg.staticlogistics.no_permission"), true);
            }
        }
        return InteractionResult.SUCCESS;
    }
}