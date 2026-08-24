package com.coobird.staticlogistics.logistics.redstone;

import com.coobird.staticlogistics.logistics.group.ConnectionCommandService;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.api.group.GroupKey;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 * 保存玩家关闭配置界面后、右键检测点前的短期选择状态。
 *
 * <p>这里只保存临时交互，不写入物品或世界存档；最终绑定仍由
 * {@link RedstoneControlStore} 统一持久化。</p>
 */
public final class RedstonePointSelectionSession {
    public static final int MAX_CONNECTIONS = 256;
    private static final long TIMEOUT_TICKS = 20L * 60L;
    private static final Map<MinecraftServer, Map<UUID, PendingSelection>> PENDING =
        new LinkedHashMap<>();

    private RedstonePointSelectionSession() {
    }

    public static boolean begin(ServerPlayer player, List<ConnectionKey> requested) {
        if (player == null || requested == null || requested.isEmpty()
            || requested.size() > MAX_CONNECTIONS) return false;

        List<ConnectionKey> connections = requested.stream().distinct().toList();
        if (connections.isEmpty() || connections.size() > MAX_CONNECTIONS) return false;
        ConnectionCommandService commands = new ConnectionCommandService(player.server);
        if (connections.stream().anyMatch(connection ->
            !commands.isSelectable(player, connection))) return false;

        PENDING.computeIfAbsent(player.server, ignored -> new LinkedHashMap<>())
            .put(player.getUUID(), new PendingSelection(
                connections, player.server.overworld().getGameTime() + TIMEOUT_TICKS));
        return true;
    }

    public static boolean hasPending(ServerPlayer player) {
        return getValid(player) != null;
    }

    public static BindResult bind(ServerPlayer player, GlobalPos controller) {
        PendingSelection selection = removeValid(player);
        if (selection == null || controller == null) return BindResult.EMPTY;

        RedstoneControlStore store = RedstoneControlStore.get(player.server);
        RedstoneControlBinding binding = new RedstoneControlBinding(
            controller, RedstoneControlMode.HIGH);
        int count = store.bindAll(selection.connections(), binding);
        Set<GroupKey> groups = new LinkedHashSet<>();
        selection.connections().forEach(connection -> groups.add(connection.groupKey()));
        return new BindResult(count, Set.copyOf(groups));
    }

    public static boolean cancel(ServerPlayer player) {
        Map<UUID, PendingSelection> byPlayer = PENDING.get(player.server);
        if (byPlayer == null || byPlayer.remove(player.getUUID()) == null) return false;
        if (byPlayer.isEmpty()) PENDING.remove(player.server);
        return true;
    }

    public static void release(MinecraftServer server) {
        if (server != null) PENDING.remove(server);
    }

    private static PendingSelection getValid(ServerPlayer player) {
        Map<UUID, PendingSelection> byPlayer = PENDING.get(player.server);
        if (byPlayer == null) return null;
        PendingSelection selection = byPlayer.get(player.getUUID());
        if (selection == null) return null;
        if (selection.expiresAt() >= player.server.overworld().getGameTime()) {
            return selection;
        }
        byPlayer.remove(player.getUUID());
        if (byPlayer.isEmpty()) PENDING.remove(player.server);
        return null;
    }

    private static PendingSelection removeValid(ServerPlayer player) {
        PendingSelection selection = getValid(player);
        if (selection != null) cancel(player);
        return selection;
    }

    private record PendingSelection(List<ConnectionKey> connections, long expiresAt) {
    }

    public record BindResult(int count, Set<GroupKey> groupKeys) {
        private static final BindResult EMPTY = new BindResult(0, Set.of());

        public BindResult {
            groupKeys = Set.copyOf(groupKeys);
        }
    }
}
