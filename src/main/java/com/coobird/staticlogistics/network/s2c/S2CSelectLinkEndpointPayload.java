package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.client.gui.screen.LinkConfiguratorScreen;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.network.BoundedNetworkCodecs;
import com.coobird.staticlogistics.transfer.TransferTypeSelection;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/// 服务端确认连接配置器当前选中的节点。
/// 只有通过远程访问与所有者校验后才发送，防止客户端显示目标与服务端
/// 实际配置目标不一致。
public record S2CSelectLinkEndpointPayload(
    int menuId,
    GroupKey groupKey,
    LogisticsNode node,
    boolean inputSide,
    List<ResourceLocation> selectedTypeIds,
    long requestId
) implements CustomPacketPayload {
    public static final Type<S2CSelectLinkEndpointPayload> TYPE =
        new Type<>(StaticLogistics.asResource("select_link_endpoint"));

    public static final StreamCodec<RegistryFriendlyByteBuf,
        S2CSelectLinkEndpointPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public S2CSelectLinkEndpointPayload decode(
                RegistryFriendlyByteBuf buffer
            ) {
                return new S2CSelectLinkEndpointPayload(
                    buffer.readVarInt(),
                    GroupKey.STREAM_CODEC.decode(buffer),
                    LogisticsNode.STREAM_CODEC.decode(buffer),
                    buffer.readBoolean(),
                    BoundedNetworkCodecs.TRANSFER_TYPE_IDS.decode(buffer), buffer.readVarLong());
            }

            @Override
            public void encode(
                RegistryFriendlyByteBuf buffer,
                S2CSelectLinkEndpointPayload payload
            ) {
                buffer.writeVarInt(payload.menuId());
                GroupKey.STREAM_CODEC.encode(buffer, payload.groupKey());
                LogisticsNode.STREAM_CODEC.encode(buffer, payload.node());
                buffer.writeBoolean(payload.inputSide());
                BoundedNetworkCodecs.TRANSFER_TYPE_IDS.encode(
                    buffer, payload.selectedTypeIds());
                buffer.writeVarLong(payload.requestId());
            }
        };

    public S2CSelectLinkEndpointPayload {
        selectedTypeIds = TransferTypeSelection.sanitize(selectedTypeIds);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
        S2CSelectLinkEndpointPayload payload,
        IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu
                instanceof LinkConfiguratorMenu menu && menu.containerId == payload.menuId()) {
                if (Minecraft.getInstance().screen instanceof LinkConfiguratorScreen screen
                    && screen.getMenu() == menu)
                    screen.acceptTarget(payload.requestId(), payload.groupKey(), payload.node(), payload.inputSide(), payload.selectedTypeIds());
            }
        });
    }
}
