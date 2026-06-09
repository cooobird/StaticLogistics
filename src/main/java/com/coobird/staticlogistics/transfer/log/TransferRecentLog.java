package com.coobird.staticlogistics.transfer.log;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 传输最近日志
 */
class TransferRecentLog {
    private final Deque<TransferEntry> log = new ConcurrentLinkedDeque<>();
    private static final int MAX_ENTRIES = 200;

    void add(TransferEntry entry) {
        while (log.size() >= MAX_ENTRIES) {
            TransferEntry old = log.pollFirst();
            if (old != null) old.recycle();
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
        TransferEntry entry;
        while ((entry = log.pollFirst()) != null) {
            entry.recycle();
        }
    }
}
