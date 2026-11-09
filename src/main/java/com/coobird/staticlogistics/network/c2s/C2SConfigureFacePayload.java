package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.ExtractionMode;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.LogisticsUpgrade;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import com.coobird.staticlogistics.logistics.node.ChannelConfigurationEdit;
import com.coobird.staticlogistics.logistics.node.ChannelRedstoneMode;
import com.coobird.staticlogistics.logistics.node.NodeInteractionRules;
import com.coobird.staticlogistics.logistics.node.NodeMutationService;
import com.coobird.staticlogistics.logistics.selection.NodeSelectionPolicy;
import com.coobird.staticlogistics.network.BoundedNetworkCodecs;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import com.coobird.staticlogistics.network.TeamPacketSync;
import com.coobird.staticlogistics.network.s2c.S2CTopologyUpdatePayload;
import com.coobird.staticlogistics.transfer.DistributionStrategyRegistry;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

/// 单节点即时修改与批量确认共用的配置提交；服务端一次校验和提交整份修改。
public record C2SConfigureFacePayload(BlockPos pos, Direction face, int channel,
                                      List<ChannelConfigurationEdit> edits, int menuId, long requestId,
                                      Map<LogisticsNode, GroupKey> targets, LogisticsNode previewNode,
                                      long previewVersion) implements CustomPacketPayload {
    public C2SConfigureFacePayload(BlockPos pos, Direction face, int channel, List<ChannelConfigurationEdit> edits,
                                   int menuId, long requestId, Map<LogisticsNode, GroupKey> targets) {
        this(pos, face, channel, edits, menuId, requestId, targets, null, -1);
    }
    private static final int GLOBAL_INPUT = 0;
    private static final int GLOBAL_OUTPUT = 1;
    /// 操作号 2、3 曾用于频道设置，保留空缺以避免误解旧数据包。
    private static final int PRIORITY = 4;
    private static final int KEEP_STOCK = 5;
    private static final int STRATEGY = 6;
    private static final int EXTRACTION_MODE = 7;
    private static final int SELECTED_TYPES = 8;
    private static final int TYPE_PATCH = 9;

    public static final Type<C2SConfigureFacePayload> TYPE =
        new Type<>(StaticLogistics.asResource("configure_face"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SConfigureFacePayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public C2SConfigureFacePayload decode(RegistryFriendlyByteBuf buffer) {
                BlockPos pos = buffer.readBlockPos();
                Direction face = buffer.readEnum(Direction.class);
                int channel = buffer.readVarInt();
                int editCount = buffer.readVarInt();
                if (editCount < 1 || editCount > 16) throw new DecoderException("Invalid configuration edit count");
                List<ChannelConfigurationEdit> edits = new ArrayList<>(editCount);
                for (int i = 0; i < editCount; i++) edits.add(decodeEdit(buffer));
                int menuId = buffer.readVarInt();
                long requestId = buffer.readVarLong();
                int count = buffer.readVarInt();
                if (count < 1 || count > NodeSelectionPolicy.MAX_NODES)
                    throw new DecoderException("Invalid configuration target count");
                Map<LogisticsNode, GroupKey> targets = new LinkedHashMap<>();
                for (int i = 0; i < count; i++) {
                    var node = LogisticsNode.STREAM_CODEC.decode(buffer);
                    var group = GroupKey.STREAM_CODEC.decode(buffer);
                    if (targets.putIfAbsent(node, group) != null)
                        throw new DecoderException("Duplicate configuration target");
                }
                LogisticsNode previewNode = buffer.readBoolean() ? LogisticsNode.STREAM_CODEC.decode(buffer) : null;
                long previewVersion = previewNode == null ? -1 : buffer.readVarLong();
                return new C2SConfigureFacePayload(pos, face, channel, edits, menuId, requestId, targets, previewNode, previewVersion);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, C2SConfigureFacePayload payload) {
                buffer.writeBlockPos(payload.pos());
                buffer.writeEnum(payload.face());
                buffer.writeVarInt(payload.channel());
                buffer.writeVarInt(payload.edits().size());
                payload.edits().forEach(edit -> encodeEdit(buffer, edit));
                buffer.writeVarInt(payload.menuId());
                buffer.writeVarLong(payload.requestId());
                buffer.writeVarInt(payload.targets().size());
                payload.targets().forEach((node, group) -> {
                    LogisticsNode.STREAM_CODEC.encode(buffer, node);
                    GroupKey.STREAM_CODEC.encode(buffer, group);
                });
                buffer.writeBoolean(payload.previewNode() != null);
                if (payload.previewNode() != null) {
                    LogisticsNode.STREAM_CODEC.encode(buffer, payload.previewNode());
                    buffer.writeVarLong(payload.previewVersion());
                }
            }
        };

    public C2SConfigureFacePayload {
        edits = List.copyOf(edits);
        if (edits.isEmpty() || edits.size() > 16) throw new IllegalArgumentException("Invalid configuration edits");
        targets = Map.copyOf(targets);
        if (targets.isEmpty() || targets.size() > NodeSelectionPolicy.MAX_NODES
            || targets.keySet().stream().anyMatch(LogisticsNode::isConnectionFace)) {
            throw new IllegalArgumentException("Invalid configuration targets");
        }
        if (previewNode != null && (!targets.containsKey(previewNode) || previewVersion < 0))
            throw new IllegalArgumentException("Invalid preview target");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SConfigureFacePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof LinkConfiguratorMenu menu))
                return;
            if (menu.containerId != payload.menuId()) return;
            boolean accepted = false;
            try {
                if (!ServerPacketRateLimiter.allow(
                    player, ServerPacketRateLimiter.Action.FACE_CONFIGURATION)
                    || !NodeInteractionRules.matchesTarget(
                    menu.getPos(), menu.getFace(), payload.pos(), payload.face())
                    || !menu.hasTarget() || menu.getTargetNode().channel() != payload.channel() || payload.edits().stream().anyMatch(edit -> !menu.allowsEdit(edit)))
                    return;

                NodeMutationService mutations = new NodeMutationService();
                NodeMutationService.ValidatedNode node = menu.resolveValidatedNode(player);
                if (node == null) return;
                if (!menu.getRemoteGroupKey().equals(payload.targets().get(node.node()))) return;
                List<NodeMutationService.ValidatedNode> targets = new ArrayList<>();
                for (var entry : payload.targets().entrySet()) {
                    var target = mutations.resolveRemote(player, entry.getKey(), entry.getValue());
                    if (target == null || target.config().determineRole() != node.config().determineRole()) return;
                    targets.add(target);
                }
                if (payload.previewNode() != null && targets.stream().noneMatch(target ->
                    target.node().equals(payload.previewNode()) && target.config().getVersion() == payload.previewVersion()))
                    return;
                if (payload.edits().stream().anyMatch(ChannelConfigurationEdit.FilterEdit.class::isInstance)
                    && targets.stream().anyMatch(target -> !(target.config().filterConfig.getUpgrades().getStackInSlot(0).getItem()
                    instanceof LogisticsUpgrade upgrade) || !upgrade.isFilterUpgrade())) return;
                var changed = mutations.configureAll(targets, payload.edits());
                accepted = true;
                for (var edit : payload.edits())
                    if (edit instanceof ChannelConfigurationEdit.RoleEdit(NodeRole role1))
                        menu.selectVisibleSide(role1 == NodeRole.RECEIVER);

                if (payload.edits().stream().anyMatch(edit -> !(edit instanceof ChannelConfigurationEdit.NumberEdit))) {
                    Map<UUID, List<S2CTopologyUpdatePayload.FaceUpdate>> updates = new LinkedHashMap<>();
                    for (var target : changed)
                        updates.computeIfAbsent(target.config().faceConfig.getOwner(), ignored -> new ArrayList<>())
                            .add(S2CTopologyUpdatePayload.FaceUpdate.from(target.level(), target.node(), target.config()));
                    updates.forEach((owner, faces) -> TeamPacketSync.sendTopology(player, owner, faces));
                }
            } finally {
                menu.acknowledgeEdit(payload.requestId(), accepted);
            }
        });
    }

    public static ChannelConfigurationEdit decodeEdit(RegistryFriendlyByteBuf buffer) {
        int operation = buffer.readUnsignedByte();
        try {
            return switch (operation) {
                case GLOBAL_INPUT -> new ChannelConfigurationEdit.BooleanEdit(
                    ChannelConfigurationEdit.BooleanField.GLOBAL_INPUT, buffer.readBoolean());
                case GLOBAL_OUTPUT -> new ChannelConfigurationEdit.BooleanEdit(
                    ChannelConfigurationEdit.BooleanField.GLOBAL_OUTPUT, buffer.readBoolean());
                case PRIORITY -> new ChannelConfigurationEdit.NumberEdit(
                    ChannelConfigurationEdit.NumberField.PRIORITY, buffer.readVarLong());
                case KEEP_STOCK -> new ChannelConfigurationEdit.NumberEdit(
                    ChannelConfigurationEdit.NumberField.KEEP_STOCK, buffer.readVarLong());
                case STRATEGY -> decodeStrategy(buffer);
                case EXTRACTION_MODE -> decodeExtractionMode(buffer);
                case SELECTED_TYPES -> decodeSelectedTypes(buffer);
                case TYPE_PATCH -> new ChannelConfigurationEdit.TypePatchEdit(
                    BoundedNetworkCodecs.TRANSFER_TYPE_IDS.decode(buffer),
                    BoundedNetworkCodecs.TRANSFER_TYPE_IDS.decode(buffer));
                case 10 -> new ChannelConfigurationEdit.RedstoneEdit(buffer.readEnum(ChannelRedstoneMode.class));
                case 11 -> new ChannelConfigurationEdit.EnabledEdit(buffer.readBoolean());
                case 12 -> new ChannelConfigurationEdit.RoleEdit(buffer.readEnum(NodeRole.class));
                case 13 -> new ChannelConfigurationEdit.FilterEdit(FilterData.STREAM_CODEC.decode(buffer));
                default -> throw new DecoderException("Unknown face configuration operation: " + operation);
            };
        } catch (IllegalArgumentException exception) {
            throw new DecoderException("Invalid face configuration edit", exception);
        }
    }

    private static ChannelConfigurationEdit decodeStrategy(RegistryFriendlyByteBuf buffer) {
        ResourceLocation id = buffer.readResourceLocation();
        DistributionStrategy strategy = DistributionStrategyRegistry.get(id);
        if (strategy == null) throw new DecoderException("Unknown distribution strategy: " + id);
        return new ChannelConfigurationEdit.StrategyEdit(strategy);
    }

    private static ChannelConfigurationEdit decodeExtractionMode(RegistryFriendlyByteBuf buffer) {
        int ordinal = buffer.readUnsignedByte();
        ExtractionMode[] modes = ExtractionMode.values();
        if (ordinal >= modes.length) throw new DecoderException("Unknown extraction mode: " + ordinal);
        return new ChannelConfigurationEdit.ExtractionEdit(modes[ordinal]);
    }

    private static ChannelConfigurationEdit decodeSelectedTypes(RegistryFriendlyByteBuf buffer) {
        var ids = BoundedNetworkCodecs.TRANSFER_TYPE_IDS.decode(buffer);
        for (ResourceLocation id : ids) {
            if (TransferRegistries.get(id) == null) {
                throw new DecoderException("Unknown transfer type: " + id);
            }
        }
        return new ChannelConfigurationEdit.SelectedTypesEdit(ids);
    }

    public static void encodeEdit(RegistryFriendlyByteBuf buffer, ChannelConfigurationEdit edit) {
        switch (edit) {
            case ChannelConfigurationEdit.FilterEdit value -> {
                buffer.writeByte(13);
                FilterData.STREAM_CODEC.encode(buffer, value.filter());
            }
            case ChannelConfigurationEdit.RoleEdit value -> {
                buffer.writeByte(12);
                buffer.writeEnum(value.role());
            }
            case ChannelConfigurationEdit.EnabledEdit value -> {
                buffer.writeByte(11);
                buffer.writeBoolean(value.enabled());
            }
            case ChannelConfigurationEdit.RedstoneEdit value -> {
                buffer.writeByte(10);
                buffer.writeEnum(value.mode());
            }
            case ChannelConfigurationEdit.BooleanEdit value -> {
                buffer.writeByte(value.field() == ChannelConfigurationEdit.BooleanField.GLOBAL_INPUT
                    ? GLOBAL_INPUT : GLOBAL_OUTPUT);
                buffer.writeBoolean(value.enabled());
            }
            case ChannelConfigurationEdit.NumberEdit value -> {
                buffer.writeByte(value.field() == ChannelConfigurationEdit.NumberField.PRIORITY
                    ? PRIORITY : KEEP_STOCK);
                buffer.writeVarLong(value.value());
            }
            case ChannelConfigurationEdit.StrategyEdit value -> {
                buffer.writeByte(STRATEGY);
                buffer.writeResourceLocation(value.strategy().id());
            }
            case ChannelConfigurationEdit.ExtractionEdit value -> {
                buffer.writeByte(EXTRACTION_MODE);
                buffer.writeByte(value.mode().ordinal());
            }
            case ChannelConfigurationEdit.SelectedTypesEdit value -> {
                buffer.writeByte(SELECTED_TYPES);
                BoundedNetworkCodecs.TRANSFER_TYPE_IDS.encode(buffer, value.typeIds());
            }
            case ChannelConfigurationEdit.TypePatchEdit value -> {
                buffer.writeByte(TYPE_PATCH);
                BoundedNetworkCodecs.TRANSFER_TYPE_IDS.encode(buffer, value.added());
                BoundedNetworkCodecs.TRANSFER_TYPE_IDS.encode(buffer, value.removed());
            }
            default -> throw new EncoderException("Unsupported face configuration edit: " + edit.getClass());
        }
    }
}
