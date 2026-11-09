package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.api.LogisticsNode;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// 管理服务器会话内的传输日志、累计统计和实时速率。
public class TransferLogManager {
    private static final Map<MinecraftServer, TransferLogManager> INSTANCES = new HashMap<>();
    private static final long FAILURE_LOG_INTERVAL_TICKS = 20L;
    private static final int MAX_TRACKED_FAILURES = 1_024;

    public static TransferLogManager get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, TransferLogManager::new);
    }

    public static void release(MinecraftServer server) {
        TransferLogManager removed = INSTANCES.remove(server);
        if (removed != null) removed.reset();
    }

    /// 生命周期结束后释放节点明细；全局累计统计与最近日志仍保留。
    public static void forgetNode(MinecraftServer server, LogisticsNode node) {
        TransferLogManager manager = INSTANCES.get(server);
        if (manager == null) return;
        manager.stats.removeNode(node);
        manager.failureLogTicks.keySet().removeIf(key -> key.source().equals(node) || key.target().equals(node));
    }

    private final MinecraftServer server;
    private final TransferRecentLog recentLog = new TransferRecentLog();
    private final TransferStats stats = new TransferStats();

    private record ObservedEndpoint(LogisticsNode node, GlobalPos peer) {
    }

    private final Map<LogisticsNode, LiveTransferRates> liveRates = new HashMap<>();
    private final Map<ObservedEndpoint, Integer> observedEndpoints = new HashMap<>();
    // 只为正在查看的连接保存最近一次没有候选资源的观察，不计为失败。
    private final Map<ObservedEndpoint, Long> noCandidateNanos = new HashMap<>();

    public LiveTransferRates observe(LogisticsNode node, GlobalPos peer) {
        LiveTransferRates rates = liveRates.computeIfAbsent(node, ignored -> new LiveTransferRates());
        rates.observers++;
        observedEndpoints.merge(new ObservedEndpoint(node, peer), 1, Integer::sum);
        return rates;
    }

    public void stopObserving(LogisticsNode node, GlobalPos peer) {
        ObservedEndpoint key = new ObservedEndpoint(node, peer);
        observedEndpoints.computeIfPresent(key, (ignored, count) -> count > 1 ? count - 1 : null);
        if (!observedEndpoints.containsKey(key)) noCandidateNanos.remove(key);
        LiveTransferRates rates = liveRates.get(node);
        if (rates != null && --rates.observers == 0) {
            liveRates.remove(node);
            noCandidateNanos.keySet().removeIf(endpoint -> endpoint.node().equals(node));
        }
    }

    public void observeNoCandidate(LogisticsNode source, LogisticsNode target) {
        if (liveRates.isEmpty()) return;
        long now = System.nanoTime();
        var sent = new ObservedEndpoint(source, target.gPos());
        var received = new ObservedEndpoint(target, source.gPos());
        if (observedEndpoints.containsKey(sent)) noCandidateNanos.put(sent, now);
        if (observedEndpoints.containsKey(received)) noCandidateNanos.put(received, now);
    }

    public boolean recentlyObservedNoCandidate(LogisticsNode node, GlobalPos peer) {
        Long observed = noCandidateNanos.get(new ObservedEndpoint(node, peer));
        return observed != null && System.nanoTime() - observed < 5_000_000_000L;
    }

    private final TransferRateCalculator rateCalculator = new TransferRateCalculator();
    private final Map<FailureLogKey, Long> failureLogTicks = new LinkedHashMap<>(16, 0.75F, true);
    private long lastTransferTick = -1;

    private TransferLogManager(MinecraftServer server) {
        this.server = server;
    }

    public void logTransfer(LogisticsNode source, LogisticsNode target,
                            LogisticsResource<?> type, long amount, boolean success) {
        logTransfer(source, target, type, amount, success, null);
    }

    public void logTransfer(LogisticsNode source, LogisticsNode target,
                            LogisticsResource<?> type, long amount, boolean success,
                            TransferFailureReason reason) {
        long gameTick = currentTick();
        if (!noCandidateNanos.isEmpty()) {
            noCandidateNanos.remove(new ObservedEndpoint(source, target.gPos()));
            noCandidateNanos.remove(new ObservedEndpoint(target, source.gPos()));
        }
        /// 即使补偿未完整成功，已确认存入的数量也属于实际成交量。
        if (amount > 0 && !liveRates.isEmpty()) {
            long now = System.nanoTime();
            LiveTransferRates sent = liveRates.get(source);
            LiveTransferRates received = liveRates.get(target);
            if (sent != null) {
                sent.record(type.typeId(), amount, true, now);
                sent.recordPeer(target, type.typeId(), amount, true, now);
            }
            if (received != null) {
                received.record(type.typeId(), amount, false, now);
                received.recordPeer(source, type.typeId(), amount, false, now);
            }
        }
        if (!success) stats.incrementFailed();
        if (!success && reason != null
            && shouldSuppressFailure(source, target, type, reason, gameTick)) return;

        TransferEntry entry = TransferEntry.obtain(
            System.currentTimeMillis(),
            source.gPos().dimension().location().toString(),
            source.gPos().pos().getX(), source.gPos().pos().getY(), source.gPos().pos().getZ(),
            source.face().getName(), source.channel(),
            target.gPos().dimension().location().toString(),
            target.gPos().pos().getX(), target.gPos().pos().getY(), target.gPos().pos().getZ(),
            target.face().getName(), target.channel(),
            type.typeId().toString(), type.color(),
            amount, success, reason != null ? reason.id().toString() : null
        );
        recentLog.add(entry);

        if (!success) {
            return;
        }
        stats.incrementTotal(amount);
        stats.recordType(type.typeId().toString(), amount);
        stats.recordSource(source, amount, gameTick);
        stats.recordTarget(target, amount, gameTick);
        rateCalculator.record(amount, gameTick);
        lastTransferTick = gameTick;
    }

    /// 对同一链路、类型和原因的连续失败按游戏刻限频。
    private boolean shouldSuppressFailure(LogisticsNode source, LogisticsNode target,
                                          LogisticsResource<?> type, TransferFailureReason reason,
                                          long gameTick) {
        FailureLogKey key = new FailureLogKey(source, target, type.typeId(), reason.id());
        Long previous = failureLogTicks.get(key);
        if (previous != null && gameTick - previous < FAILURE_LOG_INTERVAL_TICKS) return true;
        if (previous == null && failureLogTicks.size() >= MAX_TRACKED_FAILURES) {
            var iterator = failureLogTicks.keySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        failureLogTicks.put(key, gameTick);
        return false;
    }

    private long currentTick() {
        return server.overworld().getGameTime();
    }

    public List<TransferEntry> getRecent(int count) {
        return recentLog.getRecent(count);
    }

    // 返回此连接面的最近记录；维度、朝向和通道都必须一致。
    public TransferEntry latestFor(LogisticsNode node, GlobalPos peer) {
        List<TransferEntry> entries = recentLog.getRecent(200);
        var pos = node.gPos().pos();
        String dimension = node.gPos().dimension().location().toString();
        String face = node.face().getName();
        for (int i = entries.size() - 1; i >= 0; i--) {
            TransferEntry e = entries.get(i);
            boolean source = dimension.equals(e.sourceDim()) && pos.getX() == e.sx() && pos.getY() == e.sy()
                && pos.getZ() == e.sz() && face.equals(e.sourceFace()) && node.channel() == e.sourceChannel();
            boolean target = dimension.equals(e.targetDim()) && pos.getX() == e.tx() && pos.getY() == e.ty()
                && pos.getZ() == e.tz() && face.equals(e.targetFace()) && node.channel() == e.targetChannel();
            var other = peer.pos();
            String otherDimension = peer.dimension().location().toString();
            boolean sourcePeer = otherDimension.equals(e.targetDim()) && other.getX() == e.tx() && other.getY() == e.ty() && other.getZ() == e.tz();
            boolean targetPeer = otherDimension.equals(e.sourceDim()) && other.getX() == e.sx() && other.getY() == e.sy() && other.getZ() == e.sz();
            if (source && sourcePeer || target && targetPeer) return e;
        }
        return null;
    }

    public int getLogSize() {
        return recentLog.size();
    }

    public long getTotalTransfers() {
        return stats.getTotalTransfers();
    }

    public long getTotalAmount() {
        return stats.getTotalAmount();
    }

    public long getFailedTransfers() {
        return stats.getFailedTransfers();
    }

    public Map<String, TypeStats> getPerTypeStats() {
        return stats.getPerTypeStats();
    }

    public NodeStats getPerNodeStats(LogisticsNode node) {
        return stats.getPerNodeStats(node, currentTick());
    }

    public List<Map.Entry<LogisticsNode, NodeStats>> getTopNodes(int n, boolean bySent) {
        return stats.getTopNodes(n, bySent);
    }

    public double getTransfersPerMinute() {
        return rateCalculator.getTransfersPerMinute(currentTick());
    }

    public double getAmountPerMinute() {
        return rateCalculator.getAmountPerMinute(currentTick());
    }

    public void reset() {
        liveRates.clear();
        observedEndpoints.clear();
        noCandidateNanos.clear();
        recentLog.clear();
        stats.clear();
        rateCalculator.clear();
        failureLogTicks.clear();
        lastTransferTick = -1;
    }

    private record FailureLogKey(LogisticsNode source, LogisticsNode target, ResourceLocation typeId,
                                 ResourceLocation reasonId) {
    }
}
