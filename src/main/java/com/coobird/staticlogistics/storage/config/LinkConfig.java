package com.coobird.staticlogistics.storage.config;

import com.coobird.staticlogistics.api.type.DistributionStrategy;

import java.util.function.Consumer;

public class LinkConfig {
    public static final int MIN_CHANNEL = 1;
    public static final int MAX_CHANNEL = 16;
    public static final int DISABLED_CHANNEL = 0;

    private Consumer<LinkConfig> onDirty = (c) -> {
    };

    private int inputChannel = 0;
    private int outputChannel = 0;
    private DistributionStrategy strategy = DistributionStrategy.SEQUENTIAL;
    private int priority = 0;

    public int getInputChannel() {
        return inputChannel;
    }

    public void setInputChannel(int ch) {
        this.inputChannel = clampChannel(ch);
        markDirty();
    }

    public int getOutputChannel() {
        return outputChannel;
    }

    public void setOutputChannel(int ch) {
        this.outputChannel = clampChannel(ch);
        markDirty();
    }

    public DistributionStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(DistributionStrategy s) {
        this.strategy = s;
        markDirty();
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int p) {
        this.priority = p;
        markDirty();
    }

    private void markDirty() {
        if (onDirty != null) onDirty.accept(this);
    }

    public void setOnDirty(Consumer<LinkConfig> onDirty) {
        this.onDirty = onDirty;
    }

    public boolean isDefault() {
        return inputChannel == 0 && outputChannel == 0 && strategy == DistributionStrategy.SEQUENTIAL && priority == 0;
    }

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

    public void disableInputChannel() {
        setInputChannel(DISABLED_CHANNEL);
    }

    public void disableOutputChannel() {
        setOutputChannel(DISABLED_CHANNEL);
    }
}