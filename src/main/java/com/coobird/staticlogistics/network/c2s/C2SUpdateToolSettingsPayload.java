package com.coobird.staticlogistics.network.c2s;

import PortLib.extensions.net.minecraft.world.item.ItemStack.PortItemStackExtension;
import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.item.BlueprintItem;
import com.coobird.staticlogistics.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.logic.ToolMode;
import com.coobird.staticlogistics.registry.SLDataComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.util.List;

public record C2SUpdateToolSettingsPayload(String groupId, int mode,
                                           int typeMask) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("update_tool_settings");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SUpdateToolSettingsPayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public C2SUpdateToolSettingsPayload decode(PortRegistryFriendlyByteBuf buffer) {
            FriendlyByteBuf fbuf = buffer;
            return new C2SUpdateToolSettingsPayload(fbuf.readUtf(), fbuf.readVarInt(), fbuf.readVarInt());
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buffer, C2SUpdateToolSettingsPayload value) {
            FriendlyByteBuf fbuf = buffer;
            fbuf.writeUtf(value.groupId());
            fbuf.writeVarInt(value.mode());
            fbuf.writeVarInt(value.typeMask());
        }
    };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!(stack.getItem() instanceof LinkConfiguratorItem) && !(stack.getItem() instanceof BlueprintItem)) {
            stack = player.getItemInHand(InteractionHand.OFF_HAND);
            if (!(stack.getItem() instanceof LinkConfiguratorItem) && !(stack.getItem() instanceof BlueprintItem))
                return;
        }
        String rawId = groupId.trim();
        // 去除控制字符，保留中英文、数字、空格、下划线、连字符
        String safeId = rawId.replaceAll("[^\\p{L}\\p{N}_\\- ]", "");
        String finalId = safeId.isEmpty() ? "" : safeId.substring(0, Math.min(safeId.length(), 32));
        PortItemStackExtension.setData(stack, SLDataComponents.SELECTED_GROUP, finalId);
        int finalMask = typeMask;
        PortItemStackExtension.setData(stack, SLDataComponents.SELECTED_TYPES_MASK, finalMask);
        int vMode = Mth.clamp(mode, 0, ToolMode.values().length - 1);
        int currentMode = PortItemStackExtension.getDataOrDefault(stack, SLDataComponents.TOOL_MODE, 0);
        if (currentMode != vMode) {
            PortItemStackExtension.setData(stack, SLDataComponents.TOOL_MODE, vMode);
            var nodes = PortItemStackExtension.getDataOrDefault(stack, SLDataComponents.STORED_NODES, List.of());
            player.displayClientMessage(Component.translatable(
                nodes.isEmpty() ? "msg.staticlogistics.mode_switched" : "msg.staticlogistics.mode_switched_with_nodes",
                ToolMode.values()[vMode].getDisplayName(),
                nodes.size()
            ), true);
        }
    }
}
