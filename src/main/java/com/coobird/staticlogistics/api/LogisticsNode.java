package com.coobird.staticlogistics.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/// 表示物流网络中的一个节点——某个世界的某个方块位置，面朝某个方向。
/// 节点身份始终保留维度、完整方块坐标和方向，不提供有损的单 long 编码。
public record LogisticsNode(GlobalPos gPos, Direction face, int channel) {
    /// 连接面身份，不是传输通道；即使没有通道，连接仍然存在。
    public static final int CONNECTION_FACE = -1;

    public boolean isConnectionFace() {
        return channel == CONNECTION_FACE;
    }

    public LogisticsNode connectionFace() {
        return new LogisticsNode(gPos, face, CONNECTION_FACE);
    }

    /// 通道使用非负整数身份；总量由实际存储和操作预算控制，不设玩法层硬上限。
    public static final int MAX_CHANNELS = Integer.MAX_VALUE;

    public LogisticsNode {
        if (channel < CONNECTION_FACE || channel >= MAX_CHANNELS) throw new IllegalArgumentException("Invalid channel");
    }

    public LogisticsNode(GlobalPos gPos, Direction face) {
        this(gPos, face, 0);
    }

    public static final Codec<LogisticsNode> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        GlobalPos.CODEC.fieldOf("pos").forGetter(LogisticsNode::gPos),
        Direction.CODEC.fieldOf("face").forGetter(LogisticsNode::face),
        Codec.intRange(CONNECTION_FACE, MAX_CHANNELS - 1).fieldOf("channel").forGetter(LogisticsNode::channel)
    ).apply(inst, LogisticsNode::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, LogisticsNode> STREAM_CODEC = StreamCodec.composite(
        GlobalPos.STREAM_CODEC, LogisticsNode::gPos,
        Direction.STREAM_CODEC, LogisticsNode::face,
        ByteBufCodecs.VAR_INT, LogisticsNode::channel,
        LogisticsNode::new
    );

    /// 判断这个节点是不是正好在某个维度、某个坐标、对着某个面
    public boolean isAt(ResourceKey<Level> dimension, BlockPos pos, Direction face) {
        return this.gPos.dimension().equals(dimension) &&
            this.gPos.pos().equals(pos) &&
            this.face == face;
    }

    /// 判断两个节点在不在同一个维度
    public boolean isInSameDimension(LogisticsNode other) {
        return this.gPos.dimension().equals(other.gPos.dimension());
    }

    /// 判断这个节点在不在指定的维度里
    public boolean isInSameDimension(ResourceKey<Level> dimension) {
        return this.gPos.dimension().equals(dimension);
    }

}
