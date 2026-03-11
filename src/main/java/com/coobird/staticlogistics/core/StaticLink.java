package com.coobird.staticlogistics.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Objects;

public record StaticLink(
    BlockPos sourcePos,
    Direction sourceFace,
    BlockPos destPos,
    Direction destFace,
    int transferFlags,
    int priority,
    int groupId // 新增：分组ID
) {
    public static final Codec<StaticLink> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            BlockPos.CODEC.fieldOf("src").forGetter(StaticLink::sourcePos),
            Direction.CODEC.fieldOf("src_f").forGetter(StaticLink::sourceFace),
            BlockPos.CODEC.fieldOf("dst").forGetter(StaticLink::destPos),
            Direction.CODEC.fieldOf("dst_f").forGetter(StaticLink::destFace),
            Codec.INT.fieldOf("flags").forGetter(StaticLink::transferFlags),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(StaticLink::priority),
            Codec.INT.optionalFieldOf("group", 0).forGetter(StaticLink::groupId)
        ).apply(instance, StaticLink::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, StaticLink> STREAM_CODEC =
        ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public boolean hasType(TransferType type) {
        return (transferFlags & (1 << type.ordinal())) != 0;
    }

    public BlockPos getOtherPos(BlockPos current) {
        return current.equals(sourcePos) ? destPos : sourcePos;
    }

    public Direction getOtherFace(BlockPos current) {
        return current.equals(sourcePos) ? destFace : sourceFace;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StaticLink that)) return false;
        return groupId == that.groupId &&
            Objects.equals(sourcePos, that.sourcePos) && sourceFace == that.sourceFace &&
            Objects.equals(destPos, that.destPos) && destFace == that.destFace;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourcePos, sourceFace, destPos, destFace, groupId);
    }
}