package com.coobird.staticlogistics.client;

import com.coobird.staticlogistics.core.StaticLink;
import net.minecraft.core.BlockPos;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class ClientLinkCache {
    private static final Set<StaticLink> ALL_LINKS = new CopyOnWriteArraySet<>();

    public static void updateLinks(List<StaticLink> newLinks) {
        ALL_LINKS.clear();
        ALL_LINKS.addAll(newLinks);
    }

    // 提供给渲染器调用
    public static List<StaticLink> getLinksInArea(BlockPos center, double radius) {
        double radiusSq = radius * radius;
        return ALL_LINKS.stream()
            .filter(link -> link.sourcePos().distSqr(center) <= radiusSq ||
                link.destPos().distSqr(center) <= radiusSq)
            .toList();
    }

    public static void invalidate() {
        ALL_LINKS.clear();
    }

    public static Set<StaticLink> getAllLinks() {
        return Collections.unmodifiableSet(ALL_LINKS);
    }
}