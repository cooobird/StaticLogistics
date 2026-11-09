package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.content.item.LinkOperationHelper;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/// 清空链接配置器中已存储的节点
public record C2SClearStoredNodesPayload() implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("clear_stored_nodes");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SClearStoredNodesPayload> STREAM_CODEC =
        new PortStreamCodec<>() {
            @Override
            public C2SClearStoredNodesPayload decode(PortRegistryFriendlyByteBuf buf) {
                return new C2SClearStoredNodesPayload();
            }

            @Override
            public void encode(PortRegistryFriendlyByteBuf buf, C2SClearStoredNodesPayload payload) {
            }
        };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    public static void handle(final C2SClearStoredNodesPayload payload, final Player context) {

        if (!(context instanceof ServerPlayer sp)) return;
        if (!ServerPacketRateLimiter.allow(sp, ServerPacketRateLimiter.Action.STORED_NODE_CLEAR)) return;
        ItemStack stack = sp.getMainHandItem();
        if (!(stack.getItem() instanceof LinkConfiguratorItem))
            stack = sp.getOffhandItem();
        if (!(stack.getItem() instanceof LinkConfiguratorItem)) return;

        LinkOperationHelper.clearNodes(stack, sp, sp.level());

    }

    @Override
    public void work(ServerPlayer packetPlayer) {
        handle(this, packetPlayer);
    }
}
