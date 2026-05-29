package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.item.util.LinkOperationHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 清空链接配置器中已存储的节点
 */
public record C2SClearStoredNodesPayload() implements CustomPacketPayload {
    public static final Type<C2SClearStoredNodesPayload> TYPE = new Type<>(Staticlogistics.asResource("clear_stored_nodes"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SClearStoredNodesPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public C2SClearStoredNodesPayload decode(RegistryFriendlyByteBuf buf) {
                return new C2SClearStoredNodesPayload();
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, C2SClearStoredNodesPayload payload) {
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final C2SClearStoredNodesPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            ItemStack stack = sp.getMainHandItem();
            if (!(stack.getItem() instanceof LinkConfiguratorItem))
                stack = sp.getOffhandItem();
            if (!(stack.getItem() instanceof LinkConfiguratorItem)) return;

            LinkOperationHelper.clearNodes(stack, sp, sp.level());
        });
    }
}
