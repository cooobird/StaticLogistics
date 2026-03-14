package com.coobird.staticlogistics.client;

import com.coobird.staticlogistics.core.FaceConfig;
import com.coobird.staticlogistics.core.StaticLink;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientLinkCache {
    private static final Set<StaticLink> ALL_LINKS = ConcurrentHashMap.newKeySet();
    private static final Map<String, List<StaticLink>> LINKS_BY_GROUP = new ConcurrentHashMap<>();
    private static final Map<Long, Map<Direction, FaceConfig>> FACE_CONFIGS = new ConcurrentHashMap<>();

    public static void setLinks(List<StaticLink> newLinks) {
        ALL_LINKS.clear();
        LINKS_BY_GROUP.clear();
        for (StaticLink link : newLinks) {
            addOrUpdateLink(link);
        }
    }

    public static void addOrUpdateLink(StaticLink link) {
        removeLink(link);
        ALL_LINKS.add(link);
        LINKS_BY_GROUP.computeIfAbsent(link.groupId(), k -> new CopyOnWriteArrayList<>()).add(link);
    }

    public static void removeLink(StaticLink link) {
        if (ALL_LINKS.remove(link)) {
            List<StaticLink> groupLinks = LINKS_BY_GROUP.get(link.groupId());
            if (groupLinks != null) {
                groupLinks.remove(link);
                if (groupLinks.isEmpty()) LINKS_BY_GROUP.remove(link.groupId());
            }
        }
    }

    public static List<StaticLink> getLinksByGroup(String groupId) {
        return LINKS_BY_GROUP.getOrDefault(groupId, Collections.emptyList());
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
        LINKS_BY_GROUP.clear();
        FACE_CONFIGS.clear();
    }
}