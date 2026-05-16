package com.coobird.staticlogistics.item.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

public enum ToolMode implements StringRepresentable {
    WRENCH("wrench", ChatFormatting.YELLOW, 0),
    LINK_AS_INSERT("link_as_input", ChatFormatting.AQUA, 1),
    LINK_AS_EXTRACT("link_as_output", ChatFormatting.GOLD, 2),
    REMOVE("remove", ChatFormatting.RED, 3),
    FACE_CONFIG("face_config", ChatFormatting.LIGHT_PURPLE, 4),
    CONTAINER_CONFIG("container_config", ChatFormatting.GREEN, 5);

    private final String name;
    private final ChatFormatting color;
    private final int id;

    ToolMode(String name, ChatFormatting color, int id) {
        this.name = name;
        this.color = color;
        this.id = id;
    }

    public Component getDisplayName() {
        return Component.translatable("mode.staticlogistics." + name).withStyle(color);
    }

    public int getId() {
        return id;
    }

    public static ToolMode fromId(int id) {
        for (ToolMode mode : values()) {
            if (mode.id == id) return mode;
        }
        return LINK_AS_INSERT;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public ToolMode next() {
        return values()[(this.ordinal() + 1) % values().length];
    }

    public ToolMode previous() {
        return values()[(this.ordinal() - 1 + values().length) % values().length];
    }

    public boolean isLinkMode() {
        return this == LINK_AS_INSERT || this == LINK_AS_EXTRACT;
    }
}