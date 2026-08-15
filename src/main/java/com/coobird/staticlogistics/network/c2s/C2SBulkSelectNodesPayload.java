package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.content.item.BulkSelectionInteractionGuard;
import com.coobird.staticlogistics.content.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.content.item.LinkOperationHelper;
import com.coobird.staticlogistics.content.item.ToolMode;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.node.FaceAddress;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import com.coobird.staticlogistics.logistics.node.NodeInteractionValidator;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import com.coobird.staticlogistics.transfer.TransferUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 选取已加载范围内六向连通的同种方块节点。
 */
public record C2SBulkSelectNodesPayload(BlockPos origin, Direction face,
                                        ToolMode mode) implements CustomPacketPayload {
    private static final int MAX_NODES = SLDataComponents.MAX_STORED_NODES;
    private static final int MAX_VISITED = MAX_NODES * 4;
    private static final int MAX_AXIS_DISTANCE = 24;
    private static final long SCAN_BUDGET_NANOS = 5_000_000L;
    public static final Type<C2SBulkSelectNodesPayload> TYPE =
        new Type<>(StaticLogistics.asResource("bulk_select_nodes"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SBulkSelectNodesPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public C2SBulkSelectNodesPayload decode(RegistryFriendlyByteBuf buffer) {
                return new C2SBulkSelectNodesPayload(BlockPos.STREAM_CODEC.decode(buffer),
                    Direction.STREAM_CODEC.decode(buffer), ToolMode.fromId(buffer.readVarInt()));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, C2SBulkSelectNodesPayload payload) {
                BlockPos.STREAM_CODEC.encode(buffer, payload.origin());
                Direction.STREAM_CODEC.encode(buffer, payload.face());
                buffer.writeVarInt(payload.mode().getId());
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SBulkSelectNodesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !payload.mode().isLinkMode()
                || !ServerPacketRateLimiter.allow(
                player, ServerPacketRateLimiter.Action.BULK_NODE_SELECTION)
                || !NodeInteractionValidator.isDirectBlockTargetValid(
                player, payload.origin(), payload.face())) return;
            ItemStack stack = ToolSettingsTarget.findConfigurator(player);
            if (!(stack.getItem() instanceof LinkConfiguratorItem item)
                || player.distanceToSqr(Vec3.atCenterOf(payload.origin())) > 100.0D) return;
            LinkConfiguratorItem.ToolSettings settings = item.getSettings(stack);
            if (settings.mode() != payload.mode()) return;
            BulkSelectionInteractionGuard.mark(player, payload.origin(), payload.face());
            List<LogisticsNode> found = collect(player, payload, settings.selectedTypeIds());
            int added = LinkOperationHelper.addNodes(
                stack, found, payload.mode(), player, player.serverLevel());
            player.displayClientMessage(Component.translatable(
                    "msg.staticlogistics.bulk_nodes_added", added, found.size())
                .withStyle(added > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY), true);
        });
    }

    private static List<LogisticsNode> collect(
        ServerPlayer player,
        C2SBulkSelectNodesPayload payload,
        List<ResourceLocation> selectedTypeIds
    ) {
        var level = player.serverLevel();
        Block seedBlock = level.getBlockState(payload.origin()).getBlock();
        LinkManager manager = LinkManager.get(level);
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        LinkedHashSet<BlockPos> visited = new LinkedHashSet<>();
        List<LogisticsNode> result = new ArrayList<>();
        pending.add(payload.origin());
        long deadline = System.nanoTime() + SCAN_BUDGET_NANOS;
        while (!pending.isEmpty() && visited.size() < MAX_VISITED
            && result.size() < MAX_NODES && System.nanoTime() - deadline < 0L) {
            BlockPos pos = pending.removeFirst();
            if (!visited.add(pos) || !inside(payload.origin(), pos)
                || !level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                || level.getBlockState(pos).getBlock() != seedBlock
                || !player.mayBuild() || !level.mayInteract(player, pos)) continue;
            for (Direction face : TransferUtils.getCapabilityFaces(
                level, pos, payload.face(), selectedTypeIds)) {
                FaceConfigComposite config = manager.getFaceConfig(FaceAddress.of(pos, face));
                if (config == null || config.canPlayerAccess(player)) {
                    result.add(new LogisticsNode(
                        GlobalPos.of(level.dimension(), pos.immutable()), face));
                    break;
                }
            }
            for (Direction direction : Direction.values()) pending.addLast(pos.relative(direction));
        }
        return List.copyOf(result);
    }

    private static boolean inside(BlockPos origin, BlockPos candidate) {
        return Math.abs(candidate.getX() - origin.getX()) <= MAX_AXIS_DISTANCE
            && Math.abs(candidate.getY() - origin.getY()) <= MAX_AXIS_DISTANCE
            && Math.abs(candidate.getZ() - origin.getZ()) <= MAX_AXIS_DISTANCE;
    }
}
