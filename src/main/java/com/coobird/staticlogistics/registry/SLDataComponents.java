package com.coobird.staticlogistics.registry;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.filter.data.FilterData;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.function.UnaryOperator;

public class SLDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Staticlogistics.MODID);


    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SELECTED_GROUP =
        register("selected_group", builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SELECTED_TYPES_MASK =
        register("selected_types_mask", builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> PRIORITY =
        register("priority", builder -> builder.persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TOOL_MODE =
        register("tool_mode", builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<LogisticsNode>>> STORED_NODES =
        register("stored_nodes", builder -> builder
            .persistent(Codec.list(LogisticsNode.CODEC))
            .networkSynchronized(ByteBufCodecs.collection(java.util.ArrayList::new, LogisticsNode.STREAM_CODEC)));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> STORED_MODE =
        register("stored_mode", builder -> builder
            .persistent(Codec.INT)
            .networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FilterData>> FILTER_DATA =
        register("filter_data", builder -> builder.persistent(FilterData.CODEC).networkSynchronized(FilterData.STREAM_CODEC));

    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(String name, UnaryOperator<DataComponentType.Builder<T>> builder) {
        return DATA_COMPONENT_TYPES.register(name, () -> builder.apply(DataComponentType.builder()).build());
    }
}