package com.coobird.staticlogistics.core;

import net.minecraft.util.StringRepresentable;

import java.util.HashMap;
import java.util.Map;

public enum ConnectionMode implements StringRepresentable {
    DISABLED("disabled"),
    INPUT("input"),
    OUTPUT("output"),
    BOTH("both");

    private final String name;
    private static final Map<String, ConnectionMode> NAME_CACHE = new HashMap<>();

    static {
        for (ConnectionMode mode : values()) {
            NAME_CACHE.put(mode.name(), mode);
        }
    }

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

    public static ConnectionMode byName(String name, ConnectionMode fallback) {
        ConnectionMode mode = NAME_CACHE.get(name);
        return mode != null ? mode : fallback;
    }
}