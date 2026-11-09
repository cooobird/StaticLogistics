package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.content.item.*;
import com.coobird.staticlogistics.integration.sable.DynamicNodeSpace;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.node.EndpointAddress;
import com.coobird.staticlogistics.logistics.node.EndpointConfig;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import com.coobird.staticlogistics.logistics.node.NodeInteractionValidator;
import com.coobird.staticlogistics.logistics.selection.NodeSelectionPolicy;
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

import java.util.*;

/// 选取已加载范围内六向连通的同种方块节点。
public record C2SBulkSelectNodesPayload(BlockPos origin, Direction face, ToolMode mode, int slot,
                                        UUID toolId) implements CustomPacketPayload {
    public static final Type<C2SBulkSelectNodesPayload> TYPE =
        new Type<>(StaticLogistics.asResource("bulk_select_nodes"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SBulkSelectNodesPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public C2SBulkSelectNodesPayload decode(RegistryFriendlyByteBuf buffer) {
                return new C2SBulkSelectNodesPayload(BlockPos.STREAM_CODEC.decode(buffer),
                    Direction.STREAM_CODEC.decode(buffer), ToolMode.fromId(buffer.readVarInt()), buffer.readVarInt(), buffer.readUUID());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, C2SBulkSelectNodesPayload payload) {
                BlockPos.STREAM_CODEC.encode(buffer, payload.origin());
                Direction.STREAM_CODEC.encode(buffer, payload.face());
                buffer.writeVarInt(payload.mode().getId());
                buffer.writeVarInt(payload.slot());
                buffer.writeUUID(payload.toolId());
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
            if (payload.slot() != player.getInventory().selected && payload.slot() != 40) return;
            ItemStack stack = player.getInventory().getItem(payload.slot());
            if (BlueprintModeHandler.isActive(stack)) return;
            if (!(stack.getItem() instanceof LinkConfiguratorItem item) || !item.matchesSession(stack, payload.toolId())
                || DynamicNodeSpace.distanceSquared(player.level(), player.position(),
                Vec3.atCenterOf(payload.origin())) > 100.0D) return;
            LinkConfiguratorItem.ToolSettings settings = item.getSettings(stack);
            if (settings.mode() != payload.mode()) return;
            BulkSelectionInteractionGuard.mark(player, payload.origin(), payload.face());
            /// 相反模式统一进入建链，不再将新旧两组节点混进同一份选择。
            if (settings.storedMode() != null && settings.storedMode() != settings.mode()) {
                LinkOperationHelper.executeBatchLink(stack, settings, payload.origin(), payload.face(), player.serverLevel(), player);
                return;
            }
            int remainingCapacity = NodeSelectionPolicy.remainingCapacity(
                stack.getOrDefault(SLDataComponents.STORED_NODES.get(), List.of()).size());
            if (remainingCapacity == 0) {
                player.displayClientMessage(Component.translatable(
                    "msg.staticlogistics.selection_limit", NodeSelectionPolicy.MAX_NODES), true);
                return;
            }
            var level = player.serverLevel();
            if (TransferUtils.getCapabilityFaces(level, payload.origin(), payload.face(),
                settings.selectedTypeIds()).isEmpty()) {
                player.displayClientMessage(Component.translatable("msg.staticlogistics.no_capability"), true);
                return;
            }
            Block seed = level.getBlockState(payload.origin()).getBlock();
            int selectedChannel = settings.channel();
            List<ResourceLocation> selectedTypes = List.copyOf(settings.selectedTypeIds());
            var originalSelection = stack.get(SLDataComponents.STORED_NODES.get());
            BulkWorkScheduler.replace(player, "node_scan",
                new BulkWorkScheduler.Step() {
                    final ArrayDeque<BlockPos> pending = new ArrayDeque<>(List.of(payload.origin()));
                    final LinkedHashSet<BlockPos> discovered = new LinkedHashSet<>(List.of(payload.origin()));
                    final List<LogisticsNode> found = new ArrayList<>();
                    boolean limitReached;

                    public boolean run(ServerPlayer current, long deadline) {
                        LinkConfiguratorItem.ToolSettings currentSettings = item.getSettings(stack);
                        if (current.serverLevel() != level
                            || (payload.slot() != current.getInventory().selected && payload.slot() != 40)
                            || current.getInventory().getItem(payload.slot()) != stack
                            || currentSettings.mode() != payload.mode()
                            || currentSettings.channel() != selectedChannel
                            || !currentSettings.selectedTypeIds().equals(selectedTypes)
                            || stack.get(SLDataComponents.STORED_NODES.get()) != originalSelection) {
                            current.displayClientMessage(Component.translatable("msg.staticlogistics.bulk_cancelled"), true);
                            return true;
                        }
                        LinkManager manager = LinkManager.get(level);
                        int processed = 0;
                        while (!pending.isEmpty() && processed++ < 128 && System.nanoTime() - deadline < 0) {
                            BlockPos pos = pending.removeFirst();
                            if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                                || level.getBlockState(pos).getBlock() != seed || !current.mayBuild()
                                || !level.mayInteract(current, pos)) continue;
                            for (Direction face : TransferUtils.getCapabilityFaces(level, pos, payload.face(), selectedTypes)) {
                                int channel = LinkConfiguratorItem.channelForFace(
                                    level, pos, face, selectedChannel);
                                EndpointConfig config = manager.getEndpointConfig(
                                    new EndpointAddress(pos.asLong(), face, channel));
                                if (config == null || config.canPlayerAccess(current)) {
                                    found.add(new LogisticsNode(
                                        GlobalPos.of(level.dimension(), pos.immutable()), face, channel));
                                    break;
                                }
                            }
                            if (found.size() >= remainingCapacity) {
                                limitReached = true;
                                pending.clear();
                                break;
                            }
                            for (Direction direction : Direction.values()) {
                                BlockPos adjacent = pos.relative(direction);
                                if (discovered.contains(adjacent)) continue;
                                if (discovered.size() >= NodeSelectionPolicy.MAX_SCANNED_BLOCKS) {
                                    limitReached = true;
                                    pending.clear();
                                    break;
                                }
                                if (!level.getChunkSource().hasChunk(adjacent.getX() >> 4, adjacent.getZ() >> 4)
                                    || level.getBlockState(adjacent).getBlock() != seed) continue;
                                discovered.add(adjacent.immutable());
                                pending.addLast(adjacent.immutable());
                            }
                        }
                        if (!pending.isEmpty()) {
                            if (level.getGameTime() % 20 == 0) current.displayClientMessage(
                                Component.translatable("msg.staticlogistics.bulk_scanning", found.size()), true);
                            return false;
                        }
                        int added = LinkOperationHelper.addNodes(stack, found, payload.mode(), current, level);
                        String message = limitReached
                            ? "msg.staticlogistics.bulk_nodes_added_limited"
                            : "msg.staticlogistics.bulk_nodes_added";
                        current.displayClientMessage(Component.translatable(message, added, found.size())
                            .withStyle(ChatFormatting.GREEN), true);
                        return true;
                    }
                });
        });
    }
}
