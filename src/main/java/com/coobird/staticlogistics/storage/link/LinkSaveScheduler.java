package com.coobird.staticlogistics.storage.link;

import com.coobird.staticlogistics.util.LogisticsConstants;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 保存调度器 —— 管理异步延迟保存、增量/全量保存计数。
 * <p>
 * 线程安全：scheduleSave 由多线程调用（配置变更），SAVER 单线程执行。
 * 使用 synchronized(this) 保护 pendingSave 的取消和重建。
 */
class LinkSaveScheduler {
    private static final ScheduledExecutorService SAVER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "LinkManager-Saver");
        t.setDaemon(true);
        return t;
    });
    private static volatile boolean isShutdown = false;
    private static final Logger LOGGER = LogUtils.getLogger();

    private final AtomicInteger incrementalSaveCounter = new AtomicInteger(0);
    private static final int FULL_SAVE_INTERVAL = 100;

    private LinkManagerStorage storage;
    private ScheduledFuture<?> pendingSave;

    void setStorage(LinkManagerStorage storage) {
        this.storage = storage;
    }

    boolean needsFullSave() {
        return incrementalSaveCounter.incrementAndGet() >= FULL_SAVE_INTERVAL;
    }

    void resetFullSaveCounter() {
        incrementalSaveCounter.set(0);
    }

    synchronized void scheduleSave() {
        if (storage == null || isShutdown) return;
        try {
            if (pendingSave != null && !pendingSave.isDone()) pendingSave.cancel(false);
            pendingSave = SAVER.schedule(() -> {
                try {
                    if (storage != null && !isShutdown) storage.setDirty();
                } catch (Exception e) {
                    LOGGER.error("Error during save", e);
                } finally {
                    synchronized (this) {
                        pendingSave = null;
                    }
                }
            }, 1, TimeUnit.SECONDS);
        } catch (RejectedExecutionException e) {
            LOGGER.warn("Save rejected, executor shutdown", e);
        }
    }

    void shutdown() {
        if (pendingSave != null) {
            pendingSave.cancel(false);
            pendingSave = null;
        }
    }

    static void shutdownSaver() {
        if (isShutdown) return;
        isShutdown = true;
        try {
            SAVER.shutdown();
            if (!SAVER.awaitTermination(LogisticsConstants.Thread.SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                SAVER.shutdownNow();
        } catch (InterruptedException e) {
            SAVER.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
