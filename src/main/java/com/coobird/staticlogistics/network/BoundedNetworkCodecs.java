package com.coobird.staticlogistics.network;

import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.transfer.TransferTypeSelection;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** 客户端可控字符串与集合的统一网络边界。 */
public final class BoundedNetworkCodecs {
    public static final StreamCodec<RegistryFriendlyByteBuf, String> GROUP_NAME = new StreamCodec<>() {
        @Override
        public String decode(RegistryFriendlyByteBuf buffer) {
            return buffer.readUtf(GroupConstraints.MAX_NAME_LENGTH);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, String value) {
            if (value == null || value.length() > GroupConstraints.MAX_NAME_LENGTH) {
                throw new EncoderException("Invalid group name length");
            }
            buffer.writeUtf(value, GroupConstraints.MAX_NAME_LENGTH);
        }
    };

    public static final StreamCodec<RegistryFriendlyByteBuf, List<ResourceLocation>> TRANSFER_TYPE_IDS = new StreamCodec<>() {
        @Override
        public List<ResourceLocation> decode(RegistryFriendlyByteBuf buffer) {
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
        public void encode(RegistryFriendlyByteBuf buffer, List<ResourceLocation> ids) {
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
