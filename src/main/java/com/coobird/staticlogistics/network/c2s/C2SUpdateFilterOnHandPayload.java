package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.filter.data.FilterData;
import com.coobird.staticlogistics.registry.SLDataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record C2SUpdateFilterOnHandPayload(FilterData filter) implements CustomPacketPayload {

    public static final Type<C2SUpdateFilterOnHandPayload> TYPE =
        new Type<>(Staticlogistics.asResource("update_filter_on_hand"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SUpdateFilterOnHandPayload> STREAM_CODEC =
        StreamCodec.composite(
            FilterData.STREAM_CODEC, C2SUpdateFilterOnHandPayload::filter,
            C2SUpdateFilterOnHandPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final C2SUpdateFilterOnHandPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                ItemStack stack = sp.getMainHandItem();
                stack.set(SLDataComponents.FILTER_DATA.get(), payload.filter());
                sp.inventoryMenu.broadcastChanges();
            }
        });
    }
}