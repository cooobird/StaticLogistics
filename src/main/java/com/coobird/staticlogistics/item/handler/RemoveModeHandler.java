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
                    String selectedGroup = settings.group();

                    // 必须选取组才能移除
                    if (selectedGroup.isEmpty()) {
                        player.displayClientMessage(Component.translatable("msg.staticlogistics.select_group_to_remove"), true);
                        return InteractionResult.SUCCESS;
                    }

                    // 移除选中的组
                    config.faceConfig.removeGroupId(selectedGroup);
                    config.markDirty();
                    mgr.markFaceDirty(key);

                    // 级联清理：移除该链接面所属组中已无其他节点的链接面配置
                    for (LogisticsNode linked : config.getLinkedNodes()) {
                        ServerLevel linkedLevel = serverLevel.getServer().getLevel(linked.gPos().dimension());
                        if (linkedLevel == null) continue;
                        LinkManager linkedMgr = LinkManager.get(linkedLevel);
                        FaceConfigComposite linkedCfg = linkedMgr.getFaceConfig(linked.toKey());
                        if (linkedCfg == null || !linkedCfg.faceConfig.getGroupIds().contains(selectedGroup)) continue;
                        // 全局检查：该组还有没有其他活跃节点
                        boolean hasOtherInGroup = com.coobird.staticlogistics.core.manager.GlobalLogisticsManager
                            .get(serverLevel.getServer()).getNodeGroupService()
                            .getNodesInGroup(selectedGroup).size() > 1;  // >1 因为当前面自己占1个
                        if (!hasOtherInGroup) {
                            linkedCfg.faceConfig.removeGroupId(selectedGroup);
                            linkedCfg.markDirty();
                            linkedMgr.markFaceDirty(linked.toKey());
                            linkedMgr.syncNodeToDimension(linked);
                            S2CSyncFaceConfigPacket linkedPacket = new S2CSyncFaceConfigPacket(linked.gPos(), linked.face(), linkedCfg);
                            GroupService.syncToTeamMembers((ServerPlayer) player, linkedPacket);
                        }
                    }

                    // 面没有组了 → 完整移除面配置
                    if (!config.faceConfig.hasGroup()) {
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
                        mgr.refreshLocalCache(key, pos, face, config);
                        mgr.syncNodeToDimension(mgr.createNodeFromKey(key));
                        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.5f, 0.8f);
                        player.displayClientMessage(Component.translatable("msg.staticlogistics.group_removed_from_face", selectedGroup), true);
                        S2CSyncFaceConfigPacket syncPacket = new S2CSyncFaceConfigPacket(GlobalPos.of(level.dimension(), pos), face, config);
                        GroupService.syncToTeamMembers((ServerPlayer) player, syncPacket);
                    }
                } else {
                    player.displayClientMessage(Component.translatable("msg.staticlogistics.no_permission_to_remove"), true);
                }
            } else {
                player.displayClientMessage(Component.translatable("msg.staticlogistics.no_links_on_face", face.getName()), true);
            }
            // 根源清理：移除所有面配置中已失活的组ID
            com.coobird.staticlogistics.core.manager.GlobalLogisticsManager.get(serverLevel.getServer())
                .cleanupOrphanedGroupIds(player.getUUID());
        }
        return InteractionResult.SUCCESS;
    }
}
