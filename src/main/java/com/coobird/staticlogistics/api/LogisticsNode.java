package com.coobird.staticlogistics.api;

import com.coobird.staticlogistics.util.LogisticsConstants;
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
 * <p>
 * Key 编码使用 {@link LogisticsConstants.Storage#FACE_BITS} 和 {@link LogisticsConstants.Storage#FACE_MASK}。
 * 统一使用本类的 posToKey / keyToPos / keyToFace / toKey / fromKey，禁止外部硬编码位移。
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

    // ---- Key 编解码（唯一实现，其他类禁止硬编码） ----

    /**
     * 坐标+面 → long key。外部优先用这个方法，不要自己拼位移。
     */
    public static long posToKey(BlockPos pos, Direction face) {
        return (pos.asLong() << LogisticsConstants.Storage.FACE_BITS)
            | (face.get3DDataValue() & LogisticsConstants.Storage.FACE_MASK);
    }

    /**
     * long key → BlockPos
     */
    public static BlockPos keyToPos(long key) {
        return BlockPos.of(key >> LogisticsConstants.Storage.FACE_BITS);
    }

    /**
     * long key → Direction
     */
    public static Direction keyToFace(long key) {
        return Direction.from3DDataValue((int) (key & LogisticsConstants.Storage.FACE_MASK));
    }

    /**
     * 把节点的位置和朝向编码成一个 long 数字，方便当 Map 的 key
     */
    public long toKey() {
        return posToKey(gPos.pos(), face);
    }

    /**
     * 从编码的 long key 反解出 LogisticsNode
     */
    public static LogisticsNode fromKey(long key, ResourceKey<Level> dim) {
        return new LogisticsNode(GlobalPos.of(dim, keyToPos(key)), keyToFace(key));
    }
}