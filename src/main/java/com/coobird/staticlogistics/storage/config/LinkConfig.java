package com.coobird.staticlogistics.storage.config;

import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.ExtractionMode;

import java.util.function.Consumer;

/**
 * 链接通道配置 —— 管理输入/输出频道（1-16）、分发策略、提取模式和优先级。
 */
public class LinkConfig {
    public static final int MIN_CHANNEL = 1;
    public static final int MAX_CHANNEL = 16;
    public static final int DISABLED_CHANNEL = 0;

    private Consumer<LinkConfig> onDirty = (c) -> {
    };

    private int inputChannel = 0;
    private int outputChannel = 0;
    private DistributionStrategy strategy = DistributionStrategy.SEQUENTIAL;
    private ExtractionMode extractionMode = ExtractionMode.SEQUENTIAL;
    private int priority = 0;
    private int keepStock = 0;

    public int getInputChannel() {
        return inputChannel;
    }

    public void setInputChannel(int ch) {
        int clamped = clampChannel(ch);
        if (this.inputChannel != clamped) {
            this.inputChannel = clamped;
            markDirty();
        }
    }

    public int getOutputChannel() {
        return outputChannel;
    }

    public void setOutputChannel(int ch) {
        int clamped = clampChannel(ch);
        if (this.outputChannel != clamped) {
            this.outputChannel = clamped;
            markDirty();
        }
    }

    public DistributionStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(DistributionStrategy s) {
        var v = (s == null) ? DistributionStrategy.SEQUENTIAL : s;
        if (this.strategy != v) {
            this.strategy = v;
            markDirty();
        }
    }

    public ExtractionMode getExtractionMode() {
        return extractionMode;
    }

    public void setExtractionMode(ExtractionMode mode) {
        var v = mode != null ? mode : ExtractionMode.SEQUENTIAL;
        if (this.extractionMode != v) {
            this.extractionMode = v;
            markDirty();
        }
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int p) {
        if (this.priority != p) {
            this.priority = p;
            markDirty();
        }
    }

    public int getKeepStock() {
        return keepStock;
    }

    public void setKeepStock(int val) {
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
        return inputChannel == 0 && outputChannel == 0 && strategy == DistributionStrategy.SEQUENTIAL
            && extractionMode == ExtractionMode.SEQUENTIAL && priority == 0 && keepStock == 0;
    }

    /**
     * 频道值钳位到有效范围：0=禁用, 1-16=有效频道
     */
    public static int clampChannel(int value) {
        if (value == DISABLED_CHANNEL) return DISABLED_CHANNEL;
        return Math.max(MIN_CHANNEL, Math.min(MAX_CHANNEL, value));
    }

    public boolean isInputEnabled() {
        return inputChannel != DISABLED_CHANNEL;
    }

    public boolean isOutputEnabled() {
        return outputChannel != DISABLED_CHANNEL;
    }
}