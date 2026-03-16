package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.compat.ModIds;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.neoforged.fml.ModList;

import java.util.Arrays;
import java.util.List;

public enum TransferType implements StringRepresentable {
    ITEM("item", 0xFFFFFFFF, 0),
    FLUID("fluid", 0xFF3366FF, 1),
    ENERGY("energy", 0xFFFFFF00, 2),
    MEK_CHEMICALS("mek_chemicals", 0xFFFF66FF, 3),
    ARS_SOURCE("ars_source", 0xFF8000FF, 4);

    private final String name;
    private final int color;
    private final int bitOffset;

    private static final List<TransferType> AVAILABLE_TYPES = Arrays.stream(values())
        .filter(TransferType::checkAvailability)
        .toList();

    TransferType(String name, int color, int bitOffset) {
        this.name = name;
        this.color = color;
        this.bitOffset = bitOffset;
    }

    public int getColor() {
        return color;
    }

    public int getFlag() {
        return 1 << bitOffset;
    }

    public boolean isAvailable() {
        return checkAvailability(this);
    }

    public static List<TransferType> getAvailableTypes() {
        return AVAILABLE_TYPES;
    }

    private static boolean checkAvailability(TransferType type) {
        return switch (type) {
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
        index -> {
            if (index < 0 || index >= values().length) return ITEM;
            return values()[index];
        },
        TransferType::ordinal
    );
}