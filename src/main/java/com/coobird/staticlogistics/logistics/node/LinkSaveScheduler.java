package com.coobird.staticlogistics.logistics.node;

/**
 * 在服务器主线程上同步登记存档脏状态。
 */
class LinkSaveScheduler {
    private static final int FULL_SAVE_INTERVAL = 100;

    private int incrementalSaveCounter;
    private Runnable dirtyAction;

    void setStorage(LinkManagerStorage storage) {
        dirtyAction = storage == null ? null : storage::setDirty;
    }

    boolean needsFullSave() {
        return ++incrementalSaveCounter >= FULL_SAVE_INTERVAL;
    }

    void resetFullSaveCounter() {
        incrementalSaveCounter = 0;
    }

    void scheduleSave() {
        if (dirtyAction != null) dirtyAction.run();
    }

    void shutdown() {
        dirtyAction = null;
    }
}
