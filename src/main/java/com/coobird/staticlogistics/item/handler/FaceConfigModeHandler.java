package com.coobird.staticlogistics.item.handler;

import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.gui.menu.FaceConfiguratorMenu;
import com.coobird.staticlogistics.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import net.minecraft.ChatFormatting;
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

public class FaceConfigModeHandler implements ModeHandler {
    @Override
    public InteractionResult handle(LinkConfiguratorItem item, UseOnContext context, ItemStack stack, LinkConfiguratorItem.ToolSettings settings) {
        var level = context.getLevel();
        var player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            BlockPos pos = context.getClickedPos();
            Direction face = context.getClickedFace();
            LinkManager mgr = LinkManager.get(serverLevel);
            FaceConfigComposite config = mgr.getFaceConfig(LinkManager.posToKey(pos, face));
            if (config != null) {
                if (config.canPlayerAccess(player)) {
                    var firstType = settings.getSelectedTypes().isEmpty() ? TransferRegistries.ITEM : settings.getSelectedTypes().getFirst();
                    BlockState state = level.getBlockState(pos);
                    var title = state.getBlock().getName().copy()
                        .append(Component.literal(String.format(" [%d, %d, %d]", pos.getX(), pos.getY(), pos.getZ())).withStyle(ChatFormatting.GRAY));
                    serverPlayer.openMenu(new SimpleMenuProvider((id, inv, p) -> new FaceConfiguratorMenu(id, inv, pos, face), title),
                        buf -> {
                            buf.writeBlockPos(pos);
                            buf.writeEnum(face);
                            buf.writeResourceLocation(firstType.id());
                        });
                } else {
                    player.displayClientMessage(Component.translatable("msg.staticlogistics.no_permission"), true);
                }
            } else {
                player.displayClientMessage(Component.translatable("msg.staticlogistics.no_face_config"), true);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
