package com.coobird.staticlogistics.transfer.strategy;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.type.GroupSorter;
import com.coobird.staticlogistics.logistics.group.GlobalLogisticsManager;
import com.coobird.staticlogistics.logistics.node.EndpointAddress;
import com.coobird.staticlogistics.logistics.node.EndpointConfig;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import com.coobird.staticlogistics.transfer.TransferContext;
import com.coobird.staticlogistics.transfer.TransferUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/// 分批收集、排序目标；预算不足时保留进度，真正提交仍由传输管线重新校验。
public class StrategyBasedTargetSelector {
    private record Measured(LogisticsNode node, double distance, int order) {
    }

    private static final class Scan {
        final long version;
        final TransferContext.Progress progress = new TransferContext.Progress();
        final Iterator<Map.Entry<GroupKey, LogisticsNode>> links;
        final Set<LogisticsNode> seen = new HashSet<>();
        final Map<Long, List<LogisticsNode>> buckets = new TreeMap<>(Comparator.reverseOrder());
        final List<LogisticsNode> result = new ArrayList<>();
        Iterator<Map.Entry<Long, List<LogisticsNode>>> groups;
        List<LogisticsNode> group;
        PriorityQueue<Measured> measured;
        int prepareIndex, appendIndex, rotation;
        boolean arranged;
        List<LogisticsNode> prepared;

        Scan(EndpointConfig config) {
            version = config.getVersion();
            links = config.linkedNodeIterator();
        }
    }

    private final Map<EndpointConfig, Map<ResourceLocation, Scan>> scans = new WeakHashMap<>();

    public void clearCache() {
        scans.clear();
    }

    /// 目标配置变化会影响所有相邻来源的排序与候选资格，不只影响目标自身的发送任务。
    public void invalidateTarget(MinecraftServer server, EndpointConfig target) {
        for (LogisticsNode source : target.getLinkedNodes()) {
            var level = server.getLevel(source.gPos().dimension());
            if (level == null) continue;
            EndpointConfig config = LinkManager.get(level).getEndpointConfig(EndpointAddress.of(source));
            if (config != null) scans.remove(config);
        }
    }

    /// null 表示仍在准备；空列表才表示本轮已确认没有目标。
    @Nullable
    public List<LogisticsNode> selectTargets(TransferContext context) {
        if (!context.level().getServer().isSameThread())
            throw new IllegalStateException("Target selection requires the server thread");
        EndpointConfig source = context.sourceConfig();
        var byType = scans.computeIfAbsent(source, ignored -> new HashMap<>());
        Scan scan = byType.get(context.typeId());
        if (scan == null || scan.version != source.getVersion()) {
            scan = new Scan(source);
            byType.put(context.typeId(), scan);
        }
        try {
            while (context.hasTimeRemaining()) {
                if (scan.groups == null) {
                    if (scan.links.hasNext()) {
                        collect(context, scan, scan.links.next());
                        continue;
                    }
                    scan.groups = scan.buckets.entrySet().iterator();
                }
                if (scan.group == null) {
                    if (!scan.groups.hasNext()) {
                        context.bindProgress(scan.progress);
                        if (scan.prepared == null) scan.prepared = List.copyOf(scan.result);
                        return scan.prepared;
                    }
                    var bucket = scan.groups.next();
                    scan.group = bucket.getValue();
                    scan.prepareIndex = 0;
                    scan.appendIndex = 0;
                    scan.rotation = 0;
                    scan.arranged = false;
                    scan.measured = null;
                    GroupSorter sorter = source.linkConfig.getStrategy().sorter();
                    if (scan.group.size() <= 1) {
                        scan.arranged = true;
                    } else if (sorter == RoundRobinGroupSorter.INSTANCE) {
                        int size = scan.group.size();
                        scan.rotation = Math.floorMod(GlobalLogisticsManager.get(context.level().getServer())
                            .getAndUpdateDistributionCursor(context.sourceNode(), context.typeId(), bucket.getKey(),
                                current -> (Math.floorMod(current, size) + 1) % size), size);
                    } else if (sorter instanceof DistanceGroupSorter distance) {
                        Comparator<Measured> comparator = Comparator.comparingDouble(Measured::distance);
                        if (distance == DistanceGroupSorter.FURTHEST) comparator = comparator.reversed();
                        scan.measured = new PriorityQueue<>(comparator.thenComparingInt(Measured::order));
                    } else if (sorter != SequentialGroupSorter.INSTANCE && sorter != RandomGroupSorter.INSTANCE && sorter != RoundRobinGroupSorter.INSTANCE) {
                        /// 扩展排序器保留原接口语义，调用完成的结果也保留到下一 tick。
                        var global = GlobalLogisticsManager.get(context.level().getServer());
                        scan.group = sorter.sort(context.level(), scan.group, context.sourceNode().gPos().pos(),
                            context.sourceNode(), global::getAndUpdateCursor);
                        scan.arranged = true;
                    }
                }
                GroupSorter sorter = source.linkConfig.getStrategy().sorter();
                if (!scan.arranged && scan.prepareIndex < scan.group.size()) {
                    int index = scan.prepareIndex++;
                    if (scan.measured != null) {
                        LogisticsNode node = scan.group.get(index);
                        double distance = DistanceGroupSorter.distance(context.level(),
                            context.sourceNode().gPos().pos(), context.sourceNode(), node);
                        scan.measured.add(new Measured(node, distance, index));
                    } else if (sorter == RandomGroupSorter.INSTANCE) {
                        int remaining = scan.group.size() - index;
                        Collections.swap(scan.group, remaining - 1, ThreadLocalRandom.current().nextInt(remaining));
                    } else {
                        scan.prepareIndex = scan.group.size();
                    }
                    continue;
                }
                scan.arranged = true;
                if (scan.appendIndex < scan.group.size()) {
                    LogisticsNode node = scan.measured == null
                        ? scan.group.get((scan.rotation + scan.appendIndex) % scan.group.size())
                        : scan.measured.remove().node();
                    scan.result.add(node);
                    scan.appendIndex++;
                } else scan.group = null;
            }
            return null;
        } catch (RuntimeException failure) {
            byType.remove(context.typeId());
            throw failure;
        }
    }

    /// 只有传输真正获得执行机会后才释放准备结果，避免预算边界反复重扫。
    public void completeSelection(TransferContext context) {
        var byType = scans.get(context.sourceConfig());
        if (byType == null) return;
        byType.remove(context.typeId());
        if (byType.isEmpty()) scans.remove(context.sourceConfig());
    }

    private static void collect(TransferContext context, Scan scan,
                                Map.Entry<GroupKey, LogisticsNode> candidate) {
        LogisticsNode target = candidate.getValue();
        GroupKey group = candidate.getKey();
        if (scan.seen.contains(target) || !context.sourceConfig().faceConfig.containsGroup(group)) return;
        var level = context.level().getServer().getLevel(target.gPos().dimension());
        if (level == null) return;
        EndpointConfig config = LinkManager.get(level).getEndpointConfig(EndpointAddress.of(target));
        if (config == null || !TransferUtils.isTransferLinkActive(context.level().getServer(),
            context.sourceNode(), context.sourceConfig(), target, config, group)) return;
        scan.seen.add(target);
        scan.buckets.computeIfAbsent(config.linkConfig.getPriority(), ignored -> new ArrayList<>()).add(target);
    }
}
