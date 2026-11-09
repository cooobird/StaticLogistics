package com.coobird.staticlogistics.network.c2s;

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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

public record C2SUpdateFilterOnHandPayload(UpgradeType filterType, FilterData filter) implements IPortPacket.C2S {

    public static final ResourceLocation ID = StaticLogistics.asResource("update_filter_on_hand");

    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SUpdateFilterOnHandPayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public C2SUpdateFilterOnHandPayload decode(PortRegistryFriendlyByteBuf buf) {
            UpgradeType filterType = buf.readEnum(UpgradeType.class);
            if (!isFilterType(filterType)) throw new DecoderException("Invalid filter upgrade type: " + filterType);
            return new C2SUpdateFilterOnHandPayload(filterType, FilterData.STREAM_CODEC.decode(buf));
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buf, C2SUpdateFilterOnHandPayload payload) {
            if (!isFilterType(payload.filterType())) {
                throw new IllegalArgumentException("Invalid filter upgrade type: " + payload.filterType());
            }
            buf.writeEnum(payload.filterType());
            /// 类型只决定客户端写包时的数据表示；服务端仍以真实物品类型为准。
            FilterData.STREAM_CODEC.encode(buf, payload.filter().normalizedFor(payload.filterType()));
        }
    };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    private static boolean isFilterType(UpgradeType type) {
        return type == UpgradeType.FILTER;
    }

    public static void handle(final C2SUpdateFilterOnHandPayload payload, final Player context) {

        if (context instanceof ServerPlayer sp) {
            if (!ServerPacketRateLimiter.allow(
                sp, ServerPacketRateLimiter.Action.FILTER_UPDATE)) return;
            if (!(sp.containerMenu instanceof HandFilterMenu menu) || !menu.stillValid(sp)) return;
            ItemStack stack = menu.getBoundStack();
            if (!(stack.getItem() instanceof UpgradeItem upgrade) || !upgrade.isFilterUpgrade()) return;
            FilterData normalized = payload.filter().normalizedFor(upgrade.getType());
            stack.set(SLDataComponents.FILTER_DATA.get(), normalized);
            menu.broadcastChanges();
            sp.inventoryMenu.broadcastChanges();
        }

    }

    @Override
    public void work(ServerPlayer packetPlayer) {
        handle(this, packetPlayer);
    }
}
