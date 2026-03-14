package com.coobird.staticlogistics.core;

import com.coobird.staticlogistics.SLConfig;
import com.coobird.staticlogistics.transfer.TransferType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.UUID;

public record StaticLink(
    BlockPos sourcePos,
    Direction sourceFace,
    ResourceKey<Level> sourceDimension,
    BlockPos destPos,
    Direction destFace,
    ResourceKey<Level> destDimension,
    int transferFlags,
    int priority,
    UUID owner,
    String ownerName,
    String groupId,
    int maxRange,
    boolean allowCrossDim
) {
    public static final Codec<StaticLink> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            BlockPos.CODEC.fieldOf("src").forGetter(StaticLink::sourcePos),
            Direction.CODEC.fieldOf("src_f").forGetter(StaticLink::sourceFace),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("src_dim").forGetter(StaticLink::sourceDimension),
            BlockPos.CODEC.fieldOf("dst").forGetter(StaticLink::destPos),
            Direction.CODEC.fieldOf("dst_f").forGetter(StaticLink::destFace),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dst_dim").forGetter(StaticLink::destDimension),
            Codec.INT.fieldOf("flags").forGetter(StaticLink::transferFlags),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(StaticLink::priority),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(StaticLink::owner),
            Codec.STRING.optionalFieldOf("owner_name", "Unknown").forGetter(StaticLink::ownerName),
            Codec.STRING.optionalFieldOf("group", "1").forGetter(StaticLink::groupId),
            Codec.INT.optionalFieldOf("range", 1).forGetter(StaticLink::maxRange),
            Codec.BOOL.optionalFieldOf("cross_dim", false).forGetter(StaticLink::allowCrossDim)
        ).apply(instance, StaticLink::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, StaticLink> STREAM_CODEC =
        ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public boolean hasType(TransferType type) {
        return (transferFlags & (1 << type.ordinal())) != 0;
    }

    public boolean canTransfer(Level currentLevel, FaceConfig config) {
        if (config.isDimensionEffective()) return true;
        if (isCrossDim(currentLevel.dimension())) return false;

        int multiplier = config.getMaxRangeMultiplier();
        if (multiplier >= 1000000) return true;

        int baseRadius = SLConfig.getDefaultRadius();
        long effectiveMaxRange = (long) baseRadius * multiplier;

        double distSq = sourcePos.distSqr(destPos);
        return distSq <= (double) effectiveMaxRange * effectiveMaxRange;
    }

    public boolean isCrossDim(ResourceKey<Level> currentDim) {
        return !this.destDimension.equals(this.sourceDimension);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StaticLink that)) return false;
        return sourcePos.equals(that.sourcePos) &&
            sourceFace == that.sourceFace &&
            sourceDimension.equals(that.sourceDimension) &&
            destPos.equals(that.destPos) &&
            destFace == that.destFace &&
            destDimension.equals(that.destDimension) &&
            owner.equals(that.owner) &&
            groupId.equals(that.groupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourcePos, sourceFace, sourceDimension, destPos, destFace, destDimension, owner, groupId);
    }
}