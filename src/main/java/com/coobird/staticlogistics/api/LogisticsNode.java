package com.coobird.staticlogistics.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/**
 * 表示物流网络中的一个节点——某个世界的某个方块位置，面朝某个方向。
 */
public record LogisticsNode(GlobalPos gPos, Direction face) {

    public static final Codec<LogisticsNode> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        GlobalPos.CODEC.fieldOf("pos").forGetter(LogisticsNode::gPos),
        Direction.CODEC.fieldOf("face").forGetter(LogisticsNode::face)
    ).apply(inst, LogisticsNode::new));

    private static void writeGlobalPos(GlobalPos pos, PortRegistryFriendlyByteBuf buf) {
        buf.writeResourceKey(pos.dimension());
        buf.writeBlockPos(pos.pos());
    }

    private static GlobalPos readGlobalPos(PortRegistryFriendlyByteBuf buf) {
        ResourceKey<Level> dim = buf.readResourceKey(Registries.DIMENSION);
        BlockPos pos = buf.readBlockPos();
        return GlobalPos.of(dim, pos);
    }

    private static void writeDirection(Direction dir, PortRegistryFriendlyByteBuf buf) {
        buf.writeVarInt(dir.get3DDataValue());
    }

    private static Direction readDirection(PortRegistryFriendlyByteBuf buf) {
        return Direction.from3DDataValue(buf.readVarInt());
    }

    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, LogisticsNode> STREAM_CODEC = PortStreamCodec.composite(
        PortStreamCodec.ofMember(LogisticsNode::writeGlobalPos, LogisticsNode::readGlobalPos), LogisticsNode::gPos,
        PortStreamCodec.ofMember(LogisticsNode::writeDirection, LogisticsNode::readDirection), LogisticsNode::face,
        LogisticsNode::new
    );

    public boolean isAt(ResourceKey<Level> dimension, BlockPos pos, Direction face) {
        return this.gPos.dimension().equals(dimension) &&
            this.gPos.pos().equals(pos) &&
            this.face == face;
    }

    public boolean isInSameDimension(LogisticsNode other) {
        return this.gPos.dimension().equals(other.gPos.dimension());
    }

    public boolean isInSameDimension(ResourceKey<Level> dimension) {
        return this.gPos.dimension().equals(dimension);
    }

}
