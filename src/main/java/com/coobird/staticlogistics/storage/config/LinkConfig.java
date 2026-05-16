package com.coobird.staticlogistics.storage.config;

import com.coobird.staticlogistics.api.type.DistributionStrategy;

import java.util.function.Consumer;

public class LinkConfig {
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
        this.inputChannel = ch;
        markDirty();
    }

    public int getOutputChannel() {
        return outputChannel;
    }

    public void setOutputChannel(int ch) {
        this.outputChannel = ch;
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
}