package com.coobird.staticlogistics.logistics;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.blueprint.BlueprintData;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.selection.NodeSelectionPolicy;
import com.coobird.staticlogistics.network.BoundedNetworkCodecs;
import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
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
import java.util.UUID;
import java.util.function.UnaryOperator;

public class SLDataComponents {

    public static final PortDataComponentRegistration DATA_COMPONENT_TYPES = PortRegisterHandler.dataComponent(StaticLogistics.MODID);

    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<String>> SELECTED_GROUP =
        register("selected_group", builder -> builder.persistent(Codec.STRING).networkSynchronized(PortByteBufCodecs.STRING_UTF8));

    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<GroupKey>> SELECTED_GROUP_KEY =
        register("selected_group_key", builder -> builder.persistent(GroupKey.CODEC).networkSynchronized(GroupKey.STREAM_CODEC));

    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<ConnectionKey>> SELECTED_CONNECTION_KEY =
        register("selected_connection_key", builder -> builder
            .persistent(ConnectionKey.CODEC)
            .networkSynchronized(ConnectionKey.STREAM_CODEC));

    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<List<ResourceLocation>>> SELECTED_TYPES =
        register("selected_types", builder -> builder
            .persistent(Codec.list(ResourceLocation.CODEC))
            .networkSynchronized(PortByteBufCodecs.collection(ArrayList::new, ResourceLocation.STREAM_CODEC)));

    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<String>> BLUEPRINT_SOURCE_DIMENSION =
        register("blueprint_source_dimension", builder -> builder.persistent(Codec.STRING).networkSynchronized(PortByteBufCodecs.STRING_UTF8));
    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<Boolean>> BLUEPRINT_PLACING =
        register("blueprint_placing", builder -> builder.persistent(Codec.BOOL).networkSynchronized(PortByteBufCodecs.BOOL));
    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<String>> BLUEPRINT_PREVIEW_DIMENSION =
        register("blueprint_preview_dimension", builder -> builder.persistent(Codec.STRING).networkSynchronized(PortByteBufCodecs.STRING_UTF8));
    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<String>> BLUEPRINT_GROUP =
        register("blueprint_group", builder -> builder.persistent(Codec.STRING).networkSynchronized(PortByteBufCodecs.STRING_UTF8));
    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<GroupKey>> BLUEPRINT_GROUP_KEY =
        register("blueprint_group_key", builder -> builder.persistent(GroupKey.CODEC).networkSynchronized(GroupKey.STREAM_CODEC));

    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<UUID>> TOOL_SESSION =
        register("tool_session", builder -> builder.persistent(UUIDUtil.CODEC).networkSynchronized(BoundedNetworkCodecs.UUID));

    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<Integer>> TOOL_MODE =
        register("tool_mode", builder -> builder.persistent(Codec.INT).networkSynchronized(PortByteBufCodecs.VAR_INT));
    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<Integer>> SELECTED_CHANNEL =
        register("selected_channel", builder -> builder.persistent(Codec.intRange(0, LogisticsNode.MAX_CHANNELS - 1)).networkSynchronized(PortByteBufCodecs.VAR_INT));

    /// 完整选择仅在服务端物品存档中保存；客户端通过独立分页获取。
    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<List<LogisticsNode>>> STORED_NODES =
        register("stored_nodes", builder -> builder.persistent(NodeSelectionPolicy.CODEC)
            .networkSynchronized(new PortStreamCodec<>() {
                @Override
                public List<LogisticsNode> decode(PortRegistryFriendlyByteBuf buffer) {
                    return List.of();
                }

                @Override
                public void encode(PortRegistryFriendlyByteBuf buffer, List<LogisticsNode> nodes) {
                }
            }));
    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<UUID>> STORED_SELECTION_ID =
        register("stored_selection_id", builder -> builder
            .persistent(UUIDUtil.CODEC).networkSynchronized(BoundedNetworkCodecs.UUID));

    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<Integer>> STORED_MODE =
        register("stored_mode", builder -> builder
            .persistent(Codec.INT)
            .networkSynchronized(PortByteBufCodecs.VAR_INT));

    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<FilterData>> FILTER_DATA =
        register("filter_data", builder -> builder.persistent(FilterData.CODEC));

    private static <T> PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<T>> register(String name, UnaryOperator<PortDataComponentType.Builder<T>> builder) {
        return DATA_COMPONENT_TYPES.builder(name, builder::apply);
    }

    /// 存节点的玩家 UUID，链接时校验防止别人捡到工具冒用
    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<String>> STORED_NODES_OWNER =
        register("stored_nodes_owner", builder -> builder.persistent(Codec.STRING).networkSynchronized(PortByteBufCodecs.STRING_UTF8));

    /// 物流蓝图数据
    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<BlueprintData>> BLUEPRINT_DATA =
        register("blueprint_data", builder -> builder.persistent(BlueprintData.CODEC).networkSynchronized(BlueprintData.STREAM_CODEC));

    /// 蓝图锚点选择（左键记录的坐标）
    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<String>> BLUEPRINT_ANCHOR =
        register("blueprint_anchor", builder -> builder.persistent(Codec.STRING).networkSynchronized(PortByteBufCodecs.STRING_UTF8));

    /// 蓝图粘贴预览锚点（贴之前先预览位置）
    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<String>> BLUEPRINT_PREVIEW_ANCHOR =
        register("blueprint_preview_anchor", builder -> builder.persistent(Codec.STRING).networkSynchronized(PortByteBufCodecs.STRING_UTF8));

    /// 蓝图预览旋转（0/1/2/3 = 0°/90°/180°/270° 绕Y轴）
    public static final PortRegistryEntry<PortDataComponentType<?>, PortDataComponentType<Integer>> BLUEPRINT_PREVIEW_ROTATION =
        register("blueprint_preview_rotation", builder -> builder.persistent(Codec.INT).networkSynchronized(PortByteBufCodecs.VAR_INT));

    public static void init() {
    }
}
