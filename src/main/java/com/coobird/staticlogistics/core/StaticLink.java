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

import java.util.UUID;

public record StaticLink(
    UUID linkId,
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
    int tier,
    boolean allowCrossDim
) {
    public static final Codec<StaticLink> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(StaticLink::linkId),
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
            Codec.INT.optionalFieldOf("tier", 1).forGetter(StaticLink::tier),
            Codec.BOOL.optionalFieldOf("cross_dim", false).forGetter(StaticLink::allowCrossDim)
        ).apply(instance, StaticLink::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, StaticLink> STREAM_CODEC =
        ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public boolean hasType(TransferType type) {
        return (transferFlags & type.getFlag()) != 0;
    }

    public StaticLink withMergedFlags(int otherFlags) {
        return new StaticLink(linkId, sourcePos, sourceFace, sourceDimension, destPos, destFace, destDimension,
            this.transferFlags | otherFlags, priority, owner, ownerName, groupId, tier, allowCrossDim);
    }

    public boolean canTransfer(Level level, FaceConfig config) {
        boolean crossDim = isCrossDim();
        if (crossDim) {
            return this.allowCrossDim && config.isDimensionEffective();
        }

        if (!level.dimension().equals(this.sourceDimension)) return false;

        double maxRange = (double) SLConfig.getDefaultRadius() * config.getMaxRangeMultiplier();
        return sourcePos.distSqr(destPos) <= (maxRange * maxRange);
    }

    public boolean isCrossDim() {
        return !this.sourceDimension.equals(this.destDimension);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StaticLink that)) return false;
        return linkId.equals(that.linkId);
    }

    @Override
    public int hashCode() {
        return linkId.hashCode();
    }
}