package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.content.item.LinkConfiguratorClientHooks;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/// 选点同步按页传输，物品同步不再承载整份列表。
public record S2CStoredSelectionPage(UUID id, int page, boolean last,
                                     List<LogisticsNode> nodes) implements CustomPacketPayload {
    public static final int PAGE_SIZE = 128;
    public static final Type<S2CStoredSelectionPage> TYPE = new Type<>(StaticLogistics.asResource("stored_selection_page"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CStoredSelectionPage> STREAM_CODEC = new StreamCodec<>() {
        public S2CStoredSelectionPage decode(RegistryFriendlyByteBuf buffer) {
            UUID id = buffer.readUUID();
            int page = buffer.readVarInt();
            boolean last = buffer.readBoolean();
            int count = buffer.readVarInt();
            if (page < 0 || count < 0 || count > PAGE_SIZE) throw new DecoderException("Invalid selection page");
            List<LogisticsNode> nodes = new ArrayList<>(count);
            for (int i = 0; i < count; i++) nodes.add(LogisticsNode.STREAM_CODEC.decode(buffer));
            return new S2CStoredSelectionPage(id, page, last, List.copyOf(nodes));
        }

        public void encode(RegistryFriendlyByteBuf buffer, S2CStoredSelectionPage value) {
            if (value.nodes().size() > PAGE_SIZE) throw new IllegalArgumentException("Selection page too large");
            buffer.writeUUID(value.id());
            buffer.writeVarInt(value.page());
            buffer.writeBoolean(value.last());
            buffer.writeVarInt(value.nodes().size());
            value.nodes().forEach(node -> LogisticsNode.STREAM_CODEC.encode(buffer, node));
        }
    };

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CStoredSelectionPage page, IPayloadContext context) {
        context.enqueueWork(() -> LinkConfiguratorClientHooks.acceptSelectionPage(page));
    }
}
