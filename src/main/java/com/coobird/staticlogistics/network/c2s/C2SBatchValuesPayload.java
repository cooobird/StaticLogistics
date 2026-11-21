package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.node.NodeMutationService;
import com.coobird.staticlogistics.logistics.selection.NodeSelectionPolicy;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import com.coobird.staticlogistics.network.s2c.S2CBatchValuesPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 仅查询已授权连接面的批量编辑公共值，不同步过滤器等私有配置。
 */
public record C2SBatchValuesPayload(UUID request, int menuId,
                                    Map<LogisticsNode, GroupKey> targets) implements CustomPacketPayload {
    public static final Type<C2SBatchValuesPayload> TYPE = new Type<>(StaticLogistics.asResource("batch_values_query"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SBatchValuesPayload> STREAM_CODEC = new StreamCodec<>() {
        public C2SBatchValuesPayload decode(RegistryFriendlyByteBuf buf) {
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

        public void encode(RegistryFriendlyByteBuf buf, C2SBatchValuesPayload value) {
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

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SBatchValuesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof LinkConfiguratorMenu menu) || menu.containerId != payload.menuId() || !menu.hasTarget() || menu.resolveValidatedNode(player) == null)
                return;
            if (!ServerPacketRateLimiter.allow(player, ServerPacketRateLimiter.Action.BATCH_VALUE_QUERY, payload.targets().size()))
                return;
            NodeMutationService service = new NodeMutationService();
            Long priority = null, stock = null;
            boolean mixedPriority = false, mixedStock = false;
            for (var entry : payload.targets().entrySet()) {
                var resolved = service.resolveRemote(player, entry.getKey(), entry.getValue());
                if (resolved == null) return;
                var config = resolved.config().linkConfig;
                if (priority != null && priority != config.getPriority()) mixedPriority = true;
                if (stock != null && stock != config.getKeepStock()) mixedStock = true;
                priority = config.getPriority();
                stock = config.getKeepStock();
            }
            if (priority != null)
                PacketDistributor.sendToPlayer(player, new S2CBatchValuesPayload(payload.request(), priority, stock, mixedPriority, mixedStock));
        });
    }
}
