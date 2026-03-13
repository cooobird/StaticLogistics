package com.coobird.staticlogistics.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;

public record StaticLink(
    BlockPos sourcePos,
    Direction sourceFace,
    BlockPos destPos,
    Direction destFace,
    ResourceKey<Level> destDimension,
    int transferFlags,
    int priority,
    String groupId,
    int maxRange,
    boolean allowCrossDim
) {
    public static final Codec<StaticLink> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            BlockPos.CODEC.fieldOf("src").forGetter(StaticLink::sourcePos),
            Direction.CODEC.fieldOf("src_f").forGetter(StaticLink::sourceFace),
            BlockPos.CODEC.fieldOf("dst").forGetter(StaticLink::destPos),
            Direction.CODEC.fieldOf("dst_f").forGetter(StaticLink::destFace),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dst_dim").forGetter(StaticLink::destDimension),
            Codec.INT.fieldOf("flags").forGetter(StaticLink::transferFlags),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(StaticLink::priority),
            Codec.STRING.optionalFieldOf("group", "default").forGetter(StaticLink::groupId),
            Codec.INT.optionalFieldOf("range", 8).forGetter(StaticLink::maxRange),
            Codec.BOOL.optionalFieldOf("cross_dim", false).forGetter(StaticLink::allowCrossDim)
        ).apply(instance, StaticLink::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, StaticLink> STREAM_CODEC =
        ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public boolean hasType(TransferType type) {
        return (transferFlags & (1 << type.ordinal())) != 0;
    }

    public boolean canTransfer(Level currentLevel) {
        if (!currentLevel.dimension().equals(destDimension)) {
            return allowCrossDim;
        }
        return sourcePos.distSqr(destPos) <= (double) maxRange * maxRange;
    }

    public boolean isCrossDim(ResourceKey<Level> currentDim) {
        return !this.destDimension.equals(currentDim);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StaticLink that)) return false;
        return Objects.equals(sourcePos, that.sourcePos) &&
            sourceFace == that.sourceFace &&
            Objects.equals(destPos, that.destPos) &&
            destFace == that.destFace &&
            Objects.equals(destDimension, that.destDimension) &&
            Objects.equals(groupId, that.groupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourcePos, sourceFace, destPos, destFace, destDimension, groupId);
    }
}