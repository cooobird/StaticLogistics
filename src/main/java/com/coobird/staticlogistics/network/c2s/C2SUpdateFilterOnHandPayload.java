package com.coobird.staticlogistics.network.c2s;

import PortLib.extensions.net.minecraft.world.item.ItemStack.PortItemStackExtension;
import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.item.UpgradeItem;
import com.coobird.staticlogistics.content.menu.HandFilterMenu;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

public record C2SUpdateFilterOnHandPayload(FilterData filter) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("update_filter_on_hand");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SUpdateFilterOnHandPayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public C2SUpdateFilterOnHandPayload decode(PortRegistryFriendlyByteBuf buffer) {
            FriendlyByteBuf fbuf = buffer;
            return new C2SUpdateFilterOnHandPayload(FilterData.STREAM_CODEC.decode(buffer));
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buffer, C2SUpdateFilterOnHandPayload value) {
            FilterData.STREAM_CODEC.encode(buffer, value.filter());
        }
    };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        if (!ServerPacketRateLimiter.allow(
            player, ServerPacketRateLimiter.Action.FILTER_UPDATE)) return;
        if (!(player.containerMenu instanceof HandFilterMenu menu) || !menu.stillValid(player)) return;
        ItemStack stack = menu.getBoundStack();
        if (!(stack.getItem() instanceof UpgradeItem upgrade) || !upgrade.isFilterUpgrade()) return;
        PortItemStackExtension.setData(stack, SLDataComponents.FILTER_DATA.get(), filter);
        menu.broadcastChanges();
        player.inventoryMenu.broadcastChanges();
    }
}
