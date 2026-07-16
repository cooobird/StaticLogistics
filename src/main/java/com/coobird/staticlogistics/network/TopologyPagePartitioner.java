package com.coobird.staticlogistics.network;

import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.logistics.node.FaceTopology;
import com.coobird.staticlogistics.logistics.node.ScopedTopologyLink;
import com.coobird.staticlogistics.logistics.util.LogisticsConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** 按条目数量和编码复杂度双重限制拓扑页面。 */
public final class TopologyPagePartitioner {
    private TopologyPagePartitioner() {
    }

    public static List<List<FaceTopology>> faces(List<FaceTopology> values) {
        return partition(values, face -> 1 + face.groups().size());
    }

    public static List<List<ScopedTopologyLink>> links(List<ScopedTopologyLink> values) {
        return partition(values, ignored -> 3);
    }

    public static List<List<GroupRef>> groups(List<GroupRef> values) {
        return partition(values, ignored -> 1);
    }

    public static int pageWeight(List<FaceTopology> faces,
                                 List<ScopedTopologyLink> links,
                                 List<GroupRef> groups) {
        long weight = links.size() * 3L + groups.size();
        for (FaceTopology face : faces) weight += 1L + face.groups().size();
        return weight > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) weight;
    }

    public static int maximumPageWeight() {
        long configured = (long) LogisticsConstants.Network.getMaxBulkEntries() * 4L;
        return (int) Math.min(Integer.MAX_VALUE,
            Math.max(GroupConstraints.MAX_GROUPS_PER_OWNER + 1L, configured));
    }

    public static int maximumCombinedPageWeight() {
        return (int) Math.min(Integer.MAX_VALUE, maximumPageWeight() * 3L);
    }

    private static <T> List<List<T>> partition(List<T> values, ToIntFunction<T> weightFunction) {
        if (values.isEmpty()) return List.of();
        int maximumEntries = LogisticsConstants.Network.getMaxBulkEntries();
        int maximumWeight = maximumPageWeight();
        List<List<T>> pages = new ArrayList<>();
        List<T> current = new ArrayList<>();
        int weight = 0;
        for (T value : values) {
            int itemWeight = Math.max(1, weightFunction.applyAsInt(value));
            if (!current.isEmpty()
                && (current.size() >= maximumEntries
                || (long) weight + itemWeight > maximumWeight)) {
                pages.add(List.copyOf(current));
                current.clear();
                weight = 0;
            }
            current.add(value);
            weight += itemWeight;
        }
        if (!current.isEmpty()) pages.add(List.copyOf(current));
        return List.copyOf(pages);
    }
}
