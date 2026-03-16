package com.coobird.staticlogistics.common.init;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.core.NodeEntry;
import com.coobird.staticlogistics.transfer.TransferType;
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

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TransferType>> SELECTED_TYPE =
        register("selected_type", builder -> builder.persistent(TransferType.CODEC).networkSynchronized(TransferType.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> PRIORITY =
        register("priority", builder -> builder.persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TOOL_MODE =
        register("tool_mode", builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<NodeEntry>>> STORED_NODES =
        register("stored_nodes", builder -> builder
            .persistent(Codec.list(NodeEntry.CODEC))
            .networkSynchronized(ByteBufCodecs.collection(java.util.ArrayList::new, NodeEntry.STREAM_CODEC)));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> STORED_MODE =
        register("stored_mode", builder -> builder
            .persistent(Codec.INT)
            .networkSynchronized(ByteBufCodecs.VAR_INT));

    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(String name, UnaryOperator<DataComponentType.Builder<T>> builder) {
        return DATA_COMPONENT_TYPES.register(name, () -> builder.apply(DataComponentType.builder()).build());
    }
}