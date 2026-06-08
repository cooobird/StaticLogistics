package com.coobird.staticlogistics.transfer.log;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 传输最近日志 —— 环形缓冲区，线程安全。
 */
class TransferRecentLog {
    private final Deque<TransferEntry> log = new ConcurrentLinkedDeque<>();
    private static final int MAX_ENTRIES = 200;

    void add(TransferEntry entry) {
        while (log.size() >= MAX_ENTRIES) {
            log.pollFirst();
        }
        log.offerLast(entry);
    }

    List<TransferEntry> getRecent(int count) {
        List<TransferEntry> list = new ArrayList<>(log);
        if (list.size() <= count) return list;
        return list.subList(list.size() - count, list.size());
    }

    int size() {
        return log.size();
    }

    void clear() {
        log.clear();
    }
}
