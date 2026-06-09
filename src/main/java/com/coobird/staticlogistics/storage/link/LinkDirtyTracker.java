package com.coobird.staticlogistics.storage.link;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * 脏数据追踪器 —— 记录当前 tick 内变更的面 key 和容器 key。
 * <p>
 * 线程安全：mark* 方法由主线程调用（配置变更回调），drain* 由保存线程调用。
 * mark 操作直接写入（主线程单线程），drain 操作通过 synchronized 保证可见性。
 */
class LinkDirtyTracker {
    private final LongSet dirtyFaceKeys = new LongOpenHashSet();
    private final LongSet dirtyContainerKeys = new LongOpenHashSet();

    void markFaceDirty(long faceKey) {
        dirtyFaceKeys.add(faceKey);
    }

    void markContainerDirty(long containerKey) {
        dirtyContainerKeys.add(containerKey);
    }

    LongSet drainDirtyFaces() {
        synchronized (dirtyFaceKeys) {
            if (dirtyFaceKeys.isEmpty()) return new LongOpenHashSet();
            LongSet copy = new LongOpenHashSet(dirtyFaceKeys);
            dirtyFaceKeys.clear();
            return copy;
        }
    }

    LongSet drainDirtyContainers() {
        synchronized (dirtyContainerKeys) {
            if (dirtyContainerKeys.isEmpty()) return new LongOpenHashSet();
            LongSet copy = new LongOpenHashSet(dirtyContainerKeys);
            dirtyContainerKeys.clear();
            return copy;
        }
    }
}
