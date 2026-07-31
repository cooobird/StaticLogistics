package com.coobird.staticlogistics.network.c2s;

import PortLib.extensions.net.minecraft.world.item.ItemStack.PortItemStackExtension;
import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.group.ConnectionCommandService;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/**
 * 仅更新工具当前选中的单条连接。
 */
public record C2SUpdateToolConnectionPayload(
    @Nullable ConnectionKey connectionKey
) implements IPortPacket.C2S {
    public static final ResourceLocation ID =
        StaticLogistics.asResource("update_tool_connection");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf,
        C2SUpdateToolConnectionPayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public C2SUpdateToolConnectionPayload decode(
            PortRegistryFriendlyByteBuf buffer
        ) {
            ConnectionKey connectionKey =
                buffer.readBoolean() ? ConnectionKey.STREAM_CODEC.decode(buffer) : null;
            return new C2SUpdateToolConnectionPayload(connectionKey);
        }

        @Override
        public void encode(
            PortRegistryFriendlyByteBuf buffer,
            C2SUpdateToolConnectionPayload payload
        ) {
            buffer.writeBoolean(payload.connectionKey() != null);
            if (payload.connectionKey() != null) {
                ConnectionKey.STREAM_CODEC.encode(buffer, payload.connectionKey());
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
        if (connectionKey == null) {
            PortItemStackExtension.removeData(
                stack, SLDataComponents.SELECTED_CONNECTION_KEY.get());
            return;
        }

        GroupKey selectedGroup = PortItemStackExtension.getData(
            stack, SLDataComponents.SELECTED_GROUP_KEY.get());
        if (!connectionKey.groupKey().equals(selectedGroup)) return;
        if (!new ConnectionCommandService(player.server)
            .isSelectable(player, connectionKey)) {
            return;
        }
        PortItemStackExtension.setData(
            stack, SLDataComponents.SELECTED_CONNECTION_KEY.get(), connectionKey);
    }
}
