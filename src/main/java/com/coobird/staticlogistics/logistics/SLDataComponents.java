package com.coobird.staticlogistics.logistics;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.blueprint.BlueprintData;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.selection.NodeSelectionPolicy;
import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

public class SLDataComponents {

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, StaticLogistics.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SELECTED_GROUP =
        register("selected_group", builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GroupKey>> SELECTED_GROUP_KEY =
        register("selected_group_key", builder -> builder.persistent(GroupKey.CODEC).networkSynchronized(GroupKey.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ConnectionKey>> SELECTED_CONNECTION_KEY =
        register("selected_connection_key", builder -> builder
            .persistent(ConnectionKey.CODEC)
            .networkSynchronized(ConnectionKey.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<ResourceLocation>>> SELECTED_TYPES =
        register("selected_types", builder -> builder
            .persistent(Codec.list(ResourceLocation.CODEC))
            .networkSynchronized(ByteBufCodecs.collection(ArrayList::new, ResourceLocation.STREAM_CODEC)));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> BLUEPRINT_SOURCE_DIMENSION =
        register("blueprint_source_dimension", builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> BLUEPRINT_PLACING =
        register("blueprint_placing", builder -> builder.networkSynchronized(ByteBufCodecs.BOOL));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> BLUEPRINT_PREVIEW_DIMENSION =
        register("blueprint_preview_dimension", builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> BLUEPRINT_GROUP =
        register("blueprint_group", builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GroupKey>> BLUEPRINT_GROUP_KEY =
        register("blueprint_group_key", builder -> builder.persistent(GroupKey.CODEC).networkSynchronized(GroupKey.STREAM_CODEC));

    /// 服务器运行期间的工具实例身份，只同步、不写入存档，复制物品不能继承请求身份。
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> TOOL_SESSION =
        register("tool_session", builder -> builder.networkSynchronized(UUIDUtil.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TOOL_MODE =
        register("tool_mode", builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SELECTED_CHANNEL =
        register("selected_channel", builder -> builder.persistent(Codec.intRange(0, LogisticsNode.MAX_CHANNELS - 1)).networkSynchronized(ByteBufCodecs.VAR_INT));

    /// 完整选择仅在服务端物品存档中保存；客户端通过独立分页获取。
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<LogisticsNode>>> STORED_NODES =
        register("stored_nodes", builder -> builder.persistent(NodeSelectionPolicy.CODEC)
            .networkSynchronized(new StreamCodec<>() {
                @Override
                public List<LogisticsNode> decode(RegistryFriendlyByteBuf buffer) {
                    return List.of();
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, List<LogisticsNode> nodes) {
                }
            }));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> STORED_SELECTION_ID =
        register("stored_selection_id", builder -> builder
            .persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> STORED_MODE =
        register("stored_mode", builder -> builder
            .persistent(Codec.INT)
            .networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FilterData>> FILTER_DATA =
        register("filter_data", builder -> builder.persistent(FilterData.CODEC));

    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(String name, UnaryOperator<DataComponentType.Builder<T>> builder) {
        return DATA_COMPONENT_TYPES.register(name, () -> builder.apply(DataComponentType.builder()).build());
    }

    /// 存节点的玩家 UUID，链接时校验防止别人捡到工具冒用
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> STORED_NODES_OWNER =
        register("stored_nodes_owner", builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    /// 物流蓝图数据
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlueprintData>> BLUEPRINT_DATA =
        register("blueprint_data", builder -> builder.persistent(BlueprintData.CODEC).networkSynchronized(BlueprintData.STREAM_CODEC));

    /// 蓝图锚点选择（左键记录的坐标）
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> BLUEPRINT_ANCHOR =
        register("blueprint_anchor", builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    /// 蓝图粘贴预览锚点（贴之前先预览位置）
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> BLUEPRINT_PREVIEW_ANCHOR =
        register("blueprint_preview_anchor", builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    /// 蓝图预览旋转（0/1/2/3 = 0°/90°/180°/270° 绕Y轴）
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> BLUEPRINT_PREVIEW_ROTATION =
        register("blueprint_preview_rotation", builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
}
