package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.node.NodeMutationService;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import com.coobird.staticlogistics.network.TeamPacketSync;
import com.coobird.staticlogistics.network.s2c.S2CTopologyUpdatePayload;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.chat.Component;
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
 * 将当前节点的完整配置事务化应用到所选节点。
 */
public record C2SApplyNodeTemplatePayload(GroupKey groupKey, LogisticsNode source,
                                          List<LogisticsNode> targets) implements IPortPacket.C2S {
    private static final int MAX_TARGETS = 256;
    public static final ResourceLocation ID = StaticLogistics.asResource("apply_node_template");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SApplyNodeTemplatePayload> STREAM_CODEC =
        new PortStreamCodec<>() {
            @Override
            public C2SApplyNodeTemplatePayload decode(PortRegistryFriendlyByteBuf buffer) {
                GroupKey groupKey = GroupKey.STREAM_CODEC.decode(buffer);
                LogisticsNode source = LogisticsNode.STREAM_CODEC.decode(buffer);
                int size = buffer.readVarInt();
                if (size < 2 || size > MAX_TARGETS) {
                    throw new DecoderException("Invalid template target count: " + size);
                }
                List<LogisticsNode> targets = new ArrayList<>(size);
                for (int index = 0; index < size; index++) {
                    targets.add(LogisticsNode.STREAM_CODEC.decode(buffer));
                }
                return new C2SApplyNodeTemplatePayload(groupKey, source, targets);
            }

            @Override
            public void encode(PortRegistryFriendlyByteBuf buffer,
                               C2SApplyNodeTemplatePayload payload) {
                if (payload.targets().size() > MAX_TARGETS) {
                    throw new EncoderException("Template target count exceeds " + MAX_TARGETS);
                }
                GroupKey.STREAM_CODEC.encode(buffer, payload.groupKey());
                LogisticsNode.STREAM_CODEC.encode(buffer, payload.source());
                buffer.writeVarInt(payload.targets().size());
                payload.targets().forEach(node -> LogisticsNode.STREAM_CODEC.encode(buffer, node));
            }
        };

    public C2SApplyNodeTemplatePayload {
        Objects.requireNonNull(groupKey, "Group key must not be null");
        Objects.requireNonNull(source, "Template source must not be null");
        Objects.requireNonNull(targets, "Template targets must not be null");
        targets = List.copyOf(new LinkedHashSet<>(targets));
        if (targets.size() < 2 || targets.size() > MAX_TARGETS || !targets.contains(source)) {
            throw new IllegalArgumentException("Template target set is invalid");
        }
    }

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        if (!ServerPacketRateLimiter.allow(player, ServerPacketRateLimiter.Action.NODE_TEMPLATE_CONFIGURATION, targets().size())
            || !(player.containerMenu instanceof LinkConfiguratorMenu menu)
            || !menu.hasTarget()
            || !groupKey().equals(menu.getRemoteGroupKey())
            || !source().equals(menu.getTargetNode())) return;
        NodeMutationService mutations = new NodeMutationService();
        NodeMutationService.ValidatedNode sourceNode =
            mutations.resolveRemote(player, source(), groupKey());
        if (sourceNode == null) return;
        List<NodeMutationService.ValidatedNode> resolved = new ArrayList<>();
        for (LogisticsNode node : targets()) {
            NodeMutationService.ValidatedNode target =
                mutations.resolveRemote(player, node, groupKey());
            if (target == null) return;
            resolved.add(target);
        }
        List<NodeMutationService.ValidatedNode> changed =
            mutations.applyTemplate(sourceNode, resolved);
        if (changed.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                "msg.staticlogistics.node_template_missing_items"), true);
            return;
        }
        menu.syncFaceSlots();
        menu.syncContainerSlots();
        menu.broadcastChanges();
        TeamPacketSync.sendTopology(player, groupKey().ownerId(), changed.stream()
            .map(node -> S2CTopologyUpdatePayload.FaceUpdate.from(
                node.level(), node.node(), node.config()))
            .toList());
        player.displayClientMessage(Component.translatable(
            "msg.staticlogistics.node_template_applied", changed.size()), true);
    }
}
