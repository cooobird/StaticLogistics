package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.common.util.ModIds;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.neoforged.fml.ModList;

public enum TransferType implements StringRepresentable {
    ITEM("item", 0xFFFFFFFF),
    FLUID("fluid", 0xFF3366FF),
    ENERGY("energy", 0xFFFFFF00),
    MEK_CHEMICALS("mek_chemicals", 0xFFFF66FF),
    ARS_SOURCE("ars_source", 0xFF8000FF);

    private final String name;
    private final int color;

    TransferType(String name, int color) {
        this.name = name;
        this.color = color;
    }

    public int getColor() {
        return color;
    }

    public boolean isAvailable() {
        return switch (this) {
            case MEK_CHEMICALS -> ModList.get().isLoaded(ModIds.MEKANISM);
            case ARS_SOURCE -> ModList.get().isLoaded(ModIds.ARS_NOUVEAU);
            default -> true;
        };
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