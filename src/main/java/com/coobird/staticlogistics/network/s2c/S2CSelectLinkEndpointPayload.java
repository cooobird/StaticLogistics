package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.network.BoundedNetworkCodecs;
import com.coobird.staticlogistics.transfer.TransferTypeSelection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Objects;

/**
 * 服务端确认连接配置器当前选中的节点。
 *
 * <p>只有通过远程访问与所有者校验后才发送，防止客户端显示目标与服务端
 * 实际配置目标不一致。</p>
 */
public record S2CSelectLinkEndpointPayload(
    GroupKey groupKey,
    LogisticsNode node,
    boolean inputSide,
    List<ResourceLocation> selectedTypeIds
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
                    GroupKey.STREAM_CODEC.decode(buffer),
                    LogisticsNode.STREAM_CODEC.decode(buffer),
                    buffer.readBoolean(),
                    BoundedNetworkCodecs.TRANSFER_TYPE_IDS.decode(buffer));
            }

            @Override
            public void encode(
                RegistryFriendlyByteBuf buffer,
                S2CSelectLinkEndpointPayload payload
            ) {
                GroupKey.STREAM_CODEC.encode(buffer, payload.groupKey());
                LogisticsNode.STREAM_CODEC.encode(buffer, payload.node());
                buffer.writeBoolean(payload.inputSide());
                BoundedNetworkCodecs.TRANSFER_TYPE_IDS.encode(
                    buffer, payload.selectedTypeIds());
            }
        };

    public S2CSelectLinkEndpointPayload {
        Objects.requireNonNull(groupKey, "Group key must not be null");
        Objects.requireNonNull(node, "Node must not be null");
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
                instanceof LinkConfiguratorMenu menu) {
                menu.selectTarget(
                    payload.node(), payload.groupKey(), payload.inputSide(),
                    payload.selectedTypeIds());
            }
        });
    }
}
