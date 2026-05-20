package com.coobird.staticlogistics.api.type;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.HashMap;
import java.util.Map;

/**
 * 提取模式——控制从源容器取物品时按什么顺序选槽位
 */
public enum ExtractionMode implements StringRepresentable {
    SEQUENTIAL("sequential"),       // 顺序提取：从第一个可用槽位开始
    SLOT_ROUND_ROBIN("slot_round_robin"); // 槽位轮询：每个槽位轮流提取

    private static final Map<String, ExtractionMode> NAME_CACHE = new HashMap<>();

    static {
        for (ExtractionMode mode : values()) {
            NAME_CACHE.put(mode.getSerializedName(), mode);
        }
    }

    private final String name;

    ExtractionMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public String getDescriptionId() {
        return "extraction.staticlogistics." + name;
    }

    public Component getDisplayName() {
        return Component.translatable(getDescriptionId());
    }

    public static ExtractionMode byName(String name, ExtractionMode fallback) {
        ExtractionMode mode = NAME_CACHE.get(name);
        return mode != null ? mode : fallback;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ExtractionMode> STREAM_CODEC =
        ByteBufCodecs.VAR_INT.<RegistryFriendlyByteBuf>cast().map(
            index -> ExtractionMode.values()[index],
            ExtractionMode::ordinal
        );
}
