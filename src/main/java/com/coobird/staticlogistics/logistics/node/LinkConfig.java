package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.ExtractionMode;
import com.coobird.staticlogistics.transfer.DistributionStrategyRegistry;

import java.util.function.Consumer;

/**
 * 链接传输配置，管理分发策略、提取模式、优先级和存量维持。
 */
public class LinkConfig {
    private Consumer<LinkConfig> onDirty = (c) -> {
    };

    private DistributionStrategy strategy = DistributionStrategyRegistry.SEQUENTIAL;
    private ExtractionMode extractionMode = ExtractionMode.SEQUENTIAL;
    private int priority = 0;
    private int keepStock = 0;

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

    public int getPriority() {
        return priority;
    }

    void setPriority(int p) {
        if (this.priority != p) {
            this.priority = p;
            markDirty();
        }
    }

    public int getKeepStock() {
        return keepStock;
    }

    void setKeepStock(int val) {
        int v = Math.max(0, val);
        if (this.keepStock != v) {
            this.keepStock = v;
            markDirty();
        }
    }

    private void markDirty() {
        if (onDirty != null) onDirty.accept(this);
    }

    public void setOnDirty(Consumer<LinkConfig> onDirty) {
        this.onDirty = onDirty;
    }

    /**
     * 所有字段都是默认值就是空配置
     */
    public boolean isDefault() {
        return strategy == DistributionStrategyRegistry.SEQUENTIAL
            && extractionMode == ExtractionMode.SEQUENTIAL && priority == 0 && keepStock == 0;
    }

    /**
     * 以快照完整替换所有链接参数。
     */
    void restoreSnapshot(LinkConfig snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("Link snapshot must not be null");
        strategy = snapshot.strategy;
        extractionMode = snapshot.extractionMode;
        priority = snapshot.priority;
        keepStock = snapshot.keepStock;
        markDirty();
    }
}
