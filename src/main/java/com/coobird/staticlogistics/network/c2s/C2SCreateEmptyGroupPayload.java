package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.logic.GlobalLogisticsManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

public record C2SCreateEmptyGroupPayload(String groupId) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("create_empty_group");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SCreateEmptyGroupPayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public C2SCreateEmptyGroupPayload decode(PortRegistryFriendlyByteBuf buffer) {
            net.minecraft.network.FriendlyByteBuf fbuf = buffer;
            return new C2SCreateEmptyGroupPayload(fbuf.readUtf());
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buffer, C2SCreateEmptyGroupPayload value) {
            net.minecraft.network.FriendlyByteBuf fbuf = buffer;
            fbuf.writeUtf(value.groupId());
        }
    };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        String groupId = groupId().trim();
        if (!groupId.isEmpty() && player.getServer() != null) {
            GlobalLogisticsManager.get(player.getServer()).addGroup(player.getUUID(), groupId);
        }
    }
}
