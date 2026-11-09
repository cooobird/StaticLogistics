package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.api.LogisticsNode;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/// 仅为正在观察的节点分配窗口；使用真实经过时间，低 TPS 时不会虚报每秒速率。
public final class LiveTransferRates {
    private static final long BUCKET_NANOS = 100_000_000L;
    // 保留最近一秒以及当前时间桶。
    private static final int SLOTS = 11;
    private final Map<ResourceLocation, Window> windows = new HashMap<>();
    private final Map<LogisticsNode, LiveTransferRates> peers = new HashMap<>();
    private final Map<LogisticsNode, Integer> peerObservers = new HashMap<>();
    int observers;

    public record Rate(double sent, double received) {
        public static final Rate ZERO = new Rate(0, 0);
    }

    /// Only explicitly subscribed peers are tracked; the observer owns their lifetime.
    public void includePeer(LogisticsNode peer) {
        peers.computeIfAbsent(peer, ignored -> new LiveTransferRates());
        peerObservers.merge(peer, 1, Integer::sum);
    }

    public void releasePeer(LogisticsNode peer) {
        peerObservers.computeIfPresent(peer, (ignored, count) -> count > 1 ? count - 1 : null);
        if (!peerObservers.containsKey(peer)) peers.remove(peer);
    }

    void recordPeer(LogisticsNode peer, ResourceLocation type, long amount, boolean sent, long now) {
        LiveTransferRates rates = peers.get(peer);
        if (rates != null) rates.record(type, amount, sent, now);
    }

    public Map<LogisticsNode, Map<ResourceLocation, Rate>> peerSnapshots() {
        Map<LogisticsNode, Map<ResourceLocation, Rate>> result = new HashMap<>();
        peers.forEach((peer, rates) -> result.put(peer, rates.snapshot()));
        return Map.copyOf(result);
    }

    void record(ResourceLocation type, long amount, boolean sent, long now) {
        if (amount <= 0) return;
        Window window = windows.computeIfAbsent(type, ignored -> new Window());
        long bucket = Math.floorDiv(now, BUCKET_NANOS);
        int slot = Math.floorMod(bucket, SLOTS);
        if (window.ids[slot] != bucket) {
            window.ids[slot] = bucket;
            window.sent[slot] = window.received[slot] = 0;
        }
        if (sent) window.sent[slot] += amount;
        else window.received[slot] += amount;
    }

    public Map<ResourceLocation, Rate> snapshot() {
        long bucket = Math.floorDiv(System.nanoTime(), BUCKET_NANOS);
        Map<ResourceLocation, Rate> result = new HashMap<>();
        windows.forEach((type, window) -> {
            double sent = 0, received = 0;
            for (int index = 0; index < SLOTS; index++) {
                long age = bucket - window.ids[index];
                /// 最近十个完整的 100 ms，避免当前尚未结束的时间桶引起周期性跳动。
                if (age > 0 && age <= 10) {
                    sent += window.sent[index];
                    received += window.received[index];
                }
            }
            result.put(type, new Rate(sent, received));
        });
        return Map.copyOf(result);
    }

    private static final class Window {
        final long[] ids = new long[SLOTS];
        final double[] sent = new double[SLOTS];
        final double[] received = new double[SLOTS];
    }
}
