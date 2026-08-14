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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * 客户端提交的批量面配置修改。
 */
public record C2SConfigureFacesPayload(GroupKey groupKey, List<LogisticsNode> nodes,
                                       FaceConfigurationEdit edit) implements IPortPacket.C2S {
    private static final int MAX_TARGETS = 256;
    public static final ResourceLocation ID = StaticLogistics.asResource("configure_faces");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SConfigureFacesPayload> STREAM_CODEC =
        new PortStreamCodec<>() {
            @Override
            public C2SConfigureFacesPayload decode(PortRegistryFriendlyByteBuf buffer) {
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
            public void encode(PortRegistryFriendlyByteBuf buffer,
                               C2SConfigureFacesPayload payload) {
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
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        if (!ServerPacketRateLimiter.allow(player, ServerPacketRateLimiter.Action.FACE_CONFIGURATION)
            || !(player.containerMenu instanceof LinkConfiguratorMenu menu)
            || !groupKey().equals(menu.getRemoteGroupKey())
            || !menu.allowsEdit(edit())) return;
        NodeMutationService mutations = new NodeMutationService();
        List<NodeMutationService.ValidatedNode> resolved = new ArrayList<>();
        for (LogisticsNode node : nodes()) {
            NodeMutationService.ValidatedNode validated =
                mutations.resolveRemote(player, node, groupKey());
            if (validated == null) return;
            resolved.add(validated);
        }
        List<NodeMutationService.ValidatedNode> changed = mutations.configureAll(resolved, edit());
        if (changed.isEmpty()) return;
        menu.syncFaceSlots();
        menu.broadcastChanges();
        TeamPacketSync.sendTopology(player, groupKey().ownerId(), changed.stream()
            .map(node -> S2CTopologyUpdatePayload.FaceUpdate.from(
                node.level(), node.node(), node.config()))
            .toList());
    }
}
