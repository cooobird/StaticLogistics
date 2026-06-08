package com.coobird.staticlogistics.logic;

import com.coobird.staticlogistics.api.ITransferHandler;
import com.coobird.staticlogistics.api.LogisticsResource;
import com.coobird.staticlogistics.transfer.handler.ResourceAdapterHandler;
import com.coobird.staticlogistics.transfer.resource.EnergyResource;
import com.coobird.staticlogistics.transfer.resource.FluidResource;
import com.coobird.staticlogistics.transfer.resource.ItemResource;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 传输类型注册中心 —— 管理所有 {@link LogisticsResource} 实例。
 *
 * <p>原生类型（物品/流体/能量）在 {@link #init()} 中注册。
 * 外部模组通过 {@link #registerAdapter(LogisticsResource)} 注册。
 */
public class TransferRegistries {
    private static final Map<ResourceLocation, LogisticsResource<?>> RESOURCES = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ITransferHandler> HANDLERS = new LinkedHashMap<>();
    private static int generation = 0;

    public static void init() {
        registerAdapter(new ItemResource());
        registerAdapter(new FluidResource());
        registerAdapter(new EnergyResource());
    }

    /**
     * 注册一个 {@link LogisticsResource} 适配器。
     * <p>自动创建 {@link ResourceAdapterHandler} 并注册到处理器映射。
     * 如果已存在同 ID 的资源，允许覆盖（方便调试和动态加载）。
     *
     * @param <C>     资源句柄类型
     * @param adapter 适配器实例（同时作为类型元数据和传输逻辑）
     */
    public static <C> void registerAdapter(LogisticsResource<C> adapter) {
        ResourceLocation id = adapter.typeId();
        if (RESOURCES.containsKey(id)) {
            RESOURCES.remove(id);
            HANDLERS.remove(id);
        }
        RESOURCES.put(id, adapter);
        HANDLERS.put(id, new ResourceAdapterHandler<>(adapter));
        generation++;
    }

    /**
     * 按 ID 获取已注册的资源。
     */
    @Nullable
    public static LogisticsResource<?> get(ResourceLocation id) {
        return RESOURCES.get(id);
    }

    /**
     * 获取所有已注册的资源。
     */
    public static Collection<LogisticsResource<?>> getAllActive() {
        return RESOURCES.values();
    }

    /**
     * 获取指定资源对应的传输处理器。
     */
    @Nullable
    public static ITransferHandler getHandler(LogisticsResource<?> resource) {
        return HANDLERS.get(resource.typeId());
    }

    public static int generation() {
        return generation;
    }
}
