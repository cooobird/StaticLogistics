package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.util.Objects;

/**
 * 客户端提交的单项面配置修改。
 */
public record C2SConfigureFacePayload(
    BlockPos pos,
    Direction face,
    FaceConfigurationEdit edit
) implements IPortPacket.C2S {
    private static final int GLOBAL_INPUT = 0;
    private static final int GLOBAL_OUTPUT = 1;
    // 操作号 2、3 曾用于频道设置，保留空缺以避免误解旧数据包。
    private static final int PRIORITY = 4;
    private static final int KEEP_STOCK = 5;
    private static final int STRATEGY = 6;
    private static final int EXTRACTION_MODE = 7;
    private static final int SELECTED_TYPES = 8;

    public static final ResourceLocation ID = StaticLogistics.asResource("configure_face");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SConfigureFacePayload> STREAM_CODEC =
        new PortStreamCodec<>() {
            @Override
            public C2SConfigureFacePayload decode(PortRegistryFriendlyByteBuf buffer) {
                return new C2SConfigureFacePayload(
                    buffer.readBlockPos(), buffer.readEnum(Direction.class), decodeEdit(buffer));
            }

            @Override
            public void encode(PortRegistryFriendlyByteBuf buffer, C2SConfigureFacePayload payload) {
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
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        if (!ServerPacketRateLimiter.allow(player, ServerPacketRateLimiter.Action.FACE_CONFIGURATION)
            || !(player.containerMenu instanceof LinkConfiguratorMenu menu)
            || !menu.stillValid(player)
            || !NodeInteractionRules.matchesTarget(menu.getPos(), menu.getFace(), pos(), face())
            || !menu.allowsEdit(edit())) return;

        NodeMutationService mutations = new NodeMutationService();
        NodeMutationService.ValidatedNode node = menu.resolveValidatedNode(player);
        if (node == null || !mutations.configure(node, edit())) return;
        menu.syncFaceSlots();
        menu.broadcastChanges();
        TeamPacketSync.sendTopology(player, menu.getRemoteGroupKey().ownerId(), java.util.List.of(
            S2CTopologyUpdatePayload.FaceUpdate.from(
                node.level(), node.node(), node.config())));
    }

    private static FaceConfigurationEdit decodeEdit(PortRegistryFriendlyByteBuf buffer) {
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
                default -> throw new DecoderException(
                    "Unknown face configuration operation: " + operation);
            };
        } catch (IllegalArgumentException exception) {
            throw new DecoderException("Invalid face configuration edit", exception);
        }
    }

    private static FaceConfigurationEdit decodeStrategy(PortRegistryFriendlyByteBuf buffer) {
        ResourceLocation id = buffer.readResourceLocation();
        DistributionStrategy strategy = DistributionStrategyRegistry.byName(id.toString());
        if (!strategy.id().equals(id)) {
            throw new DecoderException("Unknown distribution strategy: " + id);
        }
        return new FaceConfigurationEdit.StrategyEdit(strategy);
    }

    private static FaceConfigurationEdit decodeExtractionMode(PortRegistryFriendlyByteBuf buffer) {
        int ordinal = buffer.readUnsignedByte();
        ExtractionMode[] modes = ExtractionMode.values();
        if (ordinal >= modes.length) {
            throw new DecoderException("Unknown extraction mode: " + ordinal);
        }
        return new FaceConfigurationEdit.ExtractionEdit(modes[ordinal]);
    }

    private static FaceConfigurationEdit decodeSelectedTypes(PortRegistryFriendlyByteBuf buffer) {
        var ids = BoundedNetworkCodecs.TRANSFER_TYPE_IDS.decode(buffer);
        for (ResourceLocation id : ids) {
            if (TransferRegistries.get(id) == null) {
                throw new DecoderException("Unknown transfer type: " + id);
            }
        }
        return new FaceConfigurationEdit.SelectedTypesEdit(ids);
    }

    private static void encodeEdit(PortRegistryFriendlyByteBuf buffer, FaceConfigurationEdit edit) {
        if (edit instanceof FaceConfigurationEdit.BooleanEdit value) {
            buffer.writeByte(value.field() == FaceConfigurationEdit.BooleanField.GLOBAL_INPUT
                ? GLOBAL_INPUT : GLOBAL_OUTPUT);
            buffer.writeBoolean(value.enabled());
        } else if (edit instanceof FaceConfigurationEdit.NumberEdit value) {
            buffer.writeByte(value.field() == FaceConfigurationEdit.NumberField.PRIORITY
                ? PRIORITY : KEEP_STOCK);
            buffer.writeVarInt(value.value());
        } else if (edit instanceof FaceConfigurationEdit.StrategyEdit value) {
            buffer.writeByte(STRATEGY);
            buffer.writeResourceLocation(value.strategy().id());
        } else if (edit instanceof FaceConfigurationEdit.ExtractionEdit value) {
            buffer.writeByte(EXTRACTION_MODE);
            buffer.writeByte(value.mode().ordinal());
        } else if (edit instanceof FaceConfigurationEdit.SelectedTypesEdit value) {
            buffer.writeByte(SELECTED_TYPES);
            BoundedNetworkCodecs.TRANSFER_TYPE_IDS.encode(buffer, value.typeIds());
        } else {
            throw new EncoderException("Unsupported face configuration edit: " + edit.getClass());
        }
    }
}
