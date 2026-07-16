package com.coobird.staticlogistics.logistics;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.blueprint.BlueprintData;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.mesdag.portlib.component.PortDataComponentType;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortByteBufCodecs;
import org.mesdag.portlib.network.codec.PortStreamCodec;
import org.mesdag.portlib.registries.PortDataComponentRegistration;
import org.mesdag.portlib.registries.PortRegisterHandler;
import org.mesdag.portlib.registries.PortRegistryEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据组件注册 —— 使用 PortLib 的 PortDataComponentRegistration
 */
public class SLDataComponents {
    public static final int MAX_STORED_NODES = 256;
    public static final int MAX_SELECTED_TYPES = 64;

    public static final PortDataComponentRegistration DATA_COMPONENT_TYPES = PortRegisterHandler.dataComponent(StaticLogistics.MODID);

    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<String>> SELECTED_GROUP =
        DATA_COMPONENT_TYPES.builder("selected_group", b -> b.persistent(Codec.STRING).networkSynchronized(PortByteBufCodecs.STRING_UTF8));

    /**
     * 稳定分组身份；SELECTED_GROUP 继续保存显示名以兼容旧物品。
     */
    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<GroupKey>> SELECTED_GROUP_KEY =
        DATA_COMPONENT_TYPES.builder("selected_group_key", b -> b.persistent(GroupKey.CODEC)
            .networkSynchronized(GroupKey.STREAM_CODEC));

    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<Integer>> SELECTED_TYPES_MASK =
        DATA_COMPONENT_TYPES.builder("selected_types_mask", b -> b.persistent(Codec.INT).networkSynchronized(PortByteBufCodecs.VAR_INT));

    private static final Codec<List<ResourceLocation>> SELECTED_TYPES_CODEC =
        Codec.list(ResourceLocation.CODEC).flatXmap(
            SLDataComponents::validateSelectedTypes, SLDataComponents::validateSelectedTypes);

    private static final PortStreamCodec<PortRegistryFriendlyByteBuf, List<ResourceLocation>> SELECTED_TYPES_STREAM_CODEC =
        new PortStreamCodec<>() {
            @Override
            public List<ResourceLocation> decode(PortRegistryFriendlyByteBuf buffer) {
                int size = buffer.readVarInt();
                if (size < 0 || size > MAX_SELECTED_TYPES) {
                    throw new DecoderException("Invalid selected transfer type count: " + size);
                }
                List<ResourceLocation> ids = new ArrayList<>(size);
                for (int index = 0; index < size; index++) ids.add(buffer.readResourceLocation());
                return List.copyOf(ids);
            }

            @Override
            public void encode(PortRegistryFriendlyByteBuf buffer, List<ResourceLocation> ids) {
                if (ids.size() > MAX_SELECTED_TYPES) {
                    throw new EncoderException(
                        "Selected transfer type count exceeds maximum " + MAX_SELECTED_TYPES);
                }
                buffer.writeVarInt(ids.size());
                ids.forEach(buffer::writeResourceLocation);
            }
        };

    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<List<ResourceLocation>>> SELECTED_TYPES =
        DATA_COMPONENT_TYPES.builder("selected_types", b -> b.persistent(SELECTED_TYPES_CODEC)
            .networkSynchronized(SELECTED_TYPES_STREAM_CODEC));

    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<Integer>> TOOL_MODE =
        DATA_COMPONENT_TYPES.builder("tool_mode", b -> b.persistent(Codec.INT).networkSynchronized(PortByteBufCodecs.VAR_INT));

    private static final Codec<List<LogisticsNode>> STORED_NODES_CODEC =
        Codec.list(LogisticsNode.CODEC).flatXmap(
            SLDataComponents::validateStoredNodes, SLDataComponents::validateStoredNodes);

    private static final PortStreamCodec<PortRegistryFriendlyByteBuf, List<LogisticsNode>> STORED_NODES_STREAM_CODEC =
        new PortStreamCodec<>() {
            @Override
            public List<LogisticsNode> decode(PortRegistryFriendlyByteBuf buffer) {
                int size = buffer.readVarInt();
                if (size < 0 || size > MAX_STORED_NODES) {
                    throw new DecoderException("Invalid stored node count: " + size);
                }
                List<LogisticsNode> nodes = new ArrayList<>(size);
                for (int index = 0; index < size; index++) {
                    nodes.add(LogisticsNode.STREAM_CODEC.decode(buffer));
                }
                return List.copyOf(nodes);
            }

            @Override
            public void encode(PortRegistryFriendlyByteBuf buffer, List<LogisticsNode> nodes) {
                if (nodes.size() > MAX_STORED_NODES) {
                    throw new EncoderException("Stored node count exceeds maximum " + MAX_STORED_NODES);
                }
                buffer.writeVarInt(nodes.size());
                nodes.forEach(node -> LogisticsNode.STREAM_CODEC.encode(buffer, node));
            }
        };

    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<List<LogisticsNode>>> STORED_NODES =
        DATA_COMPONENT_TYPES.builder("stored_nodes", b -> b.persistent(STORED_NODES_CODEC)
            .networkSynchronized(STORED_NODES_STREAM_CODEC));

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

    private static DataResult<List<ResourceLocation>> validateSelectedTypes(List<ResourceLocation> ids) {
        return ids.size() <= MAX_SELECTED_TYPES
            ? DataResult.success(List.copyOf(ids))
            : DataResult.error(() ->
            "Selected transfer type count exceeds maximum " + MAX_SELECTED_TYPES);
    }

    private static DataResult<List<LogisticsNode>> validateStoredNodes(List<LogisticsNode> nodes) {
        return nodes.size() <= MAX_STORED_NODES
            ? DataResult.success(List.copyOf(nodes))
            : DataResult.error(() -> "Stored node count exceeds maximum " + MAX_STORED_NODES);
    }
}
