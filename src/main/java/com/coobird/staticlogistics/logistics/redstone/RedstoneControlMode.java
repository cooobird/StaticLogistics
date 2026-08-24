package com.coobird.staticlogistics.logistics.redstone;

import com.mojang.serialization.Codec;

/**
 * 红石控制方案的电平条件。
 */
public enum RedstoneControlMode {
    HIGH("high"),
    LOW("low");

    public static final Codec<RedstoneControlMode> CODEC = Codec.STRING.xmap(
        RedstoneControlMode::fromSerializedName,
        RedstoneControlMode::serializedName);

    private final String serializedName;

    RedstoneControlMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public boolean allows(boolean powered) {
        return this == HIGH ? powered : !powered;
    }

    private static RedstoneControlMode fromSerializedName(String name) {
        for (RedstoneControlMode mode : values()) {
            if (mode.serializedName.equals(name)) return mode;
        }
        return HIGH;
    }
}
