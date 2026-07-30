package com.coobird.staticlogistics.api.group;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * 物流分组的稳定内部身份。
 */
public record GroupKey(UUID ownerId, UUID internalId) {
    public static final UUID LEGACY_UNOWNED = new UUID(0L, 0L);
    public static final Codec<GroupKey> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf("owner").forGetter(GroupKey::ownerId),
        UUIDUtil.CODEC.fieldOf("id").forGetter(GroupKey::internalId)
    ).apply(instance, GroupKey::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, GroupKey> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public GroupKey decode(RegistryFriendlyByteBuf buffer) {
            return new GroupKey(buffer.readUUID(), buffer.readUUID());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, GroupKey key) {
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
     * 更换所有者时保留分组内部身份，显示名称不参与身份计算。
     */
    public GroupKey withOwner(UUID newOwnerId) {
        return new GroupKey(Objects.requireNonNull(newOwnerId, "newOwnerId"), internalId);
    }

    public boolean isLegacyUnowned() {
        return LEGACY_UNOWNED.equals(ownerId);
    }
}
