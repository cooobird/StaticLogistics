package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.ExtractionMode;
import com.coobird.staticlogistics.transfer.DistributionStrategyRegistry;

import java.util.function.Consumer;

/**
 * 链接通道配置 —— 管理输入/输出频道（1-16）、分发策略、提取模式和优先级。
 */
public class LinkConfig {
    public static final int MIN_CHANNEL = 1;
    public static final int MAX_CHANNEL = 16;
    /** 未指定频道；与任意有效频道匹配。角色是否启用由面的输入/输出开关决定。 */
    public static final int UNSPECIFIED_CHANNEL = 0;

    private Consumer<LinkConfig> onDirty = (c) -> {
    };

    private int inputChannel = 0;
    private int outputChannel = 0;
    private DistributionStrategy strategy = DistributionStrategyRegistry.SEQUENTIAL;
    private ExtractionMode extractionMode = ExtractionMode.SEQUENTIAL;
    private int priority = 0;
    private int keepStock = 0;

    public int getInputChannel() {
        return inputChannel;
    }

    void setInputChannel(int ch) {
        int clamped = clampChannel(ch);
        if (this.inputChannel != clamped) {
            this.inputChannel = clamped;
            markDirty();
        }
    }

    public int getOutputChannel() {
        return outputChannel;
    }

    void setOutputChannel(int ch) {
        int clamped = clampChannel(ch);
        if (this.outputChannel != clamped) {
            this.outputChannel = clamped;
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
        return inputChannel == 0 && outputChannel == 0 && strategy == DistributionStrategyRegistry.SEQUENTIAL
            && extractionMode == ExtractionMode.SEQUENTIAL && priority == 0 && keepStock == 0;
    }

    /** 频道值钳位到兼容范围：0=未指定/通配，1-16=明确频道。 */
    public static int clampChannel(int value) {
        if (value == UNSPECIFIED_CHANNEL) return UNSPECIFIED_CHANNEL;
        return Math.max(MIN_CHANNEL, Math.min(MAX_CHANNEL, value));
    }

    /** 两端任一方未指定频道时通配，否则要求频道完全一致。 */
    public static boolean channelsMatch(int outputChannel, int inputChannel) {
        return outputChannel == UNSPECIFIED_CHANNEL
            || inputChannel == UNSPECIFIED_CHANNEL
            || outputChannel == inputChannel;
    }

    /** 以快照完整替换所有链接参数。 */
    void restoreSnapshot(LinkConfig snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("Link snapshot must not be null");
        inputChannel = snapshot.inputChannel;
        outputChannel = snapshot.outputChannel;
        strategy = snapshot.strategy;
        extractionMode = snapshot.extractionMode;
        priority = snapshot.priority;
        keepStock = snapshot.keepStock;
        markDirty();
    }
}
