package com.coobird.staticlogistics.registry;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.filter.FilterData;
import com.coobird.staticlogistics.item.blueprint.BlueprintData;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import org.mesdag.portlib.component.PortDataComponentType;
import org.mesdag.portlib.network.codec.PortByteBufCodecs;
import org.mesdag.portlib.registries.PortDataComponentRegistration;
import org.mesdag.portlib.registries.PortRegisterHandler;
import org.mesdag.portlib.registries.PortRegistryEntry;

import java.util.List;

/**
 * 数据组件注册 —— 使用 PortLib 的 PortDataComponentRegistration
 */
public class SLDataComponents {
    public static final PortDataComponentRegistration DATA_COMPONENT_TYPES = PortRegisterHandler.dataComponent(StaticLogistics.MODID);

    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<String>> SELECTED_GROUP =
        DATA_COMPONENT_TYPES.builder("selected_group", b -> b.persistent(Codec.STRING).networkSynchronized(PortByteBufCodecs.STRING_UTF8));

    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<Integer>> SELECTED_TYPES_MASK =
        DATA_COMPONENT_TYPES.builder("selected_types_mask", b -> b.persistent(Codec.INT).networkSynchronized(PortByteBufCodecs.VAR_INT));

    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<Integer>> TOOL_MODE =
        DATA_COMPONENT_TYPES.builder("tool_mode", b -> b.persistent(Codec.INT).networkSynchronized(PortByteBufCodecs.VAR_INT));

    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<List<LogisticsNode>>> STORED_NODES =
        DATA_COMPONENT_TYPES.builder("stored_nodes", b -> b.persistent(Codec.list(LogisticsNode.CODEC))
            .networkSynchronized(PortByteBufCodecs.collection(java.util.ArrayList::new, LogisticsNode.STREAM_CODEC)));

    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<Integer>> STORED_MODE =
        DATA_COMPONENT_TYPES.builder("stored_mode", b -> b.persistent(Codec.INT).networkSynchronized(PortByteBufCodecs.VAR_INT));

    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<FilterData>> FILTER_DATA =
        DATA_COMPONENT_TYPES.builder("filter_data", b -> b.persistent(FilterData.CODEC).networkSynchronized(FilterData.STREAM_CODEC));

    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<CompoundTag>> STORED_BE_NBT =
        DATA_COMPONENT_TYPES.builder("stored_block_entity", b -> b.persistent(CompoundTag.CODEC));

    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<String>> STORED_NODES_OWNER =
        DATA_COMPONENT_TYPES.builder("stored_nodes_owner", b -> b.persistent(Codec.STRING).networkSynchronized(PortByteBufCodecs.STRING_UTF8));

    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<BlueprintData>> BLUEPRINT_DATA =
        DATA_COMPONENT_TYPES.builder("blueprint_data", b -> b.persistent(BlueprintData.CODEC).networkSynchronized(BlueprintData.STREAM_CODEC));

    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<String>> BLUEPRINT_ANCHOR =
        DATA_COMPONENT_TYPES.builder("blueprint_anchor", b -> b.persistent(Codec.STRING).networkSynchronized(PortByteBufCodecs.STRING_UTF8));

    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<String>> BLUEPRINT_PREVIEW_ANCHOR =
        DATA_COMPONENT_TYPES.builder("blueprint_preview_anchor", b -> b.persistent(Codec.STRING).networkSynchronized(PortByteBufCodecs.STRING_UTF8));

    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<Integer>> BLUEPRINT_PREVIEW_ROTATION =
        DATA_COMPONENT_TYPES.builder("blueprint_preview_rotation", b -> b.persistent(Codec.INT).networkSynchronized(PortByteBufCodecs.VAR_INT));

    public static void init() {
    }
}
