package com.coobird.staticlogistics.transfer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** 服务器会话内、仅由主线程访问的有界最近传输日志。 */
class TransferRecentLog {
    private static final int MAX_ENTRIES = 200;
    private final Deque<TransferEntry> log = new ArrayDeque<>(MAX_ENTRIES);

    void add(TransferEntry entry) {
        if (log.size() >= MAX_ENTRIES) log.removeFirst();
        log.addLast(entry);
    }

    List<TransferEntry> getRecent(int count) {
        if (count <= 0) return List.of();
        List<TransferEntry> entries = new ArrayList<>(log);
        if (entries.size() <= count) return List.copyOf(entries);
        return List.copyOf(entries.subList(entries.size() - count, entries.size()));
    }

    int size() {
        return log.size();
    }

    void clear() {
        log.clear();
    }
}
