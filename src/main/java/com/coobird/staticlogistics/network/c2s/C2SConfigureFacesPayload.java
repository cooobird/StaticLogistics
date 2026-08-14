package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.node.FaceConfigurationEdit;
import com.coobird.staticlogistics.logistics.node.NodeMutationService;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import com.coobird.staticlogistics.network.TeamPacketSync;
import com.coobird.staticlogistics.network.s2c.S2CTopologyUpdatePayload;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * 客户端提交的批量面配置修改；模式、分组、类型与节点选择保持独立。
 */
public record C2SConfigureFacesPayload(GroupKey groupKey, List<LogisticsNode> nodes,
                                       FaceConfigurationEdit edit) implements CustomPacketPayload {
    private static final int MAX_TARGETS = 256;
    public static final Type<C2SConfigureFacesPayload> TYPE =
        new Type<>(StaticLogistics.asResource("configure_faces"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SConfigureFacesPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public C2SConfigureFacesPayload decode(RegistryFriendlyByteBuf buffer) {
                GroupKey groupKey = GroupKey.STREAM_CODEC.decode(buffer);
                int size = buffer.readVarInt();
                if (size < 1 || size > MAX_TARGETS) {
                    throw new DecoderException("Invalid batch face count: " + size);
                }
                List<LogisticsNode> nodes = new ArrayList<>(size);
                for (int index = 0; index < size; index++) {
                    nodes.add(LogisticsNode.STREAM_CODEC.decode(buffer));
                }
                return new C2SConfigureFacesPayload(
                    groupKey, nodes, C2SConfigureFacePayload.decodeEdit(buffer));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, C2SConfigureFacesPayload payload) {
                if (payload.nodes().size() > MAX_TARGETS) {
                    throw new EncoderException("Batch face count exceeds " + MAX_TARGETS);
                }
                GroupKey.STREAM_CODEC.encode(buffer, payload.groupKey());
                buffer.writeVarInt(payload.nodes().size());
                payload.nodes().forEach(node -> LogisticsNode.STREAM_CODEC.encode(buffer, node));
                C2SConfigureFacePayload.encodeEdit(buffer, payload.edit());
            }
        };

    public C2SConfigureFacesPayload {
        Objects.requireNonNull(groupKey, "Group key must not be null");
        Objects.requireNonNull(nodes, "Batch faces must not be null");
        Objects.requireNonNull(edit, "Face configuration edit must not be null");
        nodes = List.copyOf(new LinkedHashSet<>(nodes));
        if (nodes.isEmpty() || nodes.size() > MAX_TARGETS) {
            throw new IllegalArgumentException("Batch face count is invalid");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SConfigureFacesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !ServerPacketRateLimiter.allow(
                player, ServerPacketRateLimiter.Action.FACE_CONFIGURATION)
                || !(player.containerMenu instanceof LinkConfiguratorMenu menu)
                || !payload.groupKey().equals(menu.getRemoteGroupKey())
                || !menu.allowsEdit(payload.edit())) return;

            NodeMutationService mutations = new NodeMutationService();
            List<NodeMutationService.ValidatedNode> resolved = new ArrayList<>();
            for (LogisticsNode node : payload.nodes()) {
                NodeMutationService.ValidatedNode validated =
                    mutations.resolveRemote(player, node, payload.groupKey());
                if (validated == null) return;
                resolved.add(validated);
            }
            List<NodeMutationService.ValidatedNode> changed =
                mutations.configureAll(resolved, payload.edit());
            if (changed.isEmpty()) return;
            menu.syncFaceSlots();
            menu.broadcastChanges();
            List<S2CTopologyUpdatePayload.FaceUpdate> updates = changed.stream()
                .map(node -> S2CTopologyUpdatePayload.FaceUpdate.from(
                    node.level(), node.node(), node.config()))
                .toList();
            TeamPacketSync.sendTopology(player, payload.groupKey().ownerId(), updates);
        });
    }
}
