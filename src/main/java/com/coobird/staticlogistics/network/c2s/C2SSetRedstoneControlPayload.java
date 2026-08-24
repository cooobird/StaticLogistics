package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.group.ConnectionCommandService;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.redstone.RedstoneControlStore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/**
 * 解除一条连接已有的红石控制；绑定检测点由世界点选流程完成。
 */
public record C2SSetRedstoneControlPayload(ConnectionKey connection, boolean bind)
    implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("set_redstone_control");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf,
        C2SSetRedstoneControlPayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public C2SSetRedstoneControlPayload decode(PortRegistryFriendlyByteBuf buffer) {
            return new C2SSetRedstoneControlPayload(
                ConnectionKey.STREAM_CODEC.decode(buffer), buffer.readBoolean());
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buffer,
                           C2SSetRedstoneControlPayload payload) {
            ConnectionKey.STREAM_CODEC.encode(buffer, payload.connection());
            buffer.writeBoolean(payload.bind());
        }
    };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        if (!(player.containerMenu instanceof LinkConfiguratorMenu)
            || !new ConnectionCommandService(player.server).isSelectable(player, connection())) return;
        if (!bind()) RedstoneControlStore.get(player.server).unbind(connection());
        C2SQueryRedstoneGroupPayload.sendGroupToAuthorized(player, connection().groupKey());
    }
}
