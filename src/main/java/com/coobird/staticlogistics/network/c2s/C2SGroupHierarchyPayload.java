package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.event.PlayerEvents;
import com.coobird.staticlogistics.logistics.group.ConnectionCommandService;
import com.coobird.staticlogistics.logistics.group.GroupHierarchy;
import com.coobird.staticlogistics.logistics.group.LogisticsOperationStore;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.logistics.node.NodeInteractionValidator;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.util.UUID;

public record C2SGroupHierarchyPayload(String action, String id, String parent,
                                       String name) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("edit_group_hierarchy");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SGroupHierarchyPayload> STREAM_CODEC = new PortStreamCodec<>() {
        public C2SGroupHierarchyPayload decode(PortRegistryFriendlyByteBuf buf) {
            return new C2SGroupHierarchyPayload(buf.readUtf(16), buf.readUtf(80), buf.readUtf(36), buf.readUtf(128));
        }

        public void encode(PortRegistryFriendlyByteBuf buf, C2SGroupHierarchyPayload value) {
            buf.writeUtf(value.action(), 16).writeUtf(value.id(), 80).writeUtf(value.parent(), 36).writeUtf(value.name(), 128);
        }
    };

    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer packetPlayer) {
        C2SGroupHierarchyPayload payload = this;
        Player context = packetPlayer;

        if (!(context instanceof ServerPlayer player) || !NodeInteractionValidator.holdsConfigurator(player)) return;
        var limit = payload.action().equals("select") || payload.action().equals("query") ? ServerPacketRateLimiter.Action.TOOL_SETTINGS : ServerPacketRateLimiter.Action.GROUP_CREATION;
        if (!ServerPacketRateLimiter.allow(player, limit)) return;
        PlayerGroupStore store = PlayerGroupStore.get(player.server);
        if (LogisticsOperationStore.get(player.server).hasDirectoryDeletion(player.getUUID())
            && !payload.action().equals("query")
            && !payload.action().equals("delete")) return;
        if (payload.action().equals("team_access")) {
            if (!payload.parent().equals("0") && !payload.parent().equals("1")) return;
            store.setTeamAccessEnabled(player.getUUID(), payload.parent().equals("1"));
            for (ServerPlayer online : player.server.getPlayerList().getPlayers()) {
                PlayerEvents.requestClientStateRefresh(online);
                PlayerEvents.requestHierarchyRefresh(online);
            }
            return;
        }
        if (payload.action().equals("grant") || payload.action().equals("grant_name")) {
            try {
                UUID recipient;
                if (payload.action().equals("grant_name")) {
                    String playerName = payload.id().trim();
                    if (!playerName.matches("[A-Za-z0-9_]{1,16}"))
                        throw new IllegalArgumentException("Invalid player name");
                    var cache = player.server.getProfileCache();
                    if (cache == null) throw new IllegalArgumentException("Player profile unavailable");
                    var profile = cache.get(playerName);
                    if (profile.isEmpty()) {
                        player.displayClientMessage(Component.translatable("gui.staticlogistics.permissions.player_not_found", playerName), false);
                        return;
                    }
                    recipient = profile.get().getId();
                } else {
                    recipient = UUID.fromString(payload.id());
                }
                int permission = Integer.parseInt(payload.parent());
                if (payload.action().equals("grant_name") && store.grant(player.getUUID(), recipient) > 0) {
                    player.displayClientMessage(Component.translatable("gui.staticlogistics.permissions.already_added"), false);
                    return;
                }
                store.setGrant(player.getUUID(), recipient, permission);
                ServerPlayer target = player.server.getPlayerList().getPlayer(recipient);
                if (target != null) {
                    PlayerEvents.requestClientStateRefresh(target);
                    PlayerEvents.requestHierarchyRefresh(target);
                }
                PlayerEvents.requestHierarchyRefresh(player);
            } catch (IllegalArgumentException | IllegalStateException ignored) {
                // 无效请求不会更改授权。
            }
            return;
        }
        var data = store.getHierarchy(player.getUUID());
        if (GroupHierarchy.initializeDefaults(data)) store.setHierarchy(player.getUUID(), data);
        try {
            if (payload.action().equals("delete")) {
                boolean removed = new ConnectionCommandService(player.server)
                    .deleteDirectory(player, payload.id());
                if (!removed)
                    player.displayClientMessage(Component.translatable("gui.staticlogistics.directory.delete_failed"), false);
                data = store.getHierarchy(player.getUUID());
            } else if (GroupHierarchy.edit(data, payload.action(), payload.id(), payload.parent(), payload.name()))
                store.setHierarchy(player.getUUID(), data);
        } catch (IllegalArgumentException ignored) {
        }
        PlayerEvents.requestHierarchyRefresh(player);

    }
}
