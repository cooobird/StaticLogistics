package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.item.UpgradeItem;
import com.coobird.staticlogistics.content.menu.HandFilterMenu;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record C2SOpenHandFilterPayload(boolean offhand) implements CustomPacketPayload {

    public static final Type<C2SOpenHandFilterPayload> TYPE =
        new Type<>(StaticLogistics.asResource("open_hand_filter"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SOpenHandFilterPayload> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.BOOL, C2SOpenHandFilterPayload::offhand,
            C2SOpenHandFilterPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final C2SOpenHandFilterPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                InteractionHand hand = payload.offhand()
                    ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                ItemStack stack = sp.getItemInHand(hand);
                if (!(stack.getItem() instanceof UpgradeItem upgrade) || !upgrade.isFilterUpgrade()) return;
                FilterData current = stack.getOrDefault(
                    SLDataComponents.FILTER_DATA.get(), FilterData.EMPTY);
                FilterData normalized = current.normalizedFor(upgrade.getType());
                if (normalized != current) {
                    stack.set(SLDataComponents.FILTER_DATA.get(), normalized);
                }
                int inventorySlot = payload.offhand()
                    ? Inventory.SLOT_OFFHAND : sp.getInventory().selected;
                sp.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new HandFilterMenu(id, inv, stack, inventorySlot),
                    Component.translatable("gui.staticlogistics.hand_filter")
                ), buf -> {
                    buf.writeVarInt(inventorySlot);
                    ItemStack.STREAM_CODEC.encode(buf, stack);
                    FilterData.STREAM_CODEC.encode(buf, normalized);
                });
            }
        });
    }
}
