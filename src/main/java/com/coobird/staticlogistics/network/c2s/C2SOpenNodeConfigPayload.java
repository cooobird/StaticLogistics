package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.gui.menu.NodeConfiguratorMenu;
import com.coobird.staticlogistics.storage.link.LinkManager;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record C2SOpenNodeConfigPayload(BlockPos pos, Direction face) implements CustomPacketPayload {
    public static final Type<C2SOpenNodeConfigPayload> TYPE =
        new Type<>(StaticLogistics.asResource("open_node_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SOpenNodeConfigPayload> STREAM_CODEC =
        StreamCodec.composite(BlockPos.STREAM_CODEC, C2SOpenNodeConfigPayload::pos,
            Direction.STREAM_CODEC, C2SOpenNodeConfigPayload::face, C2SOpenNodeConfigPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final C2SOpenNodeConfigPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            FaceConfigComposite config = LinkManager.get((net.minecraft.server.level.ServerLevel) sp.level())
                .getOrCreateFaceConfig(payload.pos(), payload.face());
            var title = sp.level().getBlockState(payload.pos()).getBlock().getName().copy();
            sp.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new NodeConfiguratorMenu(id, inv, payload.pos(), payload.face()), title),
                buf -> {
                    buf.writeBlockPos(payload.pos());
                    buf.writeEnum(payload.face());
                    NodeConfiguratorMenu.writeInitialTypeData(buf, StaticLogistics.asResource("item"), config);
                });
        });
    }
}
