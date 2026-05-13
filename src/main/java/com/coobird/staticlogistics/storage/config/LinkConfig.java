package com.coobird.staticlogistics.storage.config;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.TransferType;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class LinkConfig {
    private final Map<ResourceLocation, SideData> typeSettings = new ConcurrentHashMap<>();
    private Consumer<LinkConfig> onDirty = (c) -> {
    };

    public LinkConfig() {
    }

    public SideData getSettings(TransferType type) {
        return typeSettings.computeIfAbsent(type.id(), id -> new SideData());
    }

    public NodeRole determineRole() {
        boolean canSend = false;
        boolean canReceive = false;
        for (SideData data : typeSettings.values()) {
            if (data.outputEnabled) canSend = true;
            if (data.inputEnabled) canReceive = true;
        }
        if (canSend && canReceive) return NodeRole.BOTH;
        if (canSend) return NodeRole.SENDER;
        if (canReceive) return NodeRole.RECEIVER;
        return NodeRole.NONE;
    }

    public boolean isDefault() {
        for (SideData data : typeSettings.values()) {
            if (!data.isDefault()) return false;
        }
        return true;
    }


    public Map<ResourceLocation, SideData> getAllSettings() {
        return typeSettings;
    }

    public void setOnDirty(Consumer<LinkConfig> onDirty) {
        this.onDirty = onDirty;
    }

    public static class SideData {
        public boolean inputEnabled = false;
        public boolean outputEnabled = false;
        public int inputChannel = 0;
        public int outputChannel = 0;
        public DistributionStrategy strategy = DistributionStrategy.SEQUENTIAL;
        public int priority = 0;
        public final Set<LogisticsNode> linkedInputs = ConcurrentHashMap.newKeySet();

        public boolean isDefault() {
            return linkedInputs.isEmpty() && !inputEnabled && !outputEnabled && inputChannel == 0 && outputChannel == 0;
        }
    }
}