package com.coobird.staticlogistics.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClientLinkCache {

    private static final Set<StaticLink> ALL_LINKS = ConcurrentHashMap.newKeySet();
    private static final Map<Long, Map<Direction, FaceConfig>> FACE_CONFIGS = new ConcurrentHashMap<>();

    public static void updateLinks(List<StaticLink> newLinks) {
        ALL_LINKS.clear();
        ALL_LINKS.addAll(newLinks);
    }

    public static void addOrUpdateLink(StaticLink link) {
        ALL_LINKS.remove(link);
        ALL_LINKS.add(link);
    }

    public static void removeLink(StaticLink link) {
        ALL_LINKS.remove(link);
    }

    public static List<StaticLink> getLinksInArea(BlockPos center, double radius) {
        double radiusSq = radius * radius;
        List<StaticLink> result = new ArrayList<>();

        for (StaticLink link : ALL_LINKS) {
            if (link.sourcePos().distSqr(center) <= radiusSq || link.destPos().distSqr(center) <= radiusSq) {
                result.add(link);
            }
        }
        return result;
    }

    public static void updateFaceConfig(BlockPos pos, Direction face, FaceConfig config) {
        FACE_CONFIGS.compute(pos.asLong(), (k, v) -> {
            Map<Direction, FaceConfig> map = (v == null) ?
                Collections.synchronizedMap(new EnumMap<>(Direction.class)) : v;
            map.put(face, config);
            return map;
        });
    }

    public static FaceConfig getFaceConfig(BlockPos pos, Direction face) {
        Map<Direction, FaceConfig> map = FACE_CONFIGS.get(pos.asLong());
        return (map == null) ? null : map.get(face);
    }

    public static void invalidate() {
        ALL_LINKS.clear();
        FACE_CONFIGS.clear();
    }

    public static Set<StaticLink> getAllLinks() {
        return Collections.unmodifiableSet(ALL_LINKS);
    }
}