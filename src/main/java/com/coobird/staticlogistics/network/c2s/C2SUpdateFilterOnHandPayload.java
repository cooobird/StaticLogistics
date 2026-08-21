package com.coobird.staticlogistics.network.c2s;

import PortLib.extensions.net.minecraft.world.item.ItemStack.PortItemStackExtension;
import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.item.UpgradeItem;
import com.coobird.staticlogistics.content.menu.HandFilterMenu;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import com.coobird.staticlogistics.transfer.UpgradeType;
import io.netty.handler.codec.DecoderException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

public record C2SUpdateFilterOnHandPayload(UpgradeType filterType, FilterData filter) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("update_filter_on_hand");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SUpdateFilterOnHandPayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public C2SUpdateFilterOnHandPayload decode(PortRegistryFriendlyByteBuf buffer) {
            UpgradeType filterType = buffer.readEnum(UpgradeType.class);
            if (!isFilterType(filterType)) throw new DecoderException("Invalid filter upgrade type: " + filterType);
            return new C2SUpdateFilterOnHandPayload(filterType, FilterData.STREAM_CODEC.decode(buffer));
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buffer, C2SUpdateFilterOnHandPayload value) {
            if (!isFilterType(value.filterType())) {
                throw new IllegalArgumentException("Invalid filter upgrade type: " + value.filterType());
            }
            buffer.writeEnum(value.filterType());
            // 类型只决定客户端写包时的数据表示；服务端仍以真实物品类型为准。
            FilterData.STREAM_CODEC.encode(buffer, value.filter().normalizedFor(value.filterType()));
        }
    };

    private static boolean isFilterType(UpgradeType type) {
        return type == UpgradeType.BASIC_FILTER || type == UpgradeType.TAG_FILTER || type == UpgradeType.NBT_FILTER;
    }

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
        FilterData normalized = filter.normalizedFor(upgrade.getType());
        PortItemStackExtension.setData(stack, SLDataComponents.FILTER_DATA.get(), normalized);
        menu.broadcastChanges();
        player.inventoryMenu.broadcastChanges();
    }
}
