package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.network.BoundedNetworkCodecs;
import com.coobird.staticlogistics.transfer.TransferTypeSelection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.util.List;
import java.util.Objects;

/**
 * 确认服务端验证后的当前节点及可选传输类型。
 */
public record S2CSelectLinkEndpointPayload(
    GroupKey groupKey,
    LogisticsNode node,
    boolean inputSide,
    List<ResourceLocation> selectedTypeIds
) implements IPortPacket.S2C {
    public static final ResourceLocation ID = StaticLogistics.asResource("select_link_endpoint");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, S2CSelectLinkEndpointPayload> STREAM_CODEC =
        new PortStreamCodec<>() {
            @Override
            public S2CSelectLinkEndpointPayload decode(PortRegistryFriendlyByteBuf buffer) {
                return new S2CSelectLinkEndpointPayload(
                    GroupKey.STREAM_CODEC.decode(buffer),
                    LogisticsNode.STREAM_CODEC.decode(buffer),
                    buffer.readBoolean(),
                    BoundedNetworkCodecs.TRANSFER_TYPE_IDS.decode(buffer));
            }

            @Override
            public void encode(PortRegistryFriendlyByteBuf buffer, S2CSelectLinkEndpointPayload value) {
                GroupKey.STREAM_CODEC.encode(buffer, value.groupKey());
                LogisticsNode.STREAM_CODEC.encode(buffer, value.node());
                buffer.writeBoolean(value.inputSide());
                BoundedNetworkCodecs.TRANSFER_TYPE_IDS.encode(buffer, value.selectedTypeIds());
            }
        };

    public S2CSelectLinkEndpointPayload {
        Objects.requireNonNull(groupKey, "Group key must not be null");
        Objects.requireNonNull(node, "Node must not be null");
        selectedTypeIds = TransferTypeSelection.sanitize(selectedTypeIds);
    }

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(Player player) {
        if (player.containerMenu instanceof LinkConfiguratorMenu menu) {
            menu.selectTarget(node(), groupKey(), inputSide(), selectedTypeIds());
        }
    }
}
