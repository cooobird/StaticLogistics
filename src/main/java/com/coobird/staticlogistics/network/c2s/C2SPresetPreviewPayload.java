package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.node.ChannelConfigurationEdit;
import com.coobird.staticlogistics.logistics.node.PresetPreviewService;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import com.coobird.staticlogistics.network.s2c.S2CPresetPreviewPayload;
import io.netty.handler.codec.DecoderException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortPacketDistributor;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.util.ArrayList;
import java.util.List;

/// 单次只读取一个目标面，避免批量传输所有过滤器数据。
public record C2SPresetPreviewPayload(int menuId, long requestId, LogisticsNode node, GroupKey group,
                                      List<ChannelConfigurationEdit> edits) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("preset_preview_request");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SPresetPreviewPayload> STREAM_CODEC = new PortStreamCodec<>() {
        public C2SPresetPreviewPayload decode(PortRegistryFriendlyByteBuf buffer) {
            int menuId = buffer.readVarInt();
            long requestId = buffer.readVarLong();
            var node = LogisticsNode.STREAM_CODEC.decode(buffer);
            var group = GroupKey.STREAM_CODEC.decode(buffer);
            int count = buffer.readVarInt();
            if (count < 1 || count > 8) throw new DecoderException("Invalid preset field count");
            List<ChannelConfigurationEdit> edits = new ArrayList<>(count);
            for (int i = 0; i < count; i++) edits.add(C2SConfigureFacePayload.decodeEdit(buffer));
            return new C2SPresetPreviewPayload(menuId, requestId, node, group, List.copyOf(edits));
        }

        public void encode(PortRegistryFriendlyByteBuf buffer, C2SPresetPreviewPayload payload) {
            if (payload.edits.size() < 1 || payload.edits.size() > 8)
                throw new IllegalArgumentException("Invalid preset field count");
            buffer.writeVarInt(payload.menuId);
            buffer.writeVarLong(payload.requestId);
            LogisticsNode.STREAM_CODEC.encode(buffer, payload.node);
            GroupKey.STREAM_CODEC.encode(buffer, payload.group);
            buffer.writeVarInt(payload.edits.size());
            payload.edits.forEach(edit -> C2SConfigureFacePayload.encodeEdit(buffer, edit));
        }
    };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        handle(this, player);
    }

    private static void handle(C2SPresetPreviewPayload payload, ServerPlayer player) {
        if (!(player.containerMenu instanceof LinkConfiguratorMenu menu) || menu.containerId != payload.menuId
            || !menu.hasTarget() || !ServerPacketRateLimiter.allow(player, ServerPacketRateLimiter.Action.BATCH_VALUE_QUERY)
            || payload.edits.stream().anyMatch(edit -> !menu.allowsEdit(edit))) return;
        var focus = menu.resolveValidatedNode(player);
        PresetPreviewService.Snapshot snapshot = focus == null ? null : PresetPreviewService.snapshot(
            player, payload.node, payload.group, focus.config().determineRole(), payload.edits);
        PortPacketDistributor.sendToPlayer(player, new S2CPresetPreviewPayload(payload.menuId, payload.requestId, payload.node, snapshot));
    }
}
