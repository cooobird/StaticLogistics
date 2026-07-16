package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.content.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.content.item.LinkConfiguratorSelection;
import com.coobird.staticlogistics.logistics.group.GroupCommandService;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.network.BoundedNetworkCodecs;
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
        boolean holdsTool = player.getMainHandItem().getItem() instanceof LinkConfiguratorItem
            || player.getOffhandItem().getItem() instanceof LinkConfiguratorItem;
        if (!holdsTool || player.getServer() == null) return;
        try {
            String normalized = GroupConstraints.normalizeName(groupId());
            var created = new GroupCommandService(player.getServer()).create(player, normalized);
            LinkConfiguratorSelection.select(player, created);
            TeamPacketSync.send(player, new S2CGroupDirectoryPayload(
                player.getUUID(), PlayerGroupStore.get(player.getServer())
                .getGroupRefs(player.getUUID())));
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            // 无效请求不改变服务端状态。
        }
    }
}
