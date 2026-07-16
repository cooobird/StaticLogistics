package com.coobird.staticlogistics.logistics.util;

import com.coobird.staticlogistics.api.LogisticsNode;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Locale;

/**
 * 统一生成节点位置的可翻译文本，避免各界面自行拼接字段和原始 ID。
 */
public final class NodeDisplayText {
    private NodeDisplayText() {
    }

    public static MutableComponent dimension(ResourceKey<Level> dimension) {
        var id = dimension.location();
        return Component.translatableWithFallback(id.toLanguageKey("dimension"), id.toString());
    }

    public static MutableComponent direction(Direction direction) {
        return Component.translatable("gui.staticlogistics.direction." + direction.getName());
    }

    public static MutableComponent details(LogisticsNode node) {
        var pos = node.gPos().pos();
        return Component.translatable("gui.staticlogistics.node.details",
            Component.translatable("gui.staticlogistics.node.dimension"),
            dimension(node.gPos().dimension()),
            Component.translatable("gui.staticlogistics.node.block_position"),
            Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ()),
            Component.translatable("gui.staticlogistics.node.connection_face"),
            direction(node.face()));
    }

    public static MutableComponent distanceFromPlayer(double distanceInBlocks) {
        String value = String.format(Locale.ROOT, "%.1f", distanceInBlocks);
        return Component.translatable("gui.staticlogistics.node.distance_from_player", value);
    }
}
