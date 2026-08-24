package com.coobird.staticlogistics.logistics.redstone;

import com.mojang.serialization.Codec;

/**
 * 红石检测点控制连接运行的模式。
 */
public enum RedstoneControlMode {
    HIGH,
    LOW;

    public static final Codec<RedstoneControlMode> CODEC = Codec.STRING.xmap(
        name -> {
            try {
                return valueOf(name.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return HIGH;
            }
        }, mode -> mode.name().toLowerCase(java.util.Locale.ROOT));

    public boolean allows(boolean powered) {
        return this == HIGH ? powered : !powered;
    }
}
