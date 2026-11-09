package com.coobird.staticlogistics.network;

import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.transfer.TransferTypeSelection;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/// 客户端可控字符串与集合的统一网络边界。
public final class BoundedNetworkCodecs {
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, ResourceLocation> RESOURCE_LOCATION =
        PortStreamCodec.ofMember((value, buffer) -> buffer.writeResourceLocation(value), buffer -> buffer.readResourceLocation());
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, GlobalPos> GLOBAL_POS =
        PortStreamCodec.ofMember((value, buffer) -> buffer.writeGlobalPos(value), buffer -> buffer.readGlobalPos());
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, BlockPos> BLOCK_POS =
        PortStreamCodec.ofMember((value, buffer) -> buffer.writeBlockPos(value), buffer -> buffer.readBlockPos());
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, Direction> DIRECTION =
        PortStreamCodec.ofMember((value, buffer) -> buffer.writeEnum(value), buffer -> buffer.readEnum(Direction.class));
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, UUID> UUID =
        PortStreamCodec.ofMember((value, buffer) -> buffer.writeUUID(value), buffer -> buffer.readUUID());
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, String> GROUP_NAME = new PortStreamCodec<>() {
        @Override
        public String decode(PortRegistryFriendlyByteBuf buffer) {
            return buffer.readUtf(GroupConstraints.MAX_NAME_LENGTH);
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buffer, String value) {
            if (value == null || value.length() > GroupConstraints.MAX_NAME_LENGTH) {
                throw new EncoderException("Invalid group name length");
            }
            buffer.writeUtf(value, GroupConstraints.MAX_NAME_LENGTH);
        }
    };

    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, List<ResourceLocation>> TRANSFER_TYPE_IDS = new PortStreamCodec<>() {
        @Override
        public List<ResourceLocation> decode(PortRegistryFriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            if (size < 0 || size > TransferTypeSelection.MAX_SELECTED_TYPES) {
                throw new DecoderException("Invalid transfer type count: " + size);
            }
            List<ResourceLocation> ids = new ArrayList<>(size);
            HashSet<ResourceLocation> unique = new HashSet<>();
            for (int index = 0; index < size; index++) {
                ResourceLocation id = buffer.readResourceLocation();
                if (!unique.add(id)) throw new DecoderException("Duplicate transfer type id: " + id);
                ids.add(id);
            }
            return List.copyOf(ids);
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buffer, List<ResourceLocation> ids) {
            if (ids == null || ids.size() > TransferTypeSelection.MAX_SELECTED_TYPES
                || new HashSet<>(ids).size() != ids.size()) {
                throw new EncoderException("Invalid transfer type list");
            }
            buffer.writeVarInt(ids.size());
            ids.forEach(buffer::writeResourceLocation);
        }
    };

    private BoundedNetworkCodecs() {
    }
}
