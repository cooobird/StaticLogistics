package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public record S2CSyncEmptyGroupsPayload(UUID playerId, Set<String> emptyGroups) implements IPortPacket.S2C {
    public static final ResourceLocation ID = StaticLogistics.asResource("sync_empty_groups");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, S2CSyncEmptyGroupsPayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public S2CSyncEmptyGroupsPayload decode(PortRegistryFriendlyByteBuf buffer) {
            FriendlyByteBuf fbuf = buffer;
            UUID playerId = fbuf.readUUID();
            int size = fbuf.readVarInt();
            Set<String> emptyGroups = new HashSet<>(size);
            for (int i = 0; i < size; i++) {
                emptyGroups.add(fbuf.readUtf());
            }
            return new S2CSyncEmptyGroupsPayload(playerId, emptyGroups);
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buffer, S2CSyncEmptyGroupsPayload value) {
            FriendlyByteBuf fbuf = buffer;
            fbuf.writeUUID(value.playerId());
            fbuf.writeVarInt(value.emptyGroups().size());
            for (String group : value.emptyGroups()) {
                fbuf.writeUtf(group);
            }
        }
    };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(Player player) {
        net.minecraft.client.Minecraft.getInstance().execute(() -> ClientLinkData.INSTANCE.setEmptyGroups(playerId, emptyGroups));
    }
}
