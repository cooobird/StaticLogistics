package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.ExtractionMode;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.node.FaceConfigurationEdit;
import com.coobird.staticlogistics.logistics.node.NodeInteractionRules;
import com.coobird.staticlogistics.logistics.node.NodeMutationService;
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
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Objects;

/**
 * 客户端提交的单项面配置修改。
 */
public record C2SConfigureFacePayload(BlockPos pos, Direction face, FaceConfigurationEdit edit) implements CustomPacketPayload {
    private static final int GLOBAL_INPUT = 0;
    private static final int GLOBAL_OUTPUT = 1;
    // 操作号 2、3 曾用于频道设置，保留空缺以避免误解旧数据包。
    private static final int PRIORITY = 4;
    private static final int KEEP_STOCK = 5;
    private static final int STRATEGY = 6;
    private static final int EXTRACTION_MODE = 7;
    private static final int SELECTED_TYPES = 8;

    public static final Type<C2SConfigureFacePayload> TYPE =
        new Type<>(StaticLogistics.asResource("configure_face"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SConfigureFacePayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public C2SConfigureFacePayload decode(RegistryFriendlyByteBuf buffer) {
                BlockPos pos = buffer.readBlockPos();
                Direction face = buffer.readEnum(Direction.class);
                return new C2SConfigureFacePayload(pos, face, decodeEdit(buffer));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, C2SConfigureFacePayload payload) {
                buffer.writeBlockPos(payload.pos());
                buffer.writeEnum(payload.face());
                encodeEdit(buffer, payload.edit());
            }
        };

    public C2SConfigureFacePayload {
        Objects.requireNonNull(pos, "Block position must not be null");
        Objects.requireNonNull(face, "Face must not be null");
        Objects.requireNonNull(edit, "Face configuration edit must not be null");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SConfigureFacePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !ServerPacketRateLimiter.allow(
                player, ServerPacketRateLimiter.Action.FACE_CONFIGURATION)
                || !(player.containerMenu instanceof LinkConfiguratorMenu menu)
                || !NodeInteractionRules.matchesTarget(
                menu.getPos(), menu.getFace(), payload.pos(), payload.face())
                || !menu.allowsEdit(payload.edit())) return;

            NodeMutationService mutations = new NodeMutationService();
            NodeMutationService.ValidatedNode node = menu.resolveValidatedNode(player);
            if (node == null || !mutations.configure(node, payload.edit())) return;

            menu.syncFaceSlots();
            menu.broadcastChanges();
            TeamPacketSync.sendTopology(player, menu.getRemoteGroupKey().ownerId(), List.of(
                S2CTopologyUpdatePayload.FaceUpdate.from(
                    node.level(),
                    new LogisticsNode(
                        GlobalPos.of(node.level().dimension(), payload.pos()), payload.face()),
                    node.config())));
        });
    }

    static FaceConfigurationEdit decodeEdit(RegistryFriendlyByteBuf buffer) {
        int operation = buffer.readUnsignedByte();
        try {
            return switch (operation) {
                case GLOBAL_INPUT -> new FaceConfigurationEdit.BooleanEdit(
                    FaceConfigurationEdit.BooleanField.GLOBAL_INPUT, buffer.readBoolean());
                case GLOBAL_OUTPUT -> new FaceConfigurationEdit.BooleanEdit(
                    FaceConfigurationEdit.BooleanField.GLOBAL_OUTPUT, buffer.readBoolean());
                case PRIORITY -> new FaceConfigurationEdit.NumberEdit(
                    FaceConfigurationEdit.NumberField.PRIORITY, buffer.readVarInt());
                case KEEP_STOCK -> new FaceConfigurationEdit.NumberEdit(
                    FaceConfigurationEdit.NumberField.KEEP_STOCK, buffer.readVarInt());
                case STRATEGY -> decodeStrategy(buffer);
                case EXTRACTION_MODE -> decodeExtractionMode(buffer);
                case SELECTED_TYPES -> decodeSelectedTypes(buffer);
                default -> throw new DecoderException("Unknown face configuration operation: " + operation);
            };
        } catch (IllegalArgumentException exception) {
            throw new DecoderException("Invalid face configuration edit", exception);
        }
    }

    private static FaceConfigurationEdit decodeStrategy(RegistryFriendlyByteBuf buffer) {
        ResourceLocation id = buffer.readResourceLocation();
        DistributionStrategy strategy = DistributionStrategyRegistry.get(id);
        if (strategy == null) throw new DecoderException("Unknown distribution strategy: " + id);
        return new FaceConfigurationEdit.StrategyEdit(strategy);
    }

    private static FaceConfigurationEdit decodeExtractionMode(RegistryFriendlyByteBuf buffer) {
        int ordinal = buffer.readUnsignedByte();
        ExtractionMode[] modes = ExtractionMode.values();
        if (ordinal >= modes.length) throw new DecoderException("Unknown extraction mode: " + ordinal);
        return new FaceConfigurationEdit.ExtractionEdit(modes[ordinal]);
    }

    private static FaceConfigurationEdit decodeSelectedTypes(RegistryFriendlyByteBuf buffer) {
        var ids = BoundedNetworkCodecs.TRANSFER_TYPE_IDS.decode(buffer);
        for (ResourceLocation id : ids) {
            if (TransferRegistries.get(id) == null) {
                throw new DecoderException("Unknown transfer type: " + id);
            }
        }
        return new FaceConfigurationEdit.SelectedTypesEdit(ids);
    }

    static void encodeEdit(RegistryFriendlyByteBuf buffer, FaceConfigurationEdit edit) {
        switch (edit) {
            case FaceConfigurationEdit.BooleanEdit value -> {
                buffer.writeByte(value.field() == FaceConfigurationEdit.BooleanField.GLOBAL_INPUT
                    ? GLOBAL_INPUT : GLOBAL_OUTPUT);
                buffer.writeBoolean(value.enabled());
            }
            case FaceConfigurationEdit.NumberEdit value -> {
                buffer.writeByte(value.field() == FaceConfigurationEdit.NumberField.PRIORITY
                    ? PRIORITY : KEEP_STOCK);
                buffer.writeVarInt(value.value());
            }
            case FaceConfigurationEdit.StrategyEdit value -> {
                buffer.writeByte(STRATEGY);
                buffer.writeResourceLocation(value.strategy().id());
            }
            case FaceConfigurationEdit.ExtractionEdit value -> {
                buffer.writeByte(EXTRACTION_MODE);
                buffer.writeByte(value.mode().ordinal());
            }
            case FaceConfigurationEdit.SelectedTypesEdit value -> {
                buffer.writeByte(SELECTED_TYPES);
                BoundedNetworkCodecs.TRANSFER_TYPE_IDS.encode(buffer, value.typeIds());
            }
            default -> throw new EncoderException("Unsupported face configuration edit: " + edit.getClass());
        }
    }
}
