package com.coobird.staticlogistics.client;

import com.coobird.staticlogistics.core.FaceConfig;
import com.coobird.staticlogistics.core.StaticLink;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class ClientLinkCache {
    private static final Set<StaticLink> ALL_LINKS = new CopyOnWriteArraySet<>();
    private static final Map<Long, Map<Direction, FaceConfig>> FACE_CONFIGS = new ConcurrentHashMap<>();

    public static void updateLinks(List<StaticLink> newLinks) {
        ALL_LINKS.clear();
        ALL_LINKS.addAll(newLinks);
    }

    public static void updateFaceConfig(BlockPos pos, Direction face, FaceConfig config) {
        FACE_CONFIGS.compute(pos.asLong(), (k, v) -> {
            Map<Direction, FaceConfig> map = (v == null) ? Collections.synchronizedMap(new EnumMap<>(Direction.class)) : v;
            map.put(face, config);
            return map;
        });
    }

    public static FaceConfig getFaceConfig(BlockPos pos, Direction face) {
        Map<Direction, FaceConfig> map = FACE_CONFIGS.get(pos.asLong());
        if (map == null || !map.containsKey(face)) return new FaceConfig();
        return map.get(face);
    }

    public static List<StaticLink> getLinksInArea(BlockPos center, double radius) {
        double radiusSq = radius * radius;
        return ALL_LINKS.stream()
            .filter(link -> link.sourcePos().distSqr(center) <= radiusSq ||
                link.destPos().distSqr(center) <= radiusSq)
            .toList();
    }

    public static void invalidate() {
        ALL_LINKS.clear();
        FACE_CONFIGS.clear();
    }

    public static Set<StaticLink> getAllLinks() {
        return Collections.unmodifiableSet(ALL_LINKS);
    }
}