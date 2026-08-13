package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.content.item.LinkConfiguratorSelection;
import com.coobird.staticlogistics.logistics.group.GroupCommandService;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.logistics.node.NodeInteractionValidator;
import com.coobird.staticlogistics.network.BoundedNetworkCodecs;
import com.coobird.staticlogistics.network.TeamPacketSync;
import com.coobird.staticlogistics.network.s2c.S2CGroupDirectoryPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/**
 * 客户端按稳定身份请求重命名或同所有者合并分组。
 */
public record C2SGroupRenamePayload(GroupKey groupKey, String newGroupId) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("group_rename");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SGroupRenamePayload> STREAM_CODEC =
        new PortStreamCodec<>() {
            @Override
            public C2SGroupRenamePayload decode(PortRegistryFriendlyByteBuf buffer) {
                return new C2SGroupRenamePayload(GroupKey.STREAM_CODEC.decode(buffer),
                    BoundedNetworkCodecs.GROUP_NAME.decode(buffer));
            }

            @Override
            public void encode(PortRegistryFriendlyByteBuf buffer, C2SGroupRenamePayload value) {
                GroupKey.STREAM_CODEC.encode(buffer, value.groupKey());
                BoundedNetworkCodecs.GROUP_NAME.encode(buffer, value.newGroupId());
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
        String newGroupName;
        try {
            newGroupName = GroupConstraints.normalizeName(newGroupId());
        } catch (IllegalArgumentException exception) {
            return;
        }
        var store = PlayerGroupStore.get(player.getServer());
        var target = store.findGroup(groupKey());
        if (target == null) return;
        boolean changed = new GroupCommandService(player.getServer())
            .rename(player, groupKey(), newGroupName);
        if (!changed) return;
        var result = store.findGroup(groupKey().ownerId(), newGroupName);
        LinkConfiguratorSelection.replaceIfSelected(
            player, target.key(), target.displayName(), result);
        TeamPacketSync.send(player, groupKey().ownerId(), new S2CGroupDirectoryPayload(
            groupKey().ownerId(), store.getGroupRefs(groupKey().ownerId())));
    }
}
