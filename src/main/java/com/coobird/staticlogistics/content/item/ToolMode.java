package com.coobird.staticlogistics.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

import java.util.List;

public enum ToolMode implements StringRepresentable {
    WRENCH("wrench", ChatFormatting.YELLOW, 0),
    LINK_AS_INSERT("link_as_input", ChatFormatting.AQUA, 1),
    LINK_AS_EXTRACT("link_as_output", ChatFormatting.GOLD, 2),
    REMOVE("remove", ChatFormatting.RED, 3),
    BLUEPRINT("blueprint", ChatFormatting.LIGHT_PURPLE, 4);

    /// 滚轮只切换常规操作；蓝图通过页签选择并同步工具状态。
    public static final List<ToolMode> ORDER = List.of(WRENCH, LINK_AS_EXTRACT, LINK_AS_INSERT, REMOVE);

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

    public boolean isLinkMode() {
        return this == LINK_AS_INSERT || this == LINK_AS_EXTRACT;
    }
}
