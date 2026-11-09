package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.content.item.LinkConfiguratorClientHooks;
import io.netty.handler.codec.DecoderException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/// 选点同步按页传输，物品同步不再承载整份列表。
public record S2CStoredSelectionPage(UUID id, int page, boolean last,
                                     List<LogisticsNode> nodes) implements IPortPacket.S2C {
    public static final int PAGE_SIZE = 128;
    public static final ResourceLocation ID = StaticLogistics.asResource("stored_selection_page");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, S2CStoredSelectionPage> STREAM_CODEC = new PortStreamCodec<>() {
        public S2CStoredSelectionPage decode(PortRegistryFriendlyByteBuf buffer) {
            UUID id = buffer.readUUID();
            int page = buffer.readVarInt();
            boolean last = buffer.readBoolean();
            int count = buffer.readVarInt();
            if (page < 0 || count < 0 || count > PAGE_SIZE) throw new DecoderException("Invalid selection page");
            List<LogisticsNode> nodes = new ArrayList<>(count);
            for (int i = 0; i < count; i++) nodes.add(LogisticsNode.STREAM_CODEC.decode(buffer));
            return new S2CStoredSelectionPage(id, page, last, List.copyOf(nodes));
        }

        public void encode(PortRegistryFriendlyByteBuf buffer, S2CStoredSelectionPage value) {
            if (value.nodes().size() > PAGE_SIZE) throw new IllegalArgumentException("Selection page too large");
            buffer.writeUUID(value.id());
            buffer.writeVarInt(value.page());
            buffer.writeBoolean(value.last());
            buffer.writeVarInt(value.nodes().size());
            value.nodes().forEach(node -> LogisticsNode.STREAM_CODEC.encode(buffer, node));
        }
    };

    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(Player packetPlayer) {
        S2CStoredSelectionPage page = this;
        Player context = packetPlayer;
        LinkConfiguratorClientHooks.acceptSelectionPage(page);
    }
}
