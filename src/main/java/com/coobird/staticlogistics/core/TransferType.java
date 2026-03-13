package com.coobird.staticlogistics.core;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.Vec3;

public enum TransferType implements StringRepresentable {
    ITEM("item", 0xFFFFFFFF),
    FLUID("fluid", 0xFF3366FF),
    ENERGY("energy", 0xFFFF0000);

    private final String name;
    private final int color;

    TransferType(String name, int color) {
        this.name = name;
        this.color = color;
    }

    public Vec3 getVisualOffset(Direction face) {
        float offsetDistance = 0.08f;

        float factor = (this == ITEM) ? -1.0f : 1.0f;
        float dist = offsetDistance * factor;

        return switch (face.getAxis()) {
            case Y -> new Vec3(dist, 0, 0);
            case X -> new Vec3(0, dist, 0);
            case Z -> new Vec3(dist, 0, 0);
        };
    }

    public int getRenderColor(ConnectionMode mode) {
        return switch (mode) {
            case INPUT -> 0xFF3498DB;
            case OUTPUT -> 0xFFF1C40F;
            case BOTH -> 0xFF9B59B6;
            default -> this.color;
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