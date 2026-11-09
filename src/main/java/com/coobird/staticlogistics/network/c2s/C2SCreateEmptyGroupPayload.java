package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.content.item.LinkConfiguratorSelection;
import com.coobird.staticlogistics.logistics.group.GroupCommandService;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.logistics.node.NodeInteractionValidator;
import com.coobird.staticlogistics.network.BoundedNetworkCodecs;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import com.coobird.staticlogistics.network.TeamPacketSync;
import com.coobird.staticlogistics.network.s2c.S2CGroupDirectoryPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/// 客户端通知服务端创建了一个新的空分组
public record C2SCreateEmptyGroupPayload(String groupId) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("create_empty_group");

    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SCreateEmptyGroupPayload> STREAM_CODEC = PortStreamCodec.composite(
        BoundedNetworkCodecs.GROUP_NAME, C2SCreateEmptyGroupPayload::groupId,
        C2SCreateEmptyGroupPayload::new
    );

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    public static void handle(final C2SCreateEmptyGroupPayload payload, final Player context) {

        var player = context;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!NodeInteractionValidator.holdsConfigurator(serverPlayer) || player.getServer() == null) return;
        if (!ServerPacketRateLimiter.allow(
            serverPlayer, ServerPacketRateLimiter.Action.GROUP_CREATION)) {
            return;
        }
        try {
            String groupId = GroupConstraints.normalizeName(payload.groupId());
            PlayerGroupStore store = PlayerGroupStore.get(player.getServer());
            boolean isNewGroup = store.findGroup(player.getUUID(), groupId) == null;
            var created = new GroupCommandService(player.getServer()).create(serverPlayer, groupId);
            LinkConfiguratorSelection.select(player, created);
            if (isNewGroup) {
                var groups = store.getGroupRefs(player.getUUID());
                TeamPacketSync.send(serverPlayer, player.getUUID(),
                    new S2CGroupDirectoryPayload(player.getUUID(), groups));
            }
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            /// 无效请求不改变服务端状态。
        }

    }

    @Override
    public void work(ServerPlayer packetPlayer) {
        handle(this, packetPlayer);
    }
}
