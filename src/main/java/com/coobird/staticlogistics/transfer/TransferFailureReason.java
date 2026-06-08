package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.StaticLogistics;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 传输失败原因 —— 注册表模式，支持第三方模组扩展。
 *
 * <p>内置原因在静态初始化块中注册。第三方模组通过 {@link #register(ResourceLocation, String)} 注册自定义原因。
 */
public record TransferFailureReason(ResourceLocation id, String translationKey) {

    // 内置原因
    public static final TransferFailureReason NO_DIMENSION_UPGRADE = register(StaticLogistics.asResource("no_dim"), "failure.staticlogistics.no_dim");
    public static final TransferFailureReason OUT_OF_RANGE = register(StaticLogistics.asResource("out_of_range"), "failure.staticlogistics.out_of_range");
    public static final TransferFailureReason CHUNK_UNLOADED = register(StaticLogistics.asResource("chunk_unloaded"), "failure.staticlogistics.chunk_unloaded");
    public static final TransferFailureReason CAPABILITY_NULL = register(StaticLogistics.asResource("no_capability"), "failure.staticlogistics.no_capability");
    public static final TransferFailureReason EVENT_CANCELLED = register(StaticLogistics.asResource("event_cancelled"), "failure.staticlogistics.event_cancelled");

    // 注册表
    private static final Map<ResourceLocation, TransferFailureReason> REGISTRY = new LinkedHashMap<>();

    public static TransferFailureReason register(ResourceLocation id, String translationKey) {
        TransferFailureReason reason = new TransferFailureReason(id, translationKey);
        if (REGISTRY != null) {
            REGISTRY.put(id, reason);
        }
        return reason;
    }

    public static TransferFailureReason byId(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    public static TransferFailureReason byId(String id) {
        return REGISTRY.get(ResourceLocation.tryParse(id));
    }

    public static Iterable<TransferFailureReason> all() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }
}
