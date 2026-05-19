package com.coobird.staticlogistics.item.handler;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.core.service.GroupService;
import com.coobird.staticlogistics.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.network.s2c.S2CSyncFaceConfigPacket;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

public class RemoveModeHandler implements ModeHandler {
    @Override
    public InteractionResult handle(LinkConfiguratorItem item, UseOnContext context, ItemStack stack, LinkConfiguratorItem.ToolSettings settings) {
        var level = context.getLevel();
        var player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            BlockPos pos = context.getClickedPos();
            Direction face = context.getClickedFace();
            LinkManager mgr = LinkManager.get(serverLevel);
            long key = LinkManager.posToKey(pos, face);
            var config = mgr.getFaceConfig(key);
            if (config != null) {
                if (GroupService.canModify(config.faceConfig.getOwner(), player)) {
                    List<LogisticsNode> affectedNodes = List.copyOf(config.getLinkedNodes());
                    mgr.removeFaceConfig(key);
                    level.playSound(null, pos, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 0.5f, 0.8f);
                    player.displayClientMessage(Component.translatable("msg.staticlogistics.links_removed_smart"), true);
                    S2CSyncFaceConfigPacket syncPacket = new S2CSyncFaceConfigPacket(GlobalPos.of(level.dimension(), pos), face, new FaceConfigComposite());
                    GroupService.syncToTeamMembers((ServerPlayer) player, syncPacket);
                    for (LogisticsNode node : affectedNodes) {
                        ServerLevel nodeLevel = serverLevel.getServer().getLevel(node.gPos().dimension());
                        if (nodeLevel != null) {
                            LinkManager nodeMgr = LinkManager.get(nodeLevel);
                            FaceConfigComposite nodeConfig = nodeMgr.getFaceConfig(node.toKey());
                            if (nodeConfig != null) {
                                S2CSyncFaceConfigPacket nodePacket = new S2CSyncFaceConfigPacket(node.gPos(), node.face(), nodeConfig);
                                GroupService.syncToTeamMembers((ServerPlayer) player, nodePacket);
                            }
                        }
                    }
                } else {
                    player.displayClientMessage(Component.translatable("msg.staticlogistics.no_permission_to_remove"), true);
                }
            } else {
                player.displayClientMessage(Component.translatable("msg.staticlogistics.no_links_on_face", face.getName()), true);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
