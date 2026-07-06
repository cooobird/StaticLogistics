package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.gui.menu.NodeConfiguratorMenu;
import com.coobird.staticlogistics.storage.link.LinkManager;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

public record C2SOpenNodeConfigPayload(BlockPos pos, Direction face) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("open_node_config");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SOpenNodeConfigPayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public C2SOpenNodeConfigPayload decode(PortRegistryFriendlyByteBuf buffer) {
            net.minecraft.network.FriendlyByteBuf fbuf = buffer;
            return new C2SOpenNodeConfigPayload(fbuf.readBlockPos(), fbuf.readEnum(Direction.class));
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buffer, C2SOpenNodeConfigPayload value) {
            net.minecraft.network.FriendlyByteBuf fbuf = buffer;
            fbuf.writeBlockPos(value.pos());
            fbuf.writeEnum(value.face());
        }
    };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        var title = player.level().getBlockState(pos()).getBlock().getName().copy();
        FaceConfigComposite config = LinkManager.get(player.serverLevel()).getFaceConfig(LinkManager.posToKey(pos(), face()));
        NetworkHooks.openScreen(player, new SimpleMenuProvider(
            (id, inv, p) -> new NodeConfiguratorMenu(id, inv, pos(), face()), title), buf -> {
            buf.writeBlockPos(pos());
            buf.writeEnum(face());
            NodeConfiguratorMenu.writeInitialTypeData(buf, StaticLogistics.asResource("item"), config);
        });
    }
}
