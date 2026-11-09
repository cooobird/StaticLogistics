package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.network.BoundedNetworkCodecs;
import com.coobird.staticlogistics.transfer.LiveTransferRates;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

public record S2CTransferRatesPayload(int menuId, LogisticsNode node,
                                      Map<ResourceLocation, LiveTransferRates.Rate> rates, String diagnostic,
                                      boolean truncated,
                                      Map<LogisticsNode, Map<ResourceLocation, LiveTransferRates.Rate>> peers,
                                      Set<LogisticsNode> unavailable) implements CustomPacketPayload {
    public S2CTransferRatesPayload {
        rates = Map.copyOf(rates);
        peers = Map.copyOf(peers);
        unavailable = Set.copyOf(unavailable);
    }

    public static final Type<S2CTransferRatesPayload> TYPE = new Type<>(StaticLogistics.asResource("transfer_rates"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CTransferRatesPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public S2CTransferRatesPayload decode(RegistryFriendlyByteBuf buffer) {
            int menuId = buffer.readVarInt();
            LogisticsNode node = LogisticsNode.STREAM_CODEC.decode(buffer);
            List<ResourceLocation> ids = BoundedNetworkCodecs.TRANSFER_TYPE_IDS.decode(buffer);
            Map<ResourceLocation, LiveTransferRates.Rate> rates = new HashMap<>();
            for (ResourceLocation id : ids) {
                double sent = buffer.readDouble(), received = buffer.readDouble();
                if (!Double.isFinite(sent) || !Double.isFinite(received) || sent < 0 || received < 0)
                    throw new DecoderException("Invalid transfer rate");
                rates.put(id, new LiveTransferRates.Rate(sent, received));
            }
            String diagnostic = buffer.readUtf(256);
            boolean truncated = buffer.readBoolean();
            int count = buffer.readVarInt();
            if (count < 0 || count > 256) throw new DecoderException("Invalid rate peer count");
            Map<LogisticsNode, Map<ResourceLocation, LiveTransferRates.Rate>> peers = new HashMap<>();
            Set<LogisticsNode> unavailable = new HashSet<>();
            for (int i = 0; i < count; i++) {
                LogisticsNode peer = LogisticsNode.STREAM_CODEC.decode(buffer);
                if (buffer.readBoolean()) unavailable.add(peer);
                List<ResourceLocation> peerIds = BoundedNetworkCodecs.TRANSFER_TYPE_IDS.decode(buffer);
                Map<ResourceLocation, LiveTransferRates.Rate> peerRates = new HashMap<>();
                for (ResourceLocation id : peerIds) {
                    double sent = buffer.readDouble(), received = buffer.readDouble();
                    if (!Double.isFinite(sent) || !Double.isFinite(received) || sent < 0 || received < 0)
                        throw new DecoderException("Invalid peer transfer rate");
                    peerRates.put(id, new LiveTransferRates.Rate(sent, received));
                }
                peers.put(peer, Map.copyOf(peerRates));
            }
            return new S2CTransferRatesPayload(menuId, node, rates, diagnostic, truncated, peers, unavailable);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, S2CTransferRatesPayload payload) {
            buffer.writeVarInt(payload.menuId());
            LogisticsNode.STREAM_CODEC.encode(buffer, payload.node());
            List<ResourceLocation> ids = List.copyOf(payload.rates().keySet());
            BoundedNetworkCodecs.TRANSFER_TYPE_IDS.encode(buffer, ids);
            for (ResourceLocation id : ids) {
                var rate = payload.rates().get(id);
                buffer.writeDouble(rate.sent());
                buffer.writeDouble(rate.received());
            }
            buffer.writeUtf(payload.diagnostic(), 256);
            buffer.writeBoolean(payload.truncated());
            buffer.writeVarInt(payload.peers().size());
            for (var entry : payload.peers().entrySet()) {
                LogisticsNode.STREAM_CODEC.encode(buffer, entry.getKey());
                buffer.writeBoolean(payload.unavailable().contains(entry.getKey()));
                List<ResourceLocation> peerIds = List.copyOf(entry.getValue().keySet());
                BoundedNetworkCodecs.TRANSFER_TYPE_IDS.encode(buffer, peerIds);
                for (ResourceLocation id : peerIds) {
                    var rate = entry.getValue().get(id);
                    buffer.writeDouble(rate.sent());
                    buffer.writeDouble(rate.received());
                }
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CTransferRatesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof LinkConfiguratorMenu menu
                && menu.containerId == payload.menuId() && menu.hasTarget()
                && payload.node().equals(menu.getTargetNode())) {
                menu.acceptTransferRates(payload.rates());
                menu.acceptTransferDiagnostic(payload.diagnostic());
                menu.acceptPeerRates(payload.peers(), payload.truncated(), payload.unavailable());
            }
        });
    }
}
