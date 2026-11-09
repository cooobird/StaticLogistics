package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.item.UpgradeItem;
import com.coobird.staticlogistics.content.menu.HandFilterMenu;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortByteBufCodecs;
import org.mesdag.portlib.network.codec.PortStreamCodec;
import org.mesdag.portlib.wrapper.common.extensions.IPortFriendlyByteBufExtension;

public record C2SOpenHandFilterPayload(boolean offhand) implements IPortPacket.C2S {

    public static final ResourceLocation ID = StaticLogistics.asResource("open_hand_filter");

    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SOpenHandFilterPayload> STREAM_CODEC =
        PortStreamCodec.composite(PortByteBufCodecs.BOOL, C2SOpenHandFilterPayload::offhand,
            C2SOpenHandFilterPayload::new);

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    public static void handle(final C2SOpenHandFilterPayload payload, final Player context) {

        if (context instanceof ServerPlayer sp) {
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
            NetworkHooks.openScreen(sp, new SimpleMenuProvider(
                (id, inv, p) -> new HandFilterMenu(id, inv, stack, inventorySlot),
                Component.translatable("gui.staticlogistics.hand_filter")
            ), buf -> {
                buf.writeVarInt(inventorySlot);
                ItemStack.STREAM_CODEC.encode(IPortFriendlyByteBufExtension.of(buf).wrap(), stack);
                FilterData.STREAM_CODEC.encode(IPortFriendlyByteBufExtension.of(buf).wrap(), normalized);
            });
        }

    }

    @Override
    public void work(ServerPlayer packetPlayer) {
        handle(this, packetPlayer);
    }
}
