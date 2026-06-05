package com.coobird.staticlogistics.item.handler;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.logic.GroupService;
import com.coobird.staticlogistics.network.s2c.S2CSyncFaceConfigPayload;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
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

                    if (selectedGroup.isEmpty()) {
                        player.displayClientMessage(Component.translatable("msg.staticlogistics.select_group_to_remove"), true);
                        return InteractionResult.SUCCESS;
                    }

                    if (!config.faceConfig.getGroupIds().contains(selectedGroup)) {
                        player.displayClientMessage(Component.translatable("msg.staticlogistics.group_not_on_face", selectedGroup), true);
                        return InteractionResult.SUCCESS;
                    }

                    // 移除点击的面：删组 → 无组则删面配置 → cascade 清理关联面
                    // A→B: 移除任一端 → 整条链接移除
                    // A→B,C,D: 移除 A → 全部移除；移除 B → 只移除 A→B
                    config.faceConfig.removeGroupId(selectedGroup);
                    config.markDirty();
                    mgr.markFaceDirty(key);

                    if (!config.faceConfig.hasGroup()) {
                        // 先给玩家发送所有关联面的 removal 包（不依赖区块追踪）
                        for (LogisticsNode linked : config.getLinkedNodes()) {
                            ServerLevel linkedLevel = serverLevel.getServer().getLevel(linked.gPos().dimension());
                            if (linkedLevel != null) {
                                S2CSyncFaceConfigPayload removalPacket = new S2CSyncFaceConfigPayload(
                                    linked.gPos(), linked.face(), new FaceConfigComposite());
                                GroupService.syncToTeamMembers((ServerPlayer) player, removalPacket);
                            }
                        }
                        // 再发自身的 removal 包
                        S2CSyncFaceConfigPayload selfRemoval = new S2CSyncFaceConfigPayload(
                            GlobalPos.of(level.dimension(), pos), face, new FaceConfigComposite());
                        GroupService.syncToTeamMembers((ServerPlayer) player, selfRemoval);

                        // 删除面配置，cascade 清理关联面的服务端数据
                        mgr.removeFaceConfig(key);
                    } else {
                        mgr.refreshLocalCache(key, pos, face, config);
                        mgr.scheduleNetworkSync(mgr.createNodeFromKey(key));
                    }

                    level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.5f, 0.8f);
                    player.displayClientMessage(Component.translatable("msg.staticlogistics.group_removed_from_face", selectedGroup), true);
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
