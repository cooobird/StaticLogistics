package com.coobird.staticlogistics.core;

import net.minecraft.util.StringRepresentable;

public enum ConnectionMode implements StringRepresentable {
    DISABLED("disabled"),
    INPUT("input"),
    OUTPUT("output"),
    BOTH("both");

    private final String name;

    ConnectionMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public boolean allowsInput() {
        return this == INPUT || this == BOTH;
    }

    public boolean allowsOutput() {
        return this == OUTPUT || this == BOTH;
    }

    public ConnectionMode next() {
        return values()[(this.ordinal() + 1) % values().length];
    }
}