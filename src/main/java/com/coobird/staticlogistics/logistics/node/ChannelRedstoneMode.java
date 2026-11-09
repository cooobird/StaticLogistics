package com.coobird.staticlogistics.logistics.node;

/// 红石条件独立于通道的启用状态。
public enum ChannelRedstoneMode {
    IGNORE, HIGH, LOW;

    public boolean allows(boolean powered) {
        return this == IGNORE || (this == HIGH) == powered;
    }

    public ChannelRedstoneMode next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
