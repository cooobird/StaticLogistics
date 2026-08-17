package com.coobird.staticlogistics.network.c2s;

import PortLib.extensions.net.minecraft.world.item.ItemStack.PortItemStackExtension;
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
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

public record C2SOpenHandFilterPayload(boolean offhand) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("open_hand_filter");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SOpenHandFilterPayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public C2SOpenHandFilterPayload decode(PortRegistryFriendlyByteBuf buffer) {
            return new C2SOpenHandFilterPayload(buffer.readBoolean());
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buffer, C2SOpenHandFilterPayload value) {
            buffer.writeBoolean(value.offhand());
        }
    };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        InteractionHand hand = offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof UpgradeItem upgrade) || !upgrade.isFilterUpgrade()) return;
        FilterData current = PortItemStackExtension.getDataOrDefault(
            stack, SLDataComponents.FILTER_DATA.get(), FilterData.EMPTY);
        FilterData normalized = current.normalizedFor(upgrade.getType());
        if (normalized != current) {
            PortItemStackExtension.setData(
                stack, SLDataComponents.FILTER_DATA.get(), normalized);
        }
        int inventorySlot = offhand ? Inventory.SLOT_OFFHAND : player.getInventory().selected;
        NetworkHooks.openScreen(player, new SimpleMenuProvider(
            (id, inv, p) -> new HandFilterMenu(id, inv, stack, inventorySlot),
            Component.translatable("gui.staticlogistics.hand_filter")
        ), buf -> {
            buf.writeVarInt(inventorySlot);
            buf.writeItem(stack);
        });
    }
}
