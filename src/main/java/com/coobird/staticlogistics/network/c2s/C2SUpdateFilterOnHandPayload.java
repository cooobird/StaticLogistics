package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.item.UpgradeItem;
import com.coobird.staticlogistics.content.menu.HandFilterMenu;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import com.coobird.staticlogistics.transfer.UpgradeType;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record C2SUpdateFilterOnHandPayload(UpgradeType filterType, FilterData filter) implements CustomPacketPayload {

    public static final Type<C2SUpdateFilterOnHandPayload> TYPE = new Type<>(StaticLogistics.asResource("update_filter_on_hand"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SUpdateFilterOnHandPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public C2SUpdateFilterOnHandPayload decode(RegistryFriendlyByteBuf buf) {
            UpgradeType filterType = buf.readEnum(UpgradeType.class);
            if (!isFilterType(filterType)) throw new DecoderException("Invalid filter upgrade type: " + filterType);
            return new C2SUpdateFilterOnHandPayload(filterType, FilterData.STREAM_CODEC.decode(buf));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, C2SUpdateFilterOnHandPayload payload) {
            if (!isFilterType(payload.filterType())) {
                throw new IllegalArgumentException("Invalid filter upgrade type: " + payload.filterType());
            }
            buf.writeEnum(payload.filterType());
            // 类型只决定客户端写包时的数据表示；服务端仍以真实物品类型为准。
            FilterData.STREAM_CODEC.encode(buf, payload.filter().normalizedFor(payload.filterType()));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static boolean isFilterType(UpgradeType type) {
        return type == UpgradeType.BASIC_FILTER || type == UpgradeType.TAG_FILTER || type == UpgradeType.NBT_FILTER;
    }

    public static void handle(final C2SUpdateFilterOnHandPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
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
        });
    }
}
