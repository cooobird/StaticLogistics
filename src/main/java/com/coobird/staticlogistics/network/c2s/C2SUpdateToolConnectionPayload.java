package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.group.ConnectionCommandService;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * 仅更新工具当前选中的单条连接。
 */
public record C2SUpdateToolConnectionPayload(
    @Nullable ConnectionKey connectionKey
) implements CustomPacketPayload {
    public static final Type<C2SUpdateToolConnectionPayload> TYPE =
        new Type<>(StaticLogistics.asResource("update_tool_connection"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SUpdateToolConnectionPayload>
        STREAM_CODEC = new StreamCodec<>() {
            @Override
            public C2SUpdateToolConnectionPayload decode(RegistryFriendlyByteBuf buffer) {
                ConnectionKey connectionKey =
                    buffer.readBoolean() ? ConnectionKey.STREAM_CODEC.decode(buffer) : null;
                return new C2SUpdateToolConnectionPayload(connectionKey);
            }

            @Override
            public void encode(
                RegistryFriendlyByteBuf buffer,
                C2SUpdateToolConnectionPayload payload
            ) {
                buffer.writeBoolean(payload.connectionKey() != null);
                if (payload.connectionKey() != null) {
                    ConnectionKey.STREAM_CODEC.encode(buffer, payload.connectionKey());
                }
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
        C2SUpdateToolConnectionPayload payload,
        IPayloadContext context
    ) {
        context.enqueueWork(() -> updateConnection(payload, context));
    }

    private static void updateConnection(
        C2SUpdateToolConnectionPayload payload,
        IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        ItemStack stack = ToolSettingsTarget.findSelectionTool(player);
        if (stack.isEmpty()) return;

        ConnectionKey connectionKey = payload.connectionKey();
        if (connectionKey == null) {
            stack.remove(SLDataComponents.SELECTED_CONNECTION_KEY.get());
            return;
        }

        GroupKey selectedGroup = stack.get(SLDataComponents.SELECTED_GROUP_KEY.get());
        if (!connectionKey.groupKey().equals(selectedGroup)) return;
        if (!new ConnectionCommandService(player.server)
            .isSelectable(player, connectionKey)) {
            return;
        }
        stack.set(SLDataComponents.SELECTED_CONNECTION_KEY.get(), connectionKey);
    }
}
