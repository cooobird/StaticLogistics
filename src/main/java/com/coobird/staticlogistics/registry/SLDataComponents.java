package com.coobird.staticlogistics.registry;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.filter.data.FilterData;
import com.coobird.staticlogistics.item.BlueprintData;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.component.CustomData;
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

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CustomData>> STORED_BE_NBT = DATA_COMPONENT_TYPES.register("stored_block_entity",
        () -> DataComponentType.<CustomData>builder().persistent(CustomData.CODEC).build());

    // 存节点的玩家 UUID，链接时校验防止别人捡到工具冒用
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> STORED_NODES_OWNER =
        register("stored_nodes_owner", builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    // 物流蓝图数据
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlueprintData>> BLUEPRINT_DATA =
        register("blueprint_data", builder -> builder.persistent(BlueprintData.CODEC).networkSynchronized(BlueprintData.STREAM_CODEC));

    // 蓝图锚点选择（左键记录的坐标）
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> BLUEPRINT_ANCHOR =
        register("blueprint_anchor", builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    // 蓝图粘贴预览锚点（贴之前先预览位置）
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> BLUEPRINT_PREVIEW_ANCHOR =
        register("blueprint_preview_anchor", builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    // 蓝图预览旋转（0/1/2/3 = 0°/90°/180°/270° 绕Y轴）
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> BLUEPRINT_PREVIEW_ROTATION =
        register("blueprint_preview_rotation", builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
}