package com.coobird.staticlogistics.api.transfer;

import com.coobird.staticlogistics.api.LogisticsNode;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** 传给资源适配器的稳定、只读传输请求。 */
public record TransferRequest(
    ResourceLocation typeId,
    LogisticsNode sourceNode,
    long maxAmount,
    boolean pullMode,
    long gameTime
) {
    public TransferRequest {
        Objects.requireNonNull(typeId, "Resource type id must not be null");
        Objects.requireNonNull(sourceNode, "Source node must not be null");
        if (maxAmount <= 0L) throw new IllegalArgumentException("Transfer amount must be positive");
    }

    public TransferRequest withMaxAmount(long amount) {
        return new TransferRequest(typeId, sourceNode, amount, pullMode, gameTime);
    }
}
