package com.coobird.staticlogistics.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record LogisticsNode(GlobalPos gPos, Direction face) {

    public static final Codec<LogisticsNode> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        GlobalPos.CODEC.fieldOf("pos").forGetter(LogisticsNode::gPos),
        Direction.CODEC.fieldOf("face").forGetter(LogisticsNode::face)
    ).apply(inst, LogisticsNode::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, LogisticsNode> STREAM_CODEC = StreamCodec.composite(
        GlobalPos.STREAM_CODEC, LogisticsNode::gPos,
        Direction.STREAM_CODEC, LogisticsNode::face,
        LogisticsNode::new
    );

    public boolean isAt(ResourceKey<Level> dimension, BlockPos pos, Direction face) {
        return this.gPos.dimension().equals(dimension) &&
            this.gPos.pos().equals(pos) &&
            this.face == face;
    }

    public long toKey() {
        return (gPos.pos().asLong() << 3) | (long) face.get3DDataValue();
    }

    public static LogisticsNode fromKey(long key, ResourceKey<Level> dim) {
        BlockPos pos = BlockPos.of(key >> 3);
        Direction face = Direction.from3DDataValue((int) (key & 0x7));
        return new LogisticsNode(GlobalPos.of(dim, pos), face);
    }
}