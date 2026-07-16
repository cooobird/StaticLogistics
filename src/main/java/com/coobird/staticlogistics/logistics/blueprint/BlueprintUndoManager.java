package com.coobird.staticlogistics.logistics.blueprint;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;

/**
 * 蓝图撤销管理器 —— 存储每个玩家最近一次粘贴的撤销数据。
 */
public class BlueprintUndoManager {
    private static final Map<MinecraftServer, BlueprintUndoManager> INSTANCES = new ConcurrentHashMap<>();
    private final Map<UUID, BlueprintUndoData> undoData = new ConcurrentHashMap<>();

    public static BlueprintUndoManager get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, ignored -> new BlueprintUndoManager());
    }

    public static void release(MinecraftServer server) {
        BlueprintUndoManager removed = INSTANCES.remove(server);
        if (removed != null) removed.clearAll();
    }

    public void store(UUID playerUuid, BlueprintUndoData data) {
        undoData.put(playerUuid, data);
    }

    public BlueprintUndoData consume(UUID playerUuid) {
        return undoData.remove(playerUuid);
    }

    public BlueprintUndoData peek(UUID playerUuid) {
        return undoData.get(playerUuid);
    }

    public boolean hasUndoData(UUID playerUuid) {
        return undoData.containsKey(playerUuid);
    }

    public void clear(UUID playerUuid) {
        undoData.remove(playerUuid);
    }

    public void clearAll() {
        undoData.clear();
    }
}
