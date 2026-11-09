package com.coobird.staticlogistics.client.data;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.logistics.group.GroupHierarchy;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.node.EndpointTopology;
import com.coobird.staticlogistics.logistics.util.NodeDisplayText;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;
import java.util.function.Function;

/// 配置器和蓝图共用的分组、连接列表。
public final class ConnectionDirectoryModel {
    private static UUID selectedOwner;
    private static long scopeRevision;
    private static Map<UUID, String> ownerNames = Map.of();
    private static Map<UUID, Integer> ownerTeamPermissions = Map.of();
    private static Map<UUID, Integer> ownerPermissions = Map.of();

    /// 权限由服务端计算；未同步或不在列表中的玩家不能推断为有权限。
    public static void acceptOwnerPermissions(CompoundTag data) {
        Map<UUID, Integer> permissions = new HashMap<>();
        CompoundTag entries = data.getCompound("owner_permissions");
        for (String key : entries.getAllKeys()) {
            try {
                int value = entries.getInt(key);
                if (value >= 0 && value <= 2) permissions.put(UUID.fromString(key), value);
            } catch (IllegalArgumentException ignored) {
            }
        }
        ownerPermissions = Map.copyOf(permissions);
        Map<UUID, String> names = new HashMap<>();
        Map<UUID, Integer> teamPermissions = new HashMap<>();
        CompoundTag nameEntries = data.getCompound("owner_names");
        CompoundTag teamEntries = data.getCompound("owner_team_permissions");
        for (UUID owner : permissions.keySet()) {
            String name = nameEntries.getString(owner.toString());
            names.put(owner, name.isBlank() ? owner.toString() : name);
            teamPermissions.put(owner, teamEntries.getInt(owner.toString()));
        }
        ownerNames = Map.copyOf(names);
        ownerTeamPermissions = Map.copyOf(teamPermissions);
        if (selectedOwner != null && !permissions.containsKey(selectedOwner)) {
            selectedOwner = null;
            scopeRevision++;
        }
    }

    public static int ownerPermission(UUID owner) {
        return ownerPermissions.getOrDefault(owner, 0);
    }

    public static void clearScope() {
        selectedOwner = null;
        ownerPermissions = Map.of();
        ownerNames = Map.of();
        ownerTeamPermissions = Map.of();
        scopeRevision++;
    }

    public static boolean teamScope() {
        return selectedOwner == null;
    }

    public static UUID selectedOwner() {
        return selectedOwner;
    }

    public static long scopeRevision() {
        return scopeRevision;
    }

    public static int ownerTeamPermission(UUID owner) {
        return ownerTeamPermissions.getOrDefault(owner, 0);
    }

    public static String ownerName(UUID owner) {
        return ownerNames.getOrDefault(owner, owner.toString());
    }

    public static List<UUID> availableOwners() {
        UUID self = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();
        return ownerPermissions.keySet().stream().filter(owner -> !owner.equals(self))
            .sorted(Comparator.comparing(ConnectionDirectoryModel::ownerName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public static void setOwnerScope(UUID owner) {
        if (!Objects.equals(selectedOwner, owner)) {
            selectedOwner = owner;
            scopeRevision++;
        }
    }

    public static Component scopeLabel() {
        if (selectedOwner == null) return Component.translatable("gui.staticlogistics.directory.all");
        if (Minecraft.getInstance().player != null && selectedOwner.equals(Minecraft.getInstance().player.getUUID()))
            return Component.translatable("gui.staticlogistics.directory.own");
        return Component.literal(ownerName(selectedOwner));
    }

    private static final Comparator<GroupRef> GROUP_ORDER = (first, second) -> {
        String firstName = first.displayName();
        String secondName = second.displayName();
        boolean firstNumber = isDecimal(firstName);
        boolean secondNumber = isDecimal(secondName);
        if (firstNumber && secondNumber) {
            try {
                int byNumber = Integer.compare(
                    Integer.parseInt(firstName),
                    Integer.parseInt(secondName));
                if (byNumber != 0) return byNumber;
            } catch (NumberFormatException ignored) {
                /// 超长数字按普通名称排序。
            }
        } else if (firstNumber != secondNumber) {
            return firstNumber ? -1 : 1;
        }
        int byName = firstName.compareToIgnoreCase(secondName);
        return byName != 0
            ? byName
            : first.key().ownerId().compareTo(second.key().ownerId());
    };

    private ConnectionDirectoryModel() {
    }

    /// 未命名连接直接显示传输两端。
    public static Map<ConnectionKey, String> connectionNames(List<ClientConnection> connections) {
        Map<ConnectionKey, String> names = new LinkedHashMap<>();
        for (ClientConnection connection : connections) {
            if (names.containsKey(connection.key())) continue;
            String name = connection.displayName();
            if (name.isBlank()) {
                boolean reverse = connection.transfersSecondToFirst() && !connection.transfersFirstToSecond();
                String first = endpointName(reverse ? connection.second() : connection.first(), reverse ? connection.secondTopology() : connection.firstTopology());
                String second = endpointName(reverse ? connection.first() : connection.second(), reverse ? connection.firstTopology() : connection.secondTopology());
                String arrow = connection.transfersFirstToSecond() && connection.transfersSecondToFirst() ? " ↔ " : " → ";
                name = first + arrow + second;
            }
            names.put(connection.key(), name);
        }
        return Map.copyOf(names);
    }

    private static String endpointName(LogisticsNode node, EndpointTopology topology) {
        var level = Minecraft.getInstance().level;
        var position = node.gPos().pos();
        if (level != null && level.dimension().equals(node.gPos().dimension())
            && level.getChunkSource().hasChunk(position.getX() >> 4, position.getZ() >> 4)) {
            var state = level.getBlockState(position);
            if (!state.isAir()) return state.getBlock().getName().getString();
        }
        return topology.channelName().isBlank() ? NodeDisplayText.compact(node).getString() : topology.channelName();
    }

    /// 使用连接发送端的方块图标；端点未加载时保留通用连接图标。
    public static ItemStack connectionIcon(ClientConnection connection) {
        boolean reverse = connection.transfersSecondToFirst() && !connection.transfersFirstToSecond();
        LogisticsNode source = reverse ? connection.second() : connection.first();
        var level = Minecraft.getInstance().level;
        var position = source.gPos().pos();
        if (level != null && level.dimension().equals(source.gPos().dimension())
            && level.getChunkSource().hasChunk(position.getX() >> 4, position.getZ() >> 4)) {
            var item = level.getBlockState(position).getBlock().asItem();
            if (item != Items.AIR) return new ItemStack(item);
        }
        return new ItemStack(Items.CHEST);
    }

    /// 两个页面从同一拓扑投影取连接，不再各自维护组列表与连接列表的组合缓存。
    public static List<ClientConnection> connections() {
        return filterAndSort(ClientLinkData.INSTANCE.getAccessibleGroupRefs(), "").stream()
            .filter(group -> selectedOwner == null || group.key().ownerId().equals(selectedOwner))
            .flatMap(group -> ClientLinkData.INSTANCE.getConnectionsForGroup(group.key()).stream())
            .distinct().toList();
    }

    public static List<GroupRef> filterAndSort(
        Collection<GroupRef> groups,
        String searchTerm
    ) {
        String filter = searchTerm.trim().toLowerCase(Locale.ROOT);
        return groups.stream()
            .filter(group -> !group.displayName().isEmpty())
            .filter(group -> SearchMatcher.matches(group.displayName(), filter))
            .sorted(GROUP_ORDER)
            .toList();
    }

    private static boolean isDecimal(String value) {
        if (value.isEmpty()) return false;
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) return false;
        }
        return true;
    }

    public record Row(String id, String name, int depth, ClientConnection connection) {
    }

    public static List<Row> rows(CompoundTag hierarchy, List<ClientConnection> connections, Set<String> collapsed, String search, Function<ClientConnection, String> name) {
        List<Row> rows = new ArrayList<>();
        CompoundTag entries = hierarchy.getCompound("entries");
        List<String> ids = entries.getAllKeys().stream().sorted(Comparator.comparing(id -> entries.getCompound(id).getString("name"))).toList();
        String filter = search.toLowerCase(Locale.ROOT);
        for (String folder : ids) {
            String folderName = entries.getCompound(folder).getString("name");
            boolean folderMatch = SearchMatcher.matches(folderName, filter);
            List<ClientConnection> members = connections.stream().filter(c -> GroupHierarchy.connectionFolder(hierarchy, c.key()).equals(folder))
                .filter(c -> folderMatch || SearchMatcher.matches(name.apply(c), filter)).toList();
            if (!folderMatch && members.isEmpty()) continue;
            rows.add(new Row(folder, folderName, 0, null));
            if (!collapsed.contains(folder) || !filter.isEmpty()) for (var connection : members)
                rows.add(new Row(GroupHierarchy.connectionId(connection.key()), name.apply(connection), 1, connection));
        }
        for (var connection : connections)
            if (!entries.contains(GroupHierarchy.connectionFolder(hierarchy, connection.key())) && SearchMatcher.matches(name.apply(connection), filter)) {
                rows.add(new Row(GroupHierarchy.connectionId(connection.key()), name.apply(connection), 0, connection));
            }
        return List.copyOf(rows);
    }
}
