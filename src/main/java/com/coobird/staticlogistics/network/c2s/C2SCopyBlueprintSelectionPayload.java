package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.content.item.BlueprintModeHandler;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.blueprint.BlueprintCaptureService;
import com.coobird.staticlogistics.logistics.blueprint.BlueprintData;
import com.coobird.staticlogistics.logistics.group.GroupHierarchy;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.network.BoundedNetworkCodecs;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import com.coobird.staticlogistics.network.s2c.S2CBlueprintCopyResultPayload;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortPacketDistributor;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.util.*;

public record C2SCopyBlueprintSelectionPayload(UUID request, int toolSlot, GroupKey group,
                                               String directoryId, List<ConnectionKey> connections,
                                               GlobalPos anchor, List<BlockPos> positions) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("copy_blueprint_selection");

    public C2SCopyBlueprintSelectionPayload {
        directoryId = directoryId == null ? "" : directoryId;
        positions = List.copyOf(positions);
        connections = List.copyOf(connections);
    }

    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SCopyBlueprintSelectionPayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public C2SCopyBlueprintSelectionPayload decode(PortRegistryFriendlyByteBuf buffer) {
            UUID id = buffer.readUUID();
            int slot = buffer.readVarInt();
            GroupKey group = GroupKey.STREAM_CODEC.decode(buffer);
            String directoryId = buffer.readUtf(GroupConstraints.MAX_HIERARCHY_ID_LENGTH);
            int count = buffer.readVarInt();
            if (count < 1 || count > BlueprintData.MAX_LINKS_PER_FACE) {
                throw new DecoderException("Invalid blueprint connection selection");
            }
            List<ConnectionKey> connections = new ArrayList<>();
            for (int i = 0; i < count; i++) connections.add(ConnectionKey.STREAM_CODEC.decode(buffer));
            var anchor = BoundedNetworkCodecs.GLOBAL_POS.decode(buffer);
            int size = buffer.readVarInt();
            if (size < 1 || size > BlueprintData.MAX_BLOCKS)
                throw new DecoderException("Invalid blueprint selection size");
            List<BlockPos> positions = new ArrayList<>();
            for (int i = 0; i < size; i++) positions.add(buffer.readBlockPos());
            return new C2SCopyBlueprintSelectionPayload(id, slot, group, directoryId, connections, anchor, positions);
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buffer, C2SCopyBlueprintSelectionPayload value) {
            buffer.writeUUID(value.request());
            buffer.writeVarInt(value.toolSlot());
            GroupKey.STREAM_CODEC.encode(buffer, value.group());
            buffer.writeUtf(value.directoryId(), GroupConstraints.MAX_HIERARCHY_ID_LENGTH);
            buffer.writeVarInt(value.connections().size());
            value.connections().forEach(key -> ConnectionKey.STREAM_CODEC.encode(buffer, key));
            BoundedNetworkCodecs.GLOBAL_POS.encode(buffer, value.anchor());
            buffer.writeVarInt(value.positions().size());
            value.positions().forEach(buffer::writeBlockPos);
        }
    };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer packetPlayer) {
        C2SCopyBlueprintSelectionPayload payload = this;
        Player context = packetPlayer;

        if (!(context instanceof ServerPlayer player)) return;
        boolean success = false;
        try {
            var stack = BlueprintModeHandler.tool(player, payload.toolSlot());
            if (stack.isEmpty() || !ServerPacketRateLimiter.allow(player, ServerPacketRateLimiter.Action.BLUEPRINT_PREVIEW))
                return;
            PlayerGroupStore store = PlayerGroupStore.get(player.server);
            var group = store.findGroup(payload.group());
            var positions = new HashSet<>(payload.positions());
            if (!payload.anchor().dimension().equals(player.level().dimension()) || group == null || positions.size() != payload.positions().size())
                return;
            Set<ConnectionKey> connections = new LinkedHashSet<>(payload.connections());
            if (connections.isEmpty() || connections.size() > BlueprintData.MAX_LINKS_PER_FACE
                || connections.size() != payload.connections().size()
                || connections.stream().anyMatch(key -> !key.groupKey().equals(payload.group()))
                || !payload.directoryId().isEmpty()
                && !connectionsBelongToDirectory(store.getHierarchy(player.getUUID()),
                payload.directoryId(), connections)) return;
            var result = BlueprintCaptureService.captureSelected(player.serverLevel(), player, payload.anchor().pos(), positions, group, connections);
            if (result.status() != BlueprintCaptureService.Status.SUCCESS) return;
            stack.set(SLDataComponents.BLUEPRINT_DATA.get(), result.data());
            stack.set(SLDataComponents.BLUEPRINT_SOURCE_DIMENSION.get(), player.level().dimension().location().toString());
            stack.set(SLDataComponents.BLUEPRINT_GROUP.get(), group.displayName());
            stack.set(SLDataComponents.BLUEPRINT_GROUP_KEY.get(), group.key());
            stack.remove(SLDataComponents.BLUEPRINT_ANCHOR.get());
            stack.remove(SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get());
            stack.remove(SLDataComponents.BLUEPRINT_PREVIEW_ROTATION.get());
            player.displayClientMessage(Component.translatable("msg.staticlogistics.blueprint.copied", positions.size(), payload.anchor().pos().toShortString()), true);
            BlueprintModeHandler.start(player, stack);
            player.containerMenu.broadcastChanges();
            success = true;
        } finally {
            PortPacketDistributor.sendToPlayer(player, new S2CBlueprintCopyResultPayload(payload.request(), success));
        }

    }

    private static boolean connectionsBelongToDirectory(CompoundTag hierarchy,
                                                        String directoryId,
                                                        Set<ConnectionKey> connections) {
        var entries = hierarchy.getCompound("entries");
        if (!entries.contains(directoryId)) return false;
        Set<String> directories = new HashSet<>();
        directories.add(directoryId);
        boolean expanded;
        do {
            expanded = false;
            for (String id : entries.getAllKeys()) {
                if (directories.contains(entries.getCompound(id).getString("parent"))) {
                    expanded |= directories.add(id);
                }
            }
        } while (expanded);
        return connections.stream().allMatch(key ->
            directories.contains(GroupHierarchy.connectionFolder(hierarchy, key)));
    }
}
