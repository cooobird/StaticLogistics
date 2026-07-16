package com.coobird.staticlogistics.logistics.node;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 脏数据追踪器 —— 记录当前 tick 内变更的面 key 和容器 key。
 * <p>
 * 线程安全：mark* 方法由主线程调用（配置变更回调），drain* 由保存线程调用。
 * mark 操作直接写入（主线程单线程），drain 操作通过 synchronized 保证可见性。
 */
class LinkDirtyTracker {
    private final Set<FaceAddress> dirtyFaceKeys = new LinkedHashSet<>();
    private final LongSet dirtyContainerKeys = new LongOpenHashSet();

    void markFaceDirty(FaceAddress faceKey) {
        dirtyFaceKeys.add(faceKey);
    }

    void markContainerDirty(long containerKey) {
        dirtyContainerKeys.add(containerKey);
    }

    Set<FaceAddress> snapshotDirtyFaces() {
        synchronized (dirtyFaceKeys) {
            return Set.copyOf(dirtyFaceKeys);
        }
    }

    LongSet snapshotDirtyContainers() {
        synchronized (dirtyContainerKeys) {
            return new LongOpenHashSet(dirtyContainerKeys);
        }
    }

    void ackDirtyFaces(Set<FaceAddress> savedKeys) {
        synchronized (dirtyFaceKeys) {
            dirtyFaceKeys.removeAll(savedKeys);
        }
    }

    void ackDirtyContainers(LongSet savedKeys) {
        synchronized (dirtyContainerKeys) {
            dirtyContainerKeys.removeAll(savedKeys);
        }
    }
}
