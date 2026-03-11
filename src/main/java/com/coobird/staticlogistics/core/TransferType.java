package com.coobird.staticlogistics.core;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

public enum TransferType implements StringRepresentable {
    ITEM("item", 0xFFFFFFFF),        // 白色
    FLUID("fluid", 0xFF3366FF);      // 蓝色
//    GAS("gas", 0xFFFF9900),          // 橙黄色
//    ENERGY("energy", 0xFF00FF00),     // 绿色
//    INFUSION("infusion", 0xFFFFCC00), // 明黄色
//    PIGMENT("pigment", 0xFFFF99CC);   // 粉红色

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