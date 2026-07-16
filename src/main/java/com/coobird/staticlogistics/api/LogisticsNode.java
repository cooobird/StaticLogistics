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

/**
 * 表示物流网络中的一个节点——某个世界的某个方块位置，面朝某个方向。
 * <p>节点身份始终保留维度、完整方块坐标和方向，不提供有损的单 {@code long} 编码。
 */
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

    /**
     * 判断这个节点是不是正好在某个维度、某个坐标、对着某个面
     */
    public boolean isAt(ResourceKey<Level> dimension, BlockPos pos, Direction face) {
        return this.gPos.dimension().equals(dimension) &&
            this.gPos.pos().equals(pos) &&
            this.face == face;
    }

    /**
     * 判断两个节点在不在同一个维度
     */
    public boolean isInSameDimension(LogisticsNode other) {
        return this.gPos.dimension().equals(other.gPos.dimension());
    }

    /**
     * 判断这个节点在不在指定的维度里
     */
    public boolean isInSameDimension(ResourceKey<Level> dimension) {
        return this.gPos.dimension().equals(dimension);
    }

}
