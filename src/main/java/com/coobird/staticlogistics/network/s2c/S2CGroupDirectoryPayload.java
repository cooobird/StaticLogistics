package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.network.BoundedNetworkCodecs;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

/**
 * 原子替换某位所有者的完整分组目录，其中同时包含空组与活跃组。
 */
public record S2CGroupDirectoryPayload(UUID ownerId, Set<GroupRef> groups) implements CustomPacketPayload {

    public S2CGroupDirectoryPayload {
        Objects.requireNonNull(ownerId, "Group directory owner must not be null");
        groups = Set.copyOf(Objects.requireNonNull(groups, "Group directory must not be null"));
    }

    public static final Type<S2CGroupDirectoryPayload> TYPE =
        new Type<>(StaticLogistics.asResource("group_directory"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CGroupDirectoryPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public S2CGroupDirectoryPayload decode(RegistryFriendlyByteBuf buf) {
            UUID ownerId = buf.readUUID();
            int size = buf.readVarInt();
            if (size < 0 || size > GroupConstraints.MAX_GROUPS_PER_OWNER) {
                throw new DecoderException("Invalid group count: " + size);
            }
            Set<GroupRef> groups = new LinkedHashSet<>();
            Set<GroupKey> keys = new LinkedHashSet<>();
            Map<String, GroupKey> names = new HashMap<>();
            for (int i = 0; i < size; i++) {
                GroupKey key = GroupKey.STREAM_CODEC.decode(buf);
                String name = BoundedNetworkCodecs.GROUP_NAME.decode(buf);
                try {
                    if (!GroupConstraints.normalizeName(name).equals(name)) {
                        throw new DecoderException("Group name is not normalized");
                    }
                } catch (IllegalArgumentException exception) {
                    throw new DecoderException("Invalid group name", exception);
                }
                if (!key.ownerId().equals(ownerId)) {
                    throw new DecoderException("Group owner does not match payload owner");
                }
                if (!keys.add(key)) throw new DecoderException("Duplicate group key: " + key);
                if (names.putIfAbsent(name, key) != null) {
                    throw new DecoderException("Duplicate group name: " + name);
                }
                groups.add(new GroupRef(key, name));
            }
            return new S2CGroupDirectoryPayload(ownerId, groups);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, S2CGroupDirectoryPayload packet) {
            validate(packet);
            buf.writeUUID(packet.ownerId());
            buf.writeVarInt(packet.groups().size());
            for (GroupRef group : packet.groups()) {
                GroupKey.STREAM_CODEC.encode(buf, group.key());
                BoundedNetworkCodecs.GROUP_NAME.encode(buf, group.displayName());
            }
        }

        private static void validate(S2CGroupDirectoryPayload packet) {
            if (packet.ownerId() == null || packet.groups() == null
                || packet.groups().size() > GroupConstraints.MAX_GROUPS_PER_OWNER) {
                throw new EncoderException("Invalid group directory payload");
            }
            Set<GroupKey> keys = new LinkedHashSet<>();
            Set<String> names = new LinkedHashSet<>();
            for (GroupRef group : packet.groups()) {
                if (group == null || !packet.ownerId().equals(group.key().ownerId())
                    || !keys.add(group.key()) || !names.add(group.displayName())) {
                    throw new EncoderException("Invalid group directory entry");
                }
                try {
                    if (!GroupConstraints.normalizeName(group.displayName()).equals(group.displayName())) {
                        throw new EncoderException("Group name is not normalized");
                    }
                } catch (IllegalArgumentException exception) {
                    throw new EncoderException("Invalid group name", exception);
                }
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CGroupDirectoryPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientLinkData.INSTANCE.replaceGroupDirectory(packet.ownerId(), packet.groups());
        });
    }
}
