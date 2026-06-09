package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.storage.link.LinkManager;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

public record C2SRemoveLinkPayload(LogisticsNode node) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("remove_link");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SRemoveLinkPayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public C2SRemoveLinkPayload decode(PortRegistryFriendlyByteBuf buffer) {
            FriendlyByteBuf fbuf = buffer;
            LogisticsNode node = LogisticsNode.STREAM_CODEC.decode(buffer);
            return new C2SRemoveLinkPayload(node);
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buffer, C2SRemoveLinkPayload value) {
            LogisticsNode.STREAM_CODEC.encode(buffer, value.node());
        }
    };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        var server = player.getServer();
        if (server == null) return;
        ServerLevel targetLevel = server.getLevel(node.gPos().dimension());
        if (targetLevel == null) return;
        LinkManager manager = LinkManager.get(targetLevel);
        long key = node.toKey();
        FaceConfigComposite config = manager.getFaceConfig(key);
        if (config != null) {
            if (!config.canPlayerModify(player)) return;
            manager.removeFaceConfig(key);
        }
    }
}
