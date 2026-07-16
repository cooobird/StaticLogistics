package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.content.item.LinkOperationHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

public record C2SClearStoredNodesPayload() implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("clear_stored_nodes");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SClearStoredNodesPayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public C2SClearStoredNodesPayload decode(PortRegistryFriendlyByteBuf buffer) {
            return new C2SClearStoredNodesPayload();
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buffer, C2SClearStoredNodesPayload value) {
        }
    };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof LinkConfiguratorItem))
            stack = player.getOffhandItem();
        if (!(stack.getItem() instanceof LinkConfiguratorItem)) return;
        LinkOperationHelper.clearNodes(stack, player, player.level());
    }
}
