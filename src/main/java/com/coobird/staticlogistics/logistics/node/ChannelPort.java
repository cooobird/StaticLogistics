package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.Direction;

/// 不包含位置的连接面和通道，用于蓝图相对坐标。
public record ChannelPort(Direction face, int channel) {
    public static final Codec<ChannelPort> CODEC = Codec.STRING.comapFlatMap(value -> {
        try {
            String[] parts = value.split(":");
            if (parts.length != 2) throw new IllegalArgumentException("Invalid channel port");
            return DataResult.success(new ChannelPort(Direction.valueOf(parts[0]), Integer.parseInt(parts[1])));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }, port -> port.face.name() + ":" + port.channel);

    public ChannelPort {
        if (channel < LogisticsNode.CONNECTION_FACE || channel >= LogisticsNode.MAX_CHANNELS)
            throw new IllegalArgumentException("Invalid channel");
    }
}
