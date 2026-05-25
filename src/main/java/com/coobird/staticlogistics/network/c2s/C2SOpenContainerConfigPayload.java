package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.gui.menu.ContainerConfiguratorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record C2SOpenContainerConfigPayload(BlockPos pos, Direction face) implements CustomPacketPayload {
    public static final Type<C2SOpenContainerConfigPayload> TYPE =
        new Type<>(Staticlogistics.asResource("open_container_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SOpenContainerConfigPayload> STREAM_CODEC =
        StreamCodec.composite(BlockPos.STREAM_CODEC, C2SOpenContainerConfigPayload::pos,
            Direction.STREAM_CODEC, C2SOpenContainerConfigPayload::face, C2SOpenContainerConfigPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final C2SOpenContainerConfigPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            if (!(sp.level() instanceof ServerLevel sl)) return;
            var title = sl.getBlockState(payload.pos()).getBlock().getName().copy();
            sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new ContainerConfiguratorMenu(id, inv, payload.pos(), payload.face()), title),
                buf -> {
                    buf.writeBlockPos(payload.pos());
                    buf.writeEnum(payload.face());
                });
        });
    }
}
