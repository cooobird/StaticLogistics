package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import com.coobird.staticlogistics.network.s2c.S2CRemoveBulkFaceConfigPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端请求删除指定分组及其所有链接。
 */
public record C2SDeleteGroupPayload(String groupId) implements CustomPacketPayload {

    public static final Type<C2SDeleteGroupPayload> TYPE = new Type<>(Staticlogistics.asResource("delete_group"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SDeleteGroupPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, C2SDeleteGroupPayload::groupId,
            C2SDeleteGroupPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final C2SDeleteGroupPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player instanceof ServerPlayer sp)) return;
            Level level = sp.level();
            var server = sp.getServer();
            if (server == null) return;

            GlobalLogisticsManager manager = GlobalLogisticsManager.get(server);

            // 收集该组所有面配置信息
            var faceEntries = manager.collectGroupFaceConfigs(payload.groupId());

            // 删除该组所有节点
            manager.removeGroup(payload.groupId());

            // 向客户端广播移除
            if (!faceEntries.isEmpty()) {
                var s2cEntries = faceEntries.stream()
                    .map(e -> new S2CRemoveBulkFaceConfigPacket.Entry(e.pos(), e.face()))
                    .toList();
                PacketDistributor.sendToPlayer(sp,
                    new S2CRemoveBulkFaceConfigPacket(s2cEntries));
            }
        });
    }
}
