package com.coobird.staticlogistics.api.group;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * 物流分组的稳定内部身份，不随显示名称变化。
 */
public record GroupKey(UUID ownerId, UUID internalId) {
    public static final UUID LEGACY_UNOWNED = new UUID(0L, 0L);
    public static final Codec<GroupKey> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf("owner").forGetter(GroupKey::ownerId),
        UUIDUtil.CODEC.fieldOf("id").forGetter(GroupKey::internalId)
    ).apply(instance, GroupKey::new));
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, GroupKey> STREAM_CODEC =
        new PortStreamCodec<>() {
            @Override
            public GroupKey decode(PortRegistryFriendlyByteBuf buffer) {
                return new GroupKey(buffer.readUUID(), buffer.readUUID());
            }

            @Override
            public void encode(PortRegistryFriendlyByteBuf buffer, GroupKey key) {
                buffer.writeUUID(key.ownerId());
                buffer.writeUUID(key.internalId());
            }
        };

    public GroupKey {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(internalId, "internalId");
    }

    public static GroupKey create(UUID ownerId) {
        return new GroupKey(Objects.requireNonNull(ownerId, "ownerId"), UUID.randomUUID());
    }

    public static GroupKey migrated(UUID ownerId, String legacyDisplayName) {
        UUID normalizedOwner = ownerId == null ? LEGACY_UNOWNED : ownerId;
        String normalizedName = legacyDisplayName == null ? "" : legacyDisplayName;
        byte[] seed = (normalizedOwner + "\0" + normalizedName).getBytes(StandardCharsets.UTF_8);
        return new GroupKey(normalizedOwner, UUID.nameUUIDFromBytes(seed));
    }

    /**
     * 更换所有者时保留分组内部身份。
     */
    public GroupKey withOwner(UUID newOwnerId) {
        return new GroupKey(Objects.requireNonNull(newOwnerId, "newOwnerId"), internalId);
    }

    public boolean isLegacyUnowned() {
        return LEGACY_UNOWNED.equals(ownerId);
    }
}
