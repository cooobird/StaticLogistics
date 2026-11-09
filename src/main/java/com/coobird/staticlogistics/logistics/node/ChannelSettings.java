package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.ExtractionMode;
import com.coobird.staticlogistics.transfer.DistributionStrategyRegistry;

import java.util.function.Consumer;

/// 链接传输配置，管理分发策略、提取模式、优先级和存量维持。
public class ChannelSettings {
    private Consumer<ChannelSettings> onDirty = (c) -> {
    };

    private Consumer<ChannelSettings> onNumericDirty = c -> {
    };

    void setOnNumericDirty(Consumer<ChannelSettings> callback) {
        onNumericDirty = callback;
    }

    private DistributionStrategy strategy = DistributionStrategyRegistry.SEQUENTIAL;
    private ExtractionMode extractionMode = ExtractionMode.SEQUENTIAL;
    private long priority = 0;
    private long keepStock = 0;
    private ChannelRedstoneMode redstoneMode = ChannelRedstoneMode.IGNORE;
    private boolean enabled = true;
    private String channelName = "";

    public String getChannelName() {
        return channelName;
    }

    void setChannelName(String name) {
        String value = GroupConstraints.normalizeConnectionName(name);
        if (!channelName.equals(value)) {
            channelName = value;
            markDirty();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            markDirty();
        }
    }

    public ChannelRedstoneMode getRedstoneMode() {
        return redstoneMode;
    }

    void setRedstoneMode(ChannelRedstoneMode mode) {
        if (redstoneMode != mode) {
            redstoneMode = mode;
            markDirty();
        }
    }

    public DistributionStrategy getStrategy() {
        return strategy;
    }

    void setStrategy(DistributionStrategy s) {
        var v = (s == null) ? DistributionStrategyRegistry.SEQUENTIAL : s;
        if (this.strategy != v) {
            this.strategy = v;
            markDirty();
        }
    }

    public ExtractionMode getExtractionMode() {
        return extractionMode;
    }

    void setExtractionMode(ExtractionMode mode) {
        var v = mode != null ? mode : ExtractionMode.SEQUENTIAL;
        if (this.extractionMode != v) {
            this.extractionMode = v;
            markDirty();
        }
    }

    public long getPriority() {
        return priority;
    }

    void setPriority(long p) {
        if (this.priority != p) {
            this.priority = p;
            onNumericDirty.accept(this);
        }
    }

    public long getKeepStock() {
        return keepStock;
    }

    void setKeepStock(long v) {
        if (this.keepStock != v) {
            this.keepStock = v;
            onNumericDirty.accept(this);
        }
    }

    private void markDirty() {
        if (onDirty != null) onDirty.accept(this);
    }

    public void setOnDirty(Consumer<ChannelSettings> onDirty) {
        this.onDirty = onDirty;
        this.onNumericDirty = onDirty == null ? c -> {
        } : onDirty;
    }

    /// 所有字段都是默认值就是空配置
    public boolean isDefault() {
        return strategy == DistributionStrategyRegistry.SEQUENTIAL
            && extractionMode == ExtractionMode.SEQUENTIAL && priority == 0 && keepStock == 0 && redstoneMode == ChannelRedstoneMode.IGNORE && enabled && channelName.isEmpty();
    }

    /// 以快照完整替换所有链接参数。
    void restoreSnapshot(ChannelSettings snapshot) {
        strategy = snapshot.strategy;
        extractionMode = snapshot.extractionMode;
        priority = snapshot.priority;
        keepStock = snapshot.keepStock;
        redstoneMode = snapshot.redstoneMode;
        enabled = snapshot.enabled;
        channelName = snapshot.channelName;
        markDirty();
    }
}
