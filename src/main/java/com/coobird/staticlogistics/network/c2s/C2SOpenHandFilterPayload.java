package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.gui.menu.HandFilterMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record C2SOpenHandFilterPayload() implements CustomPacketPayload {

    public static final Type<C2SOpenHandFilterPayload> TYPE =
        new Type<>(Staticlogistics.asResource("open_hand_filter"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SOpenHandFilterPayload> STREAM_CODEC =
        StreamCodec.unit(new C2SOpenHandFilterPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final C2SOpenHandFilterPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                ItemStack stack = sp.getMainHandItem();
                sp.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new HandFilterMenu(id, inv, stack),
                    Component.translatable("gui.staticlogistics.hand_filter")
                ), buf -> ItemStack.STREAM_CODEC.encode(buf, stack));
            }
        });
    }
}