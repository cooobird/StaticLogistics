package com.coobird.staticlogistics.logistics.node;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 脏数据追踪器 —— 记录当前 tick 内变更的面 key 和容器 key。
 * <p>
 * 线程安全：所有操作都由服务器主线程调用。
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

    /**
     * 获取脏面键快照，但在保存成功确认前不清除。
     */
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
