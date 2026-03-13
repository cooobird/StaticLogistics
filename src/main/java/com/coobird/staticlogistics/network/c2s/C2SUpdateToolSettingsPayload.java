package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.common.init.SLDataComponents;
import com.coobird.staticlogistics.common.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.core.TransferType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record C2SUpdateToolSettingsPayload(
    int priority,
    String groupId,
    int mode,
    int transferType
) implements CustomPacketPayload {

    public static final Type<C2SUpdateToolSettingsPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Staticlogistics.MODID, "update_tool_settings"));

    public static final StreamCodec<FriendlyByteBuf, C2SUpdateToolSettingsPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, C2SUpdateToolSettingsPayload::priority,
        ByteBufCodecs.STRING_UTF8, C2SUpdateToolSettingsPayload::groupId,
        ByteBufCodecs.VAR_INT, C2SUpdateToolSettingsPayload::mode,
        ByteBufCodecs.VAR_INT, C2SUpdateToolSettingsPayload::transferType,
        C2SUpdateToolSettingsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final C2SUpdateToolSettingsPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            ItemStack stack = player.getMainHandItem();

            if (stack.getItem() instanceof LinkConfiguratorItem) {
                stack.set(SLDataComponents.PRIORITY.get(), payload.priority());
                stack.set(SLDataComponents.SELECTED_GROUP.get(), payload.groupId());
                stack.set(SLDataComponents.TOOL_MODE.get(), payload.mode());

                if (payload.transferType >= 0 && payload.transferType < TransferType.values().length) {
                    stack.set(SLDataComponents.SELECTED_TYPE.get(), TransferType.values()[payload.transferType]);
                }
            }
        });
    }
}