package com.coobird.staticlogistics.logistics;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.blueprint.BlueprintData;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

public class SLDataComponents {
    public static final int MAX_STORED_NODES = 256;

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, StaticLogistics.MODID);


    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SELECTED_GROUP =
        register("selected_group", builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    /**
     * 稳定分组身份；SELECTED_GROUP 继续保存显示名以兼容旧物品。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GroupKey>> SELECTED_GROUP_KEY =
        register("selected_group_key", builder -> builder.persistent(GroupKey.CODEC).networkSynchronized(GroupKey.STREAM_CODEC));

    /**
     * 配置器当前聚焦的单条连接；缺少该组件表示预览整个分组。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ConnectionKey>> SELECTED_CONNECTION_KEY =
        register("selected_connection_key", builder -> builder
            .persistent(ConnectionKey.CODEC)
            .networkSynchronized(ConnectionKey.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SELECTED_TYPES_MASK =
        register("selected_types_mask", builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<ResourceLocation>>> SELECTED_TYPES =
        register("selected_types", builder -> builder
            .persistent(Codec.list(ResourceLocation.CODEC))
            .networkSynchronized(ByteBufCodecs.collection(ArrayList::new, ResourceLocation.STREAM_CODEC)));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TOOL_MODE =
        register("tool_mode", builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    private static final Codec<List<LogisticsNode>> STORED_NODES_CODEC =
        Codec.list(LogisticsNode.CODEC).validate(nodes -> nodes.size() <= MAX_STORED_NODES
            ? DataResult.success(List.copyOf(nodes))
            : DataResult.error(() -> "Stored node count exceeds maximum " + MAX_STORED_NODES));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<LogisticsNode>> STORED_NODES_STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public List<LogisticsNode> decode(RegistryFriendlyByteBuf buffer) {
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
            public void encode(RegistryFriendlyByteBuf buffer, List<LogisticsNode> nodes) {
                if (nodes.size() > MAX_STORED_NODES) {
                    throw new EncoderException(
                        "Stored node count exceeds maximum " + MAX_STORED_NODES);
                }
                buffer.writeVarInt(nodes.size());
                nodes.forEach(node -> LogisticsNode.STREAM_CODEC.encode(buffer, node));
            }
        };

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<LogisticsNode>>> STORED_NODES =
        register("stored_nodes", builder -> builder
            .persistent(STORED_NODES_CODEC)
            .networkSynchronized(STORED_NODES_STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> STORED_MODE =
        register("stored_mode", builder -> builder
            .persistent(Codec.INT)
            .networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FilterData>> FILTER_DATA =
        register("filter_data", builder -> builder.persistent(FilterData.CODEC).networkSynchronized(FilterData.STREAM_CODEC));

    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(String name, UnaryOperator<DataComponentType.Builder<T>> builder) {
        return DATA_COMPONENT_TYPES.register(name, () -> builder.apply(DataComponentType.builder()).build());
    }

    /**
     * 仅保留旧物品组件的解码能力；不再把任意方块实体 NBT 注入新方块。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CustomData>> LEGACY_STORED_BE_NBT = DATA_COMPONENT_TYPES.register("stored_block_entity",
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
