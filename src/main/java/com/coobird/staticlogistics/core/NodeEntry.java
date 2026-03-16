package com.coobird.staticlogistics.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record NodeEntry(GlobalPos pos, Direction face) {
    public static final Codec<NodeEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        GlobalPos.CODEC.fieldOf("pos").forGetter(NodeEntry::pos),
        Direction.CODEC.fieldOf("face").forGetter(NodeEntry::face)
    ).apply(inst, NodeEntry::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, NodeEntry> STREAM_CODEC = StreamCodec.composite(
        GlobalPos.STREAM_CODEC, NodeEntry::pos,
        Direction.STREAM_CODEC, NodeEntry::face,
        NodeEntry::new
    );
}