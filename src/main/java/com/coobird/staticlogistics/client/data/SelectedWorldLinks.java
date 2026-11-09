package com.coobird.staticlogistics.client.data;

import com.coobird.staticlogistics.logistics.node.ConnectionKey;

import java.util.Set;

/// 保存目录选中的连接；空集合永远不代表整个网络。
public final class SelectedWorldLinks {
    private static Set<ConnectionKey> links = Set.of();
    private static Set<ConnectionKey> blueprintLinks = Set.of();
    private static long version;

    public static long version() {
        return version;
    }

    private SelectedWorldLinks() {
    }

    public static Set<ConnectionKey> get() {
        return links;
    }

    public static void set(Set<ConnectionKey> selection) {
        if (links.equals(selection)) return;
        links = Set.copyOf(selection);
        version++;
    }

    public static Set<ConnectionKey> blueprint() {
        return blueprintLinks;
    }

    public static void setBlueprint(Set<ConnectionKey> selection) {
        if (blueprintLinks.equals(selection)) return;
        blueprintLinks = Set.copyOf(selection);
        version++;
    }

    public static void clear() {
        set(Set.of());
        setBlueprint(Set.of());
    }
}
