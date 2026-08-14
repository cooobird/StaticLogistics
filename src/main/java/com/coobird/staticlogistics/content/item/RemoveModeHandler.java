package com.coobird.staticlogistics.content.item;

import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.logistics.group.ConnectionCommandService;
import com.coobird.staticlogistics.logistics.group.GroupService;
import com.coobird.staticlogistics.logistics.node.FaceAddress;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            BlockPos pos = context.getClickedPos();
            Direction face = context.getClickedFace();
            LinkManager mgr = LinkManager.get(serverLevel);
            FaceAddress key = FaceAddress.of(pos, face);
            var config = mgr.getFaceConfig(key);
            if (config != null) {
                if (GroupService.canModify(config.faceConfig.getOwner(), player)) {
                    String selectedGroup = settings.group();

                    if (selectedGroup.isEmpty()) {
                        player.displayClientMessage(Component.translatable("msg.staticlogistics.select_group_to_remove"), true);
                        return InteractionResult.SUCCESS;
                    }

                    boolean containsSelected = settings.groupKey() != null
                        ? config.faceConfig.getGroupKeys().contains(settings.groupKey())
                        : config.faceConfig.getGroupIds().contains(selectedGroup);
                    if (!containsSelected) {
                        player.displayClientMessage(Component.translatable("msg.staticlogistics.group_not_on_face", selectedGroup), true);
                        return InteractionResult.SUCCESS;
                    }

                    // 统一由链接管理器按组删除边，并回收所有无连接端点。
                    GroupRef group = config.faceConfig.getGroups().stream()
                        .filter(ref -> settings.groupKey() != null
                            ? ref.key().equals(settings.groupKey())
                            : ref.displayName().equals(selectedGroup))
                        .findFirst().orElse(null);
                    if (group == null) return InteractionResult.SUCCESS;
                    boolean removed = new ConnectionCommandService(serverLevel.getServer())
                        .deleteNodeFromGroup(serverPlayer,
                            group.key(), mgr.createNodeFromKey(key));
                    if (!removed) return InteractionResult.SUCCESS;

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
