package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.item.BlueprintModeHandler;
import com.coobird.staticlogistics.content.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.content.item.ToolMode;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import com.coobird.staticlogistics.network.s2c.S2CToolModeResultPayload;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/// 模式请求绑定槽位并明确确认结果，限流拒绝也不能静默丢弃。
public record C2SUpdateToolModePayload(int slot, int mode, long requestId, UUID toolId) implements CustomPacketPayload {
    public static final Type<C2SUpdateToolModePayload> TYPE = new Type<>(StaticLogistics.asResource("update_tool_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SUpdateToolModePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, C2SUpdateToolModePayload::slot,
            ByteBufCodecs.VAR_INT, C2SUpdateToolModePayload::mode,
            ByteBufCodecs.VAR_LONG, C2SUpdateToolModePayload::requestId,
            UUIDUtil.STREAM_CODEC, C2SUpdateToolModePayload::toolId,
            C2SUpdateToolModePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SUpdateToolModePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            boolean allowed = ServerPacketRateLimiter.allow(player, ServerPacketRateLimiter.Action.TOOL_SETTINGS);
            boolean held = payload.slot() == player.getInventory().selected || payload.slot() == 40;
            ItemStack stack = held ? player.getInventory().getItem(payload.slot()) : ItemStack.EMPTY;
            boolean validTool = stack.getItem() instanceof LinkConfiguratorItem;
            ToolMode requested = ToolMode.fromId(payload.mode());
            boolean accepted = allowed && validTool && requested.getId() == payload.mode()
                && ((LinkConfiguratorItem) stack.getItem()).matchesSession(stack, payload.toolId());
            if (accepted) {
                BlueprintModeHandler.cancel(stack);
                stack.set(SLDataComponents.TOOL_MODE.get(), requested.getId());
            }
            int actual = validTool
                ? ToolMode.fromId(stack.getOrDefault(SLDataComponents.TOOL_MODE.get(), 0)).getId() : -1;
            /// -2 直接按玩家物品栏索引同步，不受当前容器界面影响。
            if (held) player.connection.send(new ClientboundContainerSetSlotPacket(
                -2, 0, payload.slot(), stack.copy()));
            PacketDistributor.sendToPlayer(player, new S2CToolModeResultPayload(payload.slot(), payload.requestId(), actual, accepted));
        });
    }
}
