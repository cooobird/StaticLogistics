package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.node.NodeMutationService;
import com.coobird.staticlogistics.logistics.selection.NodeSelectionPolicy;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import com.coobird.staticlogistics.network.s2c.S2CBatchValuesPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortPacketDistributor;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 仅查询已授权连接面的批量编辑公共值，不同步过滤器等私有配置。
 */
public record C2SBatchValuesPayload(UUID request, int menuId,
                                    Map<LogisticsNode, GroupKey> targets) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("batch_values_query");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SBatchValuesPayload> STREAM_CODEC = new PortStreamCodec<>() {
        public C2SBatchValuesPayload decode(PortRegistryFriendlyByteBuf buf) {
            UUID request = buf.readUUID();
            int menuId = buf.readVarInt();
            int count = buf.readVarInt();
            if (count < 1 || count > NodeSelectionPolicy.MAX_NODES)
                throw new io.netty.handler.codec.DecoderException("Invalid batch size");
            Map<LogisticsNode, GroupKey> targets = new LinkedHashMap<>();
            for (int i = 0; i < count; i++)
                targets.put(LogisticsNode.STREAM_CODEC.decode(buf), GroupKey.STREAM_CODEC.decode(buf));
            return new C2SBatchValuesPayload(request, menuId, targets);
        }

        public void encode(PortRegistryFriendlyByteBuf buf, C2SBatchValuesPayload value) {
            buf.writeUUID(value.request());
            buf.writeVarInt(value.menuId());
            buf.writeVarInt(value.targets().size());
            value.targets().forEach((node, group) -> {
                LogisticsNode.STREAM_CODEC.encode(buf, node);
                GroupKey.STREAM_CODEC.encode(buf, group);
            });
        }
    };

    public C2SBatchValuesPayload {
        targets = Map.copyOf(targets);
    }

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        if (!(player.containerMenu instanceof LinkConfiguratorMenu menu) || menu.containerId != menuId || !menu.hasTarget() || menu.resolveValidatedNode(player) == null)
            return;
        if (!ServerPacketRateLimiter.allow(player, ServerPacketRateLimiter.Action.BATCH_VALUE_QUERY, targets.size()))
            return;
        NodeMutationService service = new NodeMutationService();
        Long priority = null, stock = null;
        boolean mixedPriority = false, mixedStock = false;
        for (var entry : targets.entrySet()) {
            var resolved = service.resolveRemote(player, entry.getKey(), entry.getValue());
            if (resolved == null) return;
            var config = resolved.config().linkConfig;
            if (priority != null && priority != config.getPriority()) mixedPriority = true;
            if (stock != null && stock != config.getKeepStock()) mixedStock = true;
            priority = config.getPriority();
            stock = config.getKeepStock();
        }
        if (priority != null)
            PortPacketDistributor.sendToPlayer(player, new S2CBatchValuesPayload(request, priority, stock, mixedPriority, mixedStock));
    }
}
