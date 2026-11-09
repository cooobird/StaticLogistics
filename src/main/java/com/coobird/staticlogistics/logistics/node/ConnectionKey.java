package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.network.BoundedNetworkCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.util.Comparator;

/// 分组内一对容器的稳定连接身份；连接面、通道配置和传输方向都不参与身份。
public record ConnectionKey(GroupKey groupKey, GlobalPos first, GlobalPos second) {
    public static final Comparator<LogisticsNode> NODE_ORDER = Comparator
        .comparing((LogisticsNode node) -> node.gPos().dimension().location().toString())
        .thenComparingInt(node -> node.gPos().pos().getX())
        .thenComparingInt(node -> node.gPos().pos().getY())
        .thenComparingInt(node -> node.gPos().pos().getZ())
        .thenComparingInt(node -> node.face().ordinal())
        .thenComparingInt(LogisticsNode::channel);

    public static final Codec<ConnectionKey> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        GroupKey.CODEC.fieldOf("group").forGetter(ConnectionKey::groupKey),
        GlobalPos.CODEC.fieldOf("first").forGetter(ConnectionKey::first),
        GlobalPos.CODEC.fieldOf("second").forGetter(ConnectionKey::second)
    ).apply(instance, ConnectionKey::new));

    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, ConnectionKey> STREAM_CODEC =
        PortStreamCodec.composite(
            GroupKey.STREAM_CODEC, ConnectionKey::groupKey,
            BoundedNetworkCodecs.GLOBAL_POS, ConnectionKey::first,
            BoundedNetworkCodecs.GLOBAL_POS, ConnectionKey::second,
            ConnectionKey::new
        );

    public ConnectionKey {
        if (first.equals(second)) {
            throw new IllegalArgumentException("A connection cannot target the same endpoint");
        }
        if (compareContainers(first, second) > 0) {
            var swap = first;
            first = second;
            second = swap;
        }
    }

    public ConnectionKey(GroupKey groupKey, LogisticsNode first, LogisticsNode second) {
        this(groupKey, first.gPos(), second.gPos());
    }

    public boolean contains(GlobalPos container) {
        return first.equals(container) || second.equals(container);
    }

    public static int compareContainers(GlobalPos a, GlobalPos b) {
        int dimension = a.dimension().location().compareTo(b.dimension().location());
        return dimension != 0 ? dimension : a.pos().compareTo(b.pos());
    }
}
