package com.coobird.staticlogistics.client;

import com.coobird.staticlogistics.core.FaceConfig;
import com.coobird.staticlogistics.core.StaticLink;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientLinkCache {
    private static final Map<UUID, StaticLink> ID_TO_LINK = new ConcurrentHashMap<>();
    private static final Map<String, List<StaticLink>> LINKS_BY_GROUP = new ConcurrentHashMap<>();
    private static final Map<Long, List<StaticLink>> LINKS_BY_POS = new ConcurrentHashMap<>();
    private static final Map<Long, FaceConfig[]> FACE_CONFIGS = new ConcurrentHashMap<>();

    public static Collection<StaticLink> getAllLinks() {
        return ID_TO_LINK.values();
    }

    public static void addOrUpdateLink(StaticLink link) {
        if (link == null) return;
        StaticLink old = ID_TO_LINK.put(link.linkId(), link);
        if (old != null) removeFromGroupAndPos(old);
        LINKS_BY_GROUP.computeIfAbsent(link.groupId(), k -> new CopyOnWriteArrayList<>()).add(link);
        LINKS_BY_POS.computeIfAbsent(link.sourcePos().asLong(), k -> new CopyOnWriteArrayList<>()).add(link);
    }

    public static void removeLinkById(UUID id) {
        StaticLink removed = ID_TO_LINK.remove(id);
        if (removed != null) removeFromGroupAndPos(removed);
    }

    private static void removeFromGroupAndPos(StaticLink link) {
        List<StaticLink> groupList = LINKS_BY_GROUP.get(link.groupId());
        if (groupList != null) groupList.remove(link);
        List<StaticLink> posList = LINKS_BY_POS.get(link.sourcePos().asLong());
        if (posList != null) posList.remove(link);
    }

    public static List<StaticLink> getLinksByGroup(String groupId) {
        return LINKS_BY_GROUP.getOrDefault(groupId, Collections.emptyList());
    }

    public static List<StaticLink> getLinksByPos(BlockPos pos) {
        return LINKS_BY_POS.getOrDefault(pos.asLong(), Collections.emptyList());
    }

    public static void updateFaceConfig(BlockPos pos, Direction face, FaceConfig config) {
        FACE_CONFIGS.compute(pos.asLong(), (k, v) -> {
            FaceConfig[] configs = (v == null) ? new FaceConfig[6] : v;
            configs[face.get3DDataValue()] = config;
            return configs;
        });
    }

    public static FaceConfig getFaceConfig(BlockPos pos, Direction face) {
        FaceConfig[] configs = FACE_CONFIGS.get(pos.asLong());
        return (configs == null) ? null : configs[face.get3DDataValue()];
    }

    public static void invalidate() {
        ID_TO_LINK.clear();
        LINKS_BY_GROUP.clear();
        LINKS_BY_POS.clear();
        FACE_CONFIGS.clear();
    }
}