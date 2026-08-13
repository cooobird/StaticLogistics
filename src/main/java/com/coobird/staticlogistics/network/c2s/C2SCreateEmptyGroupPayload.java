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
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/**
 * 客户端请求创建新的空分组。
 */
public record C2SCreateEmptyGroupPayload(String groupId) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("create_empty_group");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SCreateEmptyGroupPayload> STREAM_CODEC =
        new PortStreamCodec<>() {
            @Override
            public C2SCreateEmptyGroupPayload decode(PortRegistryFriendlyByteBuf buffer) {
                return new C2SCreateEmptyGroupPayload(BoundedNetworkCodecs.GROUP_NAME.decode(buffer));
            }

            @Override
            public void encode(PortRegistryFriendlyByteBuf buffer, C2SCreateEmptyGroupPayload value) {
                BoundedNetworkCodecs.GROUP_NAME.encode(buffer, value.groupId());
            }
        };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        if (!NodeInteractionValidator.holdsConfigurator(player)
            || player.getServer() == null) return;
        if (!ServerPacketRateLimiter.allow(
            player, ServerPacketRateLimiter.Action.GROUP_CREATION)) return;
        try {
            String normalized = GroupConstraints.normalizeName(groupId());
            PlayerGroupStore store = PlayerGroupStore.get(player.getServer());
            boolean isNewGroup = store.findGroup(player.getUUID(), normalized) == null;
            var created = new GroupCommandService(player.getServer()).create(player, normalized);
            LinkConfiguratorSelection.select(player, created);
            if (isNewGroup) {
                TeamPacketSync.send(player, player.getUUID(), new S2CGroupDirectoryPayload(
                    player.getUUID(), store.getGroupRefs(player.getUUID())));
            }
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            // 无效请求不改变服务端状态。
        }
    }
}
