package com.coobird.staticlogistics.transfer;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

public enum TransferType implements StringRepresentable {
    ITEM("item", 0xFFFFFFFF),
    FLUID("fluid", 0xFF3366FF),
    ENERGY("energy", 0xFFFFFF00),
    CHEMICALS("chemicals", 0xFFFF66FF);

    private final String name;
    private final int color;

    TransferType(String name, int color) {
        this.name = name;
        this.color = color;
    }

    public int getColor() {
        return color;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public static final Codec<TransferType> CODEC = StringRepresentable.fromEnum(TransferType::values);

    public static final StreamCodec<ByteBuf, TransferType> STREAM_CODEC = ByteBufCodecs.idMapper(
        index -> TransferType.values()[index],
        TransferType::ordinal
    );
}