package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.group.GroupService;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.network.BoundedNetworkCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * 仅更新工具当前选中的分组。
 */
public record C2SUpdateToolGroupPayload(String groupId, @Nullable GroupKey groupKey) implements CustomPacketPayload {
    public static final Type<C2SUpdateToolGroupPayload> TYPE = new Type<>(StaticLogistics.asResource("update_tool_group"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SUpdateToolGroupPayload>
        STREAM_CODEC = new StreamCodec<>() {
            @Override
            public C2SUpdateToolGroupPayload decode(RegistryFriendlyByteBuf buffer) {
                String groupId = BoundedNetworkCodecs.GROUP_NAME.decode(buffer);
                GroupKey groupKey =
                    buffer.readBoolean() ? GroupKey.STREAM_CODEC.decode(buffer) : null;
                return new C2SUpdateToolGroupPayload(groupId, groupKey);
            }

            @Override
            public void encode(
                RegistryFriendlyByteBuf buffer,
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
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SUpdateToolGroupPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> updateGroup(payload, context));
    }

    private static void updateGroup(
        C2SUpdateToolGroupPayload payload,
        IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        ItemStack stack = ToolSettingsTarget.findSelectionTool(player);
        if (stack.isEmpty()) return;

        GroupRef selectedGroup;
        String finalId;
        if (payload.groupKey() != null) {
            selectedGroup = PlayerGroupStore.get(player.server).findGroup(payload.groupKey());
            if (selectedGroup == null
                || !GroupService.canModify(selectedGroup.key().ownerId(), player)) {
                return;
            }
            finalId = selectedGroup.displayName();
        } else if (payload.groupId().trim().isEmpty()) {
            selectedGroup = null;
            finalId = "";
        } else {
            try {
                finalId = GroupConstraints.normalizeName(payload.groupId());
            } catch (IllegalArgumentException exception) {
                return;
            }
            selectedGroup =
                PlayerGroupStore.get(player.server).findGroup(player.getUUID(), finalId);
            if (selectedGroup == null) return;
            finalId = selectedGroup.displayName();
        }

        stack.set(SLDataComponents.SELECTED_GROUP.get(), finalId);
        if (selectedGroup == null) {
            stack.remove(SLDataComponents.SELECTED_GROUP_KEY.get());
        } else {
            stack.set(SLDataComponents.SELECTED_GROUP_KEY.get(), selectedGroup.key());
        }
        var selectedConnection = stack.get(SLDataComponents.SELECTED_CONNECTION_KEY.get());
        if (selectedConnection != null && (selectedGroup == null || !selectedConnection.groupKey().equals(selectedGroup.key()))) {
            stack.remove(SLDataComponents.SELECTED_CONNECTION_KEY.get());
        }
    }
}
