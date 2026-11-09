package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/// 单维度内的位置、面、通道复合端点地址。
/// 方块位置本身已经占满 64 位，方向不能再安全塞进同一个 long。
/// 因此运行时仓储、缓存、版本和脏集合统一使用这个复合键。
public record EndpointAddress(long posLong, Direction face, int channel) {
    private static final char STORAGE_SEPARATOR = ':';

    public EndpointAddress {
        if (channel < LogisticsNode.CONNECTION_FACE || channel >= LogisticsNode.MAX_CHANNELS)
            throw new IllegalArgumentException("Invalid channel");
    }

    public EndpointAddress(long posLong, Direction face) {
        this(posLong, face, 0);
    }

    public static EndpointAddress of(BlockPos pos, Direction face) {
        return new EndpointAddress(pos.asLong(), face);
    }

    public static EndpointAddress of(LogisticsNode node) {
        return new EndpointAddress(node.gPos().pos().asLong(), node.face(), node.channel());
    }

    public BlockPos pos() {
        return BlockPos.of(posLong);
    }

    public LogisticsNode toNode(ResourceKey<Level> dimension) {
        return new LogisticsNode(GlobalPos.of(dimension, pos()), face, channel);
    }

    /// 存档键分别保存完整位置 long、方向编号与通道编号。
    public String storageKey() {
        return posLong + String.valueOf(STORAGE_SEPARATOR) + face.get3DDataValue() + STORAGE_SEPARATOR + channel;
    }

    /// 读取完整的位置、面、通道三段键；不接受旧有损格式。
    public static EndpointAddress parseStorageKey(String value) {
        String[] parts = value.split(":");
        if (parts.length != 3) throw new IllegalArgumentException("Unsupported channel address format");
        int faceId = Integer.parseInt(parts[1]);
        if (faceId < 0 || faceId >= Direction.values().length) throw new IllegalArgumentException("Invalid direction");
        return new EndpointAddress(Long.parseLong(parts[0]), Direction.from3DDataValue(faceId), Integer.parseInt(parts[2]));
    }
}
