package com.coobird.staticlogistics.item.blueprint;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 蓝图撤销管理器 —— 存储每个玩家最近一次粘贴的撤销数据。
 */
public class BlueprintUndoManager {
    private static final BlueprintUndoManager INSTANCE = new BlueprintUndoManager();
    private final Map<UUID, BlueprintUndoData> undoData = new ConcurrentHashMap<>();

    public static BlueprintUndoManager get() {
        return INSTANCE;
    }

    public void store(UUID playerUuid, BlueprintUndoData data) {
        undoData.put(playerUuid, data);
    }

    public BlueprintUndoData consume(UUID playerUuid) {
        return undoData.remove(playerUuid);
    }

    public boolean hasUndoData(UUID playerUuid) {
        return undoData.containsKey(playerUuid);
    }

    public void clear(UUID playerUuid) {
        undoData.remove(playerUuid);
    }
}
