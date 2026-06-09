package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.logic.GlobalLogisticsManager;
import com.coobird.staticlogistics.network.SLNetwork;
import com.coobird.staticlogistics.network.s2c.S2CRemoveBulkFaceConfigPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

public record C2SDeleteGroupPayload(String groupId) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("delete_group");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SDeleteGroupPayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public C2SDeleteGroupPayload decode(PortRegistryFriendlyByteBuf buffer) {
            net.minecraft.network.FriendlyByteBuf fbuf = buffer;
            return new C2SDeleteGroupPayload(fbuf.readUtf());
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buffer, C2SDeleteGroupPayload value) {
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
        var server = player.getServer();
        if (server == null) return;
        GlobalLogisticsManager manager = GlobalLogisticsManager.get(server);
        var faceEntries = manager.collectGroupFaceConfigs(groupId());
        // 统一删除分组（包括空分组和有内容的分组）
        manager.removeGroup(player.getUUID(), groupId());
        if (!faceEntries.isEmpty()) {
            var s2cEntries = faceEntries.stream()
                .map(e -> new S2CRemoveBulkFaceConfigPayload.Entry(e.pos(), e.face()))
                .toList();
            SLNetwork.HANDLER.sendToPlayer(player,
                new S2CRemoveBulkFaceConfigPayload(s2cEntries));
        }
    }
}
