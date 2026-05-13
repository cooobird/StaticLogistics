package com.coobird.staticlogistics.api.event;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;

import java.util.Collection;
import java.util.Collections;

public class LogisticsNodeEvent extends Event {
    private final MinecraftServer server;
    private final Collection<NodeEntry> affectedEntries;
    private final ChangeType type;

    public enum ChangeType {
        ADDED,
        REMOVED,
        CHANGED
    }

    public record NodeEntry(String groupId, LogisticsNode node, NodeRole role) {
    }

    public LogisticsNodeEvent(MinecraftServer server, Collection<NodeEntry> entries, ChangeType type) {
        this.server = server;
        this.affectedEntries = entries;
        this.type = type;
    }

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