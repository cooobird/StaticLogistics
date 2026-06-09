package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.item.BlueprintItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

public record C2SBlueprintUndoPayload() implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("blueprint_undo");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SBlueprintUndoPayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public C2SBlueprintUndoPayload decode(PortRegistryFriendlyByteBuf buffer) {
            return new C2SBlueprintUndoPayload();
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buffer, C2SBlueprintUndoPayload value) {
        }
    };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level) {
            BlueprintItem.undoPaste(level, player);
        }
    }
}
