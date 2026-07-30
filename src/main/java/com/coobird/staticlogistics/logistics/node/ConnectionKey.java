package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Comparator;
import java.util.Objects;

/**
 * 分组内一条连接的稳定身份。
 *
 * <p>连接本身仍由两个面配置中的互惠边保存；本类型只统一无方向的连接身份，
 * 供连接名称、网络命令和客户端去重共同使用。
 */
public record ConnectionKey(GroupKey groupKey, LogisticsNode first, LogisticsNode second) {
    public static final Comparator<LogisticsNode> NODE_ORDER = Comparator
        .comparing((LogisticsNode node) -> node.gPos().dimension().location().toString())
        .thenComparingInt(node -> node.gPos().pos().getX())
        .thenComparingInt(node -> node.gPos().pos().getY())
        .thenComparingInt(node -> node.gPos().pos().getZ())
        .thenComparingInt(node -> node.face().ordinal());

    public static final Codec<ConnectionKey> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        GroupKey.CODEC.fieldOf("group").forGetter(ConnectionKey::groupKey),
        LogisticsNode.CODEC.fieldOf("first").forGetter(ConnectionKey::first),
        LogisticsNode.CODEC.fieldOf("second").forGetter(ConnectionKey::second)
    ).apply(instance, ConnectionKey::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConnectionKey> STREAM_CODEC =
        StreamCodec.composite(
            GroupKey.STREAM_CODEC, ConnectionKey::groupKey,
            LogisticsNode.STREAM_CODEC, ConnectionKey::first,
            LogisticsNode.STREAM_CODEC, ConnectionKey::second,
            ConnectionKey::new
        );

    public ConnectionKey {
        Objects.requireNonNull(groupKey, "Connection group must not be null");
        Objects.requireNonNull(first, "First connection endpoint must not be null");
        Objects.requireNonNull(second, "Second connection endpoint must not be null");
        if (first.equals(second)) {
            throw new IllegalArgumentException("A connection cannot target the same endpoint");
        }
        if (NODE_ORDER.compare(first, second) > 0) {
            LogisticsNode swap = first;
            first = second;
            second = swap;
        }
    }
}
