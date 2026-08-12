package com.coobird.staticlogistics.network.c2s;

import PortLib.extensions.net.minecraft.world.item.ItemStack.PortItemStackExtension;
import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.group.GroupService;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.network.BoundedNetworkCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/**
 * 仅更新工具当前选中的分组。
 */
public record C2SUpdateToolGroupPayload(
    String groupId,
    @Nullable GroupKey groupKey
) implements IPortPacket.C2S {
    public static final ResourceLocation ID =
        StaticLogistics.asResource("update_tool_group");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf,
        C2SUpdateToolGroupPayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public C2SUpdateToolGroupPayload decode(
            PortRegistryFriendlyByteBuf buffer
        ) {
            String groupId = BoundedNetworkCodecs.GROUP_NAME.decode(buffer);
            GroupKey groupKey =
                buffer.readBoolean() ? GroupKey.STREAM_CODEC.decode(buffer) : null;
            return new C2SUpdateToolGroupPayload(groupId, groupKey);
        }

        @Override
        public void encode(
            PortRegistryFriendlyByteBuf buffer,
            C2SUpdateToolGroupPayload payload
        ) {
            BoundedNetworkCodecs.GROUP_NAME.encode(buffer, payload.groupId());
            buffer.writeBoolean(payload.groupKey() != null);
            if (payload.groupKey() != null) {
                GroupKey.STREAM_CODEC.encode(buffer, payload.groupKey());
            }
        }
    };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        ItemStack stack = ToolSettingsTarget.findSelectionTool(player);
        if (stack.isEmpty()) return;

        GroupRef selectedGroup;
        String finalId;
        if (groupKey != null) {
            selectedGroup = PlayerGroupStore.get(player.server).findGroup(groupKey);
            if (selectedGroup == null
                || !GroupService.canModify(selectedGroup.key().ownerId(), player)) {
                return;
            }
            finalId = selectedGroup.displayName();
        } else if (groupId.trim().isEmpty()) {
            selectedGroup = null;
            finalId = "";
        } else {
            try {
                finalId = GroupConstraints.normalizeName(groupId);
            } catch (IllegalArgumentException exception) {
                return;
            }
            selectedGroup =
                PlayerGroupStore.get(player.server).findGroup(player.getUUID(), finalId);
            if (selectedGroup == null) return;
            finalId = selectedGroup.displayName();
        }

        PortItemStackExtension.setData(
            stack, SLDataComponents.SELECTED_GROUP.get(), finalId);
        if (selectedGroup == null) {
            PortItemStackExtension.removeData(
                stack, SLDataComponents.SELECTED_GROUP_KEY.get());
        } else {
            PortItemStackExtension.setData(
                stack, SLDataComponents.SELECTED_GROUP_KEY.get(), selectedGroup.key());
        }
        var selectedConnection = PortItemStackExtension.getData(
            stack, SLDataComponents.SELECTED_CONNECTION_KEY.get());
        if (selectedConnection != null && (selectedGroup == null
            || !selectedConnection.groupKey().equals(selectedGroup.key()))) {
            PortItemStackExtension.removeData(
                stack, SLDataComponents.SELECTED_CONNECTION_KEY.get());
        }
    }
}
