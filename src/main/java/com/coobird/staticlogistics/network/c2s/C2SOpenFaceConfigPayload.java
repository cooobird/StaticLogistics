package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.gui.menu.FaceConfiguratorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record C2SOpenFaceConfigPayload(BlockPos pos, Direction face) implements CustomPacketPayload {
    public static final Type<C2SOpenFaceConfigPayload> TYPE =
        new Type<>(Staticlogistics.asResource("open_face_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SOpenFaceConfigPayload> STREAM_CODEC =
        StreamCodec.composite(BlockPos.STREAM_CODEC, C2SOpenFaceConfigPayload::pos,
            Direction.STREAM_CODEC, C2SOpenFaceConfigPayload::face, C2SOpenFaceConfigPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final C2SOpenFaceConfigPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            if (!(sp.level() instanceof ServerLevel sl)) return;
            var title = sl.getBlockState(payload.pos()).getBlock().getName().copy();
            sp.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new FaceConfiguratorMenu(id, inv, payload.pos(), payload.face()), title),
                buf -> {
                    buf.writeBlockPos(payload.pos());
                    buf.writeEnum(payload.face());
                    buf.writeResourceLocation(TransferRegistries.ITEM.id());
                });
        });
    }
}
