package com.coobird.staticlogistics.transfer;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

/// 冷却与就绪任务分开排序；到期来源按入队顺序轮转。
public final class TransferWorkQueue<K> {
    private record Entry<K>(K key, long dueTick, long order) {
    }

    private final Map<K, Entry<K>> entries = new HashMap<>();
    private final TreeSet<Entry<K>> waiting = new TreeSet<>(Comparator.comparingLong((Entry<K> a) -> a.dueTick()).thenComparingLong(Entry::order));
    private final TreeSet<Entry<K>> ready = new TreeSet<>(Comparator.comparingLong(Entry::order));
    private long sequence;

    public void schedule(K key, long tick) {
        remove(key);
        Entry<K> entry = new Entry<>(key, tick, sequence++);
        entries.put(key, entry);
        waiting.add(entry);
    }

    /// 到期搬运单独计入准备预算，尚未搬运的任务留待下一轮。
    public void prepare(long tick, long deadline) {
        while (!waiting.isEmpty() && waiting.first().dueTick() <= tick && System.nanoTime() - deadline < 0L) {
            ready.add(waiting.pollFirst());
        }
    }

    public boolean hasReady() {
        return !ready.isEmpty();
    }

    public K poll() {
        if (ready.isEmpty()) return null;
        Entry<K> entry = ready.pollFirst();
        entries.remove(entry.key());
        return entry.key();
    }

    public void remove(K key) {
        Entry<K> entry = entries.remove(key);
        if (entry != null) {
            waiting.remove(entry);
            ready.remove(entry);
        }
    }

    public int size() {
        return entries.size();
    }
}
