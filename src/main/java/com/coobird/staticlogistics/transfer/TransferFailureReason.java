package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.StaticLogistics;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// 传输失败原因 —— 注册表模式，支持第三方模组扩展。
public record TransferFailureReason(ResourceLocation id, String translationKey) {

    private static volatile Map<ResourceLocation, TransferFailureReason> registry = Map.of();
    private static volatile boolean frozen;

    public static final TransferFailureReason NO_DIMENSION_UPGRADE = register(StaticLogistics.asResource("no_dim"), "failure.staticlogistics.no_dim");
    public static final TransferFailureReason OUT_OF_RANGE = register(StaticLogistics.asResource("out_of_range"), "failure.staticlogistics.out_of_range");
    public static final TransferFailureReason CHUNK_UNLOADED = register(StaticLogistics.asResource("chunk_unloaded"), "failure.staticlogistics.chunk_unloaded");
    public static final TransferFailureReason CAPABILITY_NULL = register(StaticLogistics.asResource("no_capability"), "failure.staticlogistics.no_capability");
    public static final TransferFailureReason TARGET_REJECTED = register(StaticLogistics.asResource("target_rejected"), "failure.staticlogistics.target_rejected");
    public static final TransferFailureReason TARGET_POLICY_REJECTED = register(StaticLogistics.asResource("target_policy_rejected"), "failure.staticlogistics.target_policy_rejected");
    public static final TransferFailureReason EVENT_CANCELLED = register(StaticLogistics.asResource("event_cancelled"), "failure.staticlogistics.event_cancelled");
    public static final TransferFailureReason SIMULATION_FAILED = register(StaticLogistics.asResource("simulation_failed"), "failure.staticlogistics.simulation_failed");
    public static final TransferFailureReason SOURCE_COMMIT_FAILED = register(StaticLogistics.asResource("source_commit_failed"), "failure.staticlogistics.source_commit_failed");
    public static final TransferFailureReason COMMIT_STATE_UNKNOWN = register(StaticLogistics.asResource("commit_state_unknown"), "failure.staticlogistics.commit_state_unknown");
    public static final TransferFailureReason ROLLBACK_FAILED = register(StaticLogistics.asResource("rollback_failed"), "failure.staticlogistics.rollback_failed");

    public static synchronized TransferFailureReason register(ResourceLocation id, String translationKey) {
        if (frozen) throw new IllegalStateException("Transfer failure reason registration is already frozen");
        TransferFailureReason existing = registry.get(id);
        if (existing != null) return existing;
        TransferFailureReason reason = new TransferFailureReason(id, translationKey);
        Map<ResourceLocation, TransferFailureReason> updated = new LinkedHashMap<>(registry);
        updated.put(id, reason);
        registry = Collections.unmodifiableMap(updated);
        return reason;
    }

    public static synchronized void freeze() {
        frozen = true;
    }

    public static TransferFailureReason byId(ResourceLocation id) {
        return registry.get(id);
    }

    public static TransferFailureReason byId(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        return parsed == null ? null : registry.get(parsed);
    }

    public static Iterable<TransferFailureReason> all() {
        return List.copyOf(registry.values());
    }
}
