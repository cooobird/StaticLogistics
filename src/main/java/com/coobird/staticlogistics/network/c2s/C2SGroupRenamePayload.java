package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.logic.GlobalLogisticsManager;
import com.coobird.staticlogistics.logic.group.GroupService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

public record C2SGroupRenamePayload(String oldGroupId,
                                    String newGroupId) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("group_rename");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SGroupRenamePayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public C2SGroupRenamePayload decode(PortRegistryFriendlyByteBuf buffer) {
            net.minecraft.network.FriendlyByteBuf fbuf = buffer;
            return new C2SGroupRenamePayload(fbuf.readUtf(), fbuf.readUtf());
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buffer, C2SGroupRenamePayload value) {
            net.minecraft.network.FriendlyByteBuf fbuf = buffer;
            fbuf.writeUtf(value.oldGroupId());
            fbuf.writeUtf(value.newGroupId());
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
        GlobalLogisticsManager globalManager = GlobalLogisticsManager.get(server);
        GroupService.renameGroup(player.level(), player, oldGroupId(), newGroupId(), globalManager);
        updateConfiguratorGroup(player, oldGroupId(), newGroupId());
    }

    private static void updateConfiguratorGroup(net.minecraft.world.entity.player.Player player, String oldId, String newId) {
        // Update the configurator screen if it's open
    }
}
