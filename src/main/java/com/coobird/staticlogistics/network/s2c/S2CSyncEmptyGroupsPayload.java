package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 同步玩家的空分组列表（创建了但没有节点的分组）
 */
public record S2CSyncEmptyGroupsPayload(UUID playerId, Set<String> emptyGroups) implements CustomPacketPayload {

    public static final Type<S2CSyncEmptyGroupsPayload> TYPE = new Type<>(StaticLogistics.asResource("sync_empty_groups"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CSyncEmptyGroupsPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public S2CSyncEmptyGroupsPayload decode(RegistryFriendlyByteBuf buf) {
            UUID playerId = buf.readUUID();
            int size = buf.readVarInt();
            Set<String> groups = new HashSet<>();
            for (int i = 0; i < size; i++) {
                groups.add(buf.readUtf(64));
            }
            return new S2CSyncEmptyGroupsPayload(playerId, groups);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, S2CSyncEmptyGroupsPayload packet) {
            buf.writeUUID(packet.playerId());
            buf.writeVarInt(packet.emptyGroups().size());
            for (String gid : packet.emptyGroups()) {
                buf.writeUtf(gid, 64);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CSyncEmptyGroupsPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientLinkData.INSTANCE.setEmptyGroups(packet.playerId(), packet.emptyGroups());
        });
    }
}
