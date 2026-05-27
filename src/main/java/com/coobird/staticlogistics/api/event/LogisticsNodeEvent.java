package com.coobird.staticlogistics.api.event;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;

import java.util.Collection;
import java.util.Collections;

/**
 * 物流节点发生变化时触发的事件，比如新增节点、删除节点、修改节点
 */
public class LogisticsNodeEvent extends Event {
    // 当前所在的 MC 服务器实例
    private final MinecraftServer server;
    // 本次事件涉及到的节点列表（一个或多个）
    private final Collection<NodeEntry> affectedEntries;
    // 变动类型：新增、删除、还是改了
    private final ChangeType type;

    // 节点变动类型
    public enum ChangeType {
        ADDED,   // 新增了节点
        REMOVED, // 移除了节点
        CHANGED  // 节点信息被修改了
    }

    // 受影响的节点条目：记录频道 ID、节点本体、以及节点的角色
    public record NodeEntry(String groupId, LogisticsNode node, NodeRole role) {
    }

    // 构造函数——批量节点变动（一次性多个节点一起触发）
    public LogisticsNodeEvent(MinecraftServer server, Collection<NodeEntry> entries, ChangeType type) {
        this.server = server;
        this.affectedEntries = entries;
        this.type = type;
    }

    // 构造函数——单个节点变动
    public LogisticsNodeEvent(MinecraftServer server, NodeEntry entry, ChangeType type) {
        this(server, Collections.singletonList(entry), type);
    }

    public MinecraftServer getServer() {
        return server;
    }

    public Collection<NodeEntry> getAffectedEntries() {
        return affectedEntries;
    }

    public ChangeType getType() {
        return type;
    }
}