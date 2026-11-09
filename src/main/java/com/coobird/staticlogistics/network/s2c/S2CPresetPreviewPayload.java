package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.client.gui.screen.LinkConfiguratorScreen;
import com.coobird.staticlogistics.logistics.node.PresetPreviewService;
import com.coobird.staticlogistics.network.c2s.C2SConfigureFacePayload;
import io.netty.handler.codec.DecoderException;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;

public record S2CPresetPreviewPayload(int menuId, long requestId, LogisticsNode node,
                                      PresetPreviewService.Snapshot snapshot) implements CustomPacketPayload {
    public static final Type<S2CPresetPreviewPayload> TYPE = new Type<>(StaticLogistics.asResource("preset_preview_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CPresetPreviewPayload> STREAM_CODEC = new StreamCodec<>() {
        public S2CPresetPreviewPayload decode(RegistryFriendlyByteBuf buffer) {
            int menu = buffer.readVarInt();
            long request = buffer.readVarLong();
            var node = LogisticsNode.STREAM_CODEC.decode(buffer);
            if (!buffer.readBoolean()) return new S2CPresetPreviewPayload(menu, request, node, null);
            var group = GroupKey.STREAM_CODEC.decode(buffer);
            long version = buffer.readVarLong();
            int count = buffer.readVarInt();
            if (count < 1 || count > 8) throw new DecoderException("Invalid preset field count");
            var fields = new ArrayList<PresetPreviewService.Field>();
            for (int i = 0; i < count; i++) {
                int index = buffer.readVarInt();
                boolean available = buffer.readBoolean();
                if (index != i) throw new DecoderException("Invalid preset field order");
                fields.add(new PresetPreviewService.Field(index, C2SConfigureFacePayload.decodeEdit(buffer), available));
            }
            return new S2CPresetPreviewPayload(menu, request, node, new PresetPreviewService.Snapshot(node, group, version, fields));
        }

        public void encode(RegistryFriendlyByteBuf buffer, S2CPresetPreviewPayload payload) {
            buffer.writeVarInt(payload.menuId);
            buffer.writeVarLong(payload.requestId);
            LogisticsNode.STREAM_CODEC.encode(buffer, payload.node);
            buffer.writeBoolean(payload.snapshot != null);
            if (payload.snapshot == null) return;
            GroupKey.STREAM_CODEC.encode(buffer, payload.snapshot.group());
            buffer.writeVarLong(payload.snapshot.version());
            buffer.writeVarInt(payload.snapshot.fields().size());
            for (var field : payload.snapshot.fields()) {
                buffer.writeVarInt(field.editIndex());
                buffer.writeBoolean(field.available());
                C2SConfigureFacePayload.encodeEdit(buffer, field.value());
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CPresetPreviewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> display(payload));
    }

    private static void display(S2CPresetPreviewPayload payload) {
        if (Minecraft.getInstance().screen instanceof LinkConfiguratorScreen screen
            && screen.getMenu().containerId == payload.menuId)
            screen.acceptPresetPreview(payload.requestId, payload.node, payload.snapshot);
    }
}
