package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.gui.menu.HandFilterMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

public record C2SOpenHandFilterPayload() implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("open_hand_filter");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SOpenHandFilterPayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public C2SOpenHandFilterPayload decode(PortRegistryFriendlyByteBuf buffer) {
            return new C2SOpenHandFilterPayload();
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buffer, C2SOpenHandFilterPayload value) {
        }
    };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        NetworkHooks.openScreen(player, new SimpleMenuProvider(
            (id, inv, p) -> new HandFilterMenu(id, inv, stack),
            Component.translatable("gui.staticlogistics.hand_filter")
        ), buf -> buf.writeItem(stack));
    }
}
