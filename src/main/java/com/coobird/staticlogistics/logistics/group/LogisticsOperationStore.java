package com.coobird.staticlogistics.logistics.group;

import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;

/// 跨 tick、离线或重启后仍必须继续的物流操作。
public final class LogisticsOperationStore extends SavedData {
    private static final String DATA_NAME = "static_logistics_operations_v2";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final MinecraftServer server;
    private final Map<UUID, PendingDirectoryDeletion> directoryDeletions = new HashMap<>();
    private final Set<GroupKey> groupDeletions = new LinkedHashSet<>();
    private final Map<UUID, LinkedHashMap<UUID, PendingItemDelivery>> itemDeliveries = new HashMap<>();

    private LogisticsOperationStore(MinecraftServer server) {
        this.server = server;
    }

    public static LogisticsOperationStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(
                () -> new LogisticsOperationStore(server),
                (tag, provider) -> load(server, tag, provider)),
            DATA_NAME);
    }

    @Nullable
    public PendingDirectoryDeletion getDirectoryDeletion(UUID player) {
        return directoryDeletions.get(player);
    }

    public boolean beginDirectoryDeletion(UUID player, String directoryId,
                                          Collection<GroupKey> groups) {
        if (player == null || directoryId == null || directoryId.isEmpty() || groups == null) return false;
        PendingDirectoryDeletion deletion = new PendingDirectoryDeletion(directoryId, Set.copyOf(groups));
        PendingDirectoryDeletion previous = directoryDeletions.putIfAbsent(player, deletion);
        if (previous != null && !previous.directoryId().equals(directoryId)) return false;
        if (previous != null && previous.groups().isEmpty() && !groups.isEmpty()) {
            directoryDeletions.put(player, deletion);
        }
        setDirty();
        return true;
    }

    public void completeDirectoryDeletion(UUID player, String directoryId) {
        PendingDirectoryDeletion current = directoryDeletions.get(player);
        if (current != null && current.directoryId().equals(directoryId)) {
            directoryDeletions.remove(player);
            setDirty();
        }
    }

    public boolean hasDirectoryDeletion(UUID player) {
        return player != null && directoryDeletions.containsKey(player);
    }

    public Set<UUID> directoryDeletionOwners() {
        return Set.copyOf(directoryDeletions.keySet());
    }

    public boolean isDirectoryPending(UUID player, String directoryId) {
        PendingDirectoryDeletion pending = directoryDeletions.get(player);
        if (pending == null || directoryId == null || directoryId.isEmpty()) return false;
        CompoundTag entries = PlayerGroupStore.get(server).getHierarchy(player).getCompound("entries");
        String current = directoryId;
        Set<String> visited = new HashSet<>();
        while (!current.isEmpty() && visited.add(current)) {
            if (pending.directoryId().equals(current)) return true;
            if (!entries.contains(current, Tag.TAG_COMPOUND)) return false;
            current = entries.getCompound(current).getString("parent");
        }
        return false;
    }

    public boolean isConnectionDeletionBlocked(ConnectionKey key) {
        if (key == null) return false;
        return directoryDeletions.entrySet().stream().anyMatch(entry ->
            entry.getValue().groups().contains(key.groupKey())
                && isDirectoryPending(entry.getKey(), GroupHierarchy.connectionFolder(
                PlayerGroupStore.get(server).getHierarchy(entry.getKey()), key)));
    }

    public boolean beginGroupDeletion(GroupKey groupKey) {
        if (groupKey == null) return false;
        boolean added = groupDeletions.add(groupKey);
        if (added) setDirty();
        return added || groupDeletions.contains(groupKey);
    }

    public void completeGroupDeletion(GroupKey groupKey) {
        if (groupDeletions.remove(groupKey)) setDirty();
    }

    public boolean isGroupDeletionPending(GroupKey groupKey) {
        return groupKey != null && groupDeletions.contains(groupKey);
    }

    public Set<GroupKey> groupDeletions() {
        return Set.copyOf(groupDeletions);
    }

    public UUID queueItemDelivery(UUID player, List<ItemStack> stacks) {
        if (player == null || stacks == null || stacks.isEmpty()) return null;
        List<ItemStack> items = stacks.stream()
            .filter(stack -> stack != null && !stack.isEmpty())
            .map(ItemStack::copy).toList();
        if (items.isEmpty()) return null;
        UUID operationId = UUID.randomUUID();
        itemDeliveries.computeIfAbsent(player, ignored -> new LinkedHashMap<>())
            .put(operationId, new PendingItemDelivery(operationId, items));
        setDirty();
        return operationId;
    }

    public void cancelItemDelivery(UUID player, UUID operationId) {
        LinkedHashMap<UUID, PendingItemDelivery> deliveries = itemDeliveries.get(player);
        if (deliveries == null || operationId == null || deliveries.remove(operationId) == null) return;
        if (deliveries.isEmpty()) itemDeliveries.remove(player);
        setDirty();
    }

    public void deliverQueuedItems(ServerPlayer player) {
        if (player == null) return;
        LinkedHashMap<UUID, PendingItemDelivery> deliveries = itemDeliveries.get(player.getUUID());
        if (deliveries == null || deliveries.isEmpty()) return;
        for (UUID operationId : List.copyOf(deliveries.keySet())) {
            deliverItemDelivery(player, operationId);
        }
    }

    public void deliverItemDelivery(ServerPlayer player, UUID operationId) {
        if (player == null || operationId == null) return;
        UUID playerId = player.getUUID();
        LinkedHashMap<UUID, PendingItemDelivery> deliveries = itemDeliveries.get(playerId);
        PendingItemDelivery delivery = deliveries == null ? null : deliveries.get(operationId);
        if (delivery == null) return;
        /// 背包和待领取列表在同一次调用内一起变更；失败时由持有这两份状态的入口恢复。
        List<ItemStack> inventorySnapshot = player.getInventory().items.stream()
            .map(ItemStack::copy).toList();
        List<ItemStack> remaining = new ArrayList<>();
        try {
            for (ItemStack stored : delivery.items()) {
                ItemStack stack = stored.copy();
                player.getInventory().add(stack);
                if (!stack.isEmpty()) remaining.add(stack.copy());
            }
        } catch (RuntimeException exception) {
            for (int slot = 0; slot < inventorySnapshot.size(); slot++) {
                player.getInventory().items.set(slot, inventorySnapshot.get(slot));
            }
            player.getInventory().setChanged();
            LOGGER.error("Failed to return logistics upgrades; items remain available for delivery", exception);
            return;
        }
        if (remaining.isEmpty()) deliveries.remove(operationId);
        else deliveries.put(operationId, new PendingItemDelivery(operationId, remaining));
        if (deliveries.isEmpty()) itemDeliveries.remove(playerId);
        player.getInventory().setChanged();
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag deletions = new CompoundTag();
        directoryDeletions.forEach((player, deletion) -> {
            CompoundTag encoded = new CompoundTag();
            encoded.putString("directory", deletion.directoryId());
            ListTag groups = new ListTag();
            deletion.groups().forEach(group -> GroupKey.CODEC.encodeStart(NbtOps.INSTANCE, group)
                .resultOrPartial(message -> LOGGER.error("Failed to encode deletion group: {}", message))
                .ifPresent(groups::add));
            encoded.put("groups", groups);
            deletions.put(player.toString(), encoded);
        });
        tag.put("directory_deletions", deletions);

        ListTag deletedGroups = new ListTag();
        groupDeletions.forEach(group -> GroupKey.CODEC.encodeStart(NbtOps.INSTANCE, group)
            .resultOrPartial(message -> LOGGER.error("Failed to encode group deletion: {}", message))
            .ifPresent(deletedGroups::add));
        tag.put("group_deletions", deletedGroups);

        CompoundTag deliveries = new CompoundTag();
        itemDeliveries.forEach((player, operations) -> {
            CompoundTag encodedOperations = new CompoundTag();
            operations.forEach((operationId, delivery) -> {
                ListTag items = new ListTag();
                delivery.items().forEach(stack -> items.add(stack.save(provider)));
                encodedOperations.put(operationId.toString(), items);
            });
            deliveries.put(player.toString(), encodedOperations);
        });
        tag.put("item_deliveries", deliveries);
        return tag;
    }

    private static LogisticsOperationStore load(MinecraftServer server, CompoundTag tag,
                                                HolderLookup.Provider provider) {
        LogisticsOperationStore store = new LogisticsOperationStore(server);
        CompoundTag deletions = tag.getCompound("directory_deletions");
        for (String playerText : deletions.getAllKeys()) {
            try {
                UUID player = UUID.fromString(playerText);
                CompoundTag encoded = deletions.getCompound(playerText);
                String directory = encoded.getString("directory");
                if (directory.isEmpty() || directory.length() > GroupConstraints.MAX_HIERARCHY_ID_LENGTH) continue;
                Set<GroupKey> groups = new LinkedHashSet<>();
                ListTag groupTags = encoded.getList("groups", Tag.TAG_COMPOUND);
                for (int index = 0; index < groupTags.size(); index++) {
                    groups.add(GroupKey.CODEC.parse(NbtOps.INSTANCE, groupTags.get(index)).getOrThrow());
                }
                store.directoryDeletions.put(player, new PendingDirectoryDeletion(directory, groups));
            } catch (RuntimeException exception) {
                LOGGER.warn("Skipping invalid pending directory deletion for {}", playerText, exception);
            }
        }
        CompoundTag deliveries = tag.getCompound("item_deliveries");
        for (String playerText : deliveries.getAllKeys()) {
            try {
                UUID player = UUID.fromString(playerText);
                CompoundTag operations = deliveries.getCompound(playerText);
                LinkedHashMap<UUID, PendingItemDelivery> decoded = new LinkedHashMap<>();
                for (String operationText : operations.getAllKeys()) {
                    UUID operationId = UUID.fromString(operationText);
                    ListTag storedItems = operations.getList(operationText, Tag.TAG_COMPOUND);
                    List<ItemStack> items = new ArrayList<>();
                    for (int index = 0; index < storedItems.size(); index++) {
                        ItemStack stack = ItemStack.parseOptional(provider, storedItems.getCompound(index));
                        if (!stack.isEmpty()) items.add(stack);
                    }
                    if (!items.isEmpty()) decoded.put(operationId, new PendingItemDelivery(operationId, items));
                }
                if (!decoded.isEmpty()) store.itemDeliveries.put(player, decoded);
            } catch (RuntimeException exception) {
                LOGGER.warn("Skipping invalid pending item delivery for {}", playerText, exception);
            }
        }
        ListTag deletedGroups = tag.getList("group_deletions", Tag.TAG_COMPOUND);
        for (int index = 0; index < deletedGroups.size(); index++) {
            try {
                store.groupDeletions.add(
                    GroupKey.CODEC.parse(NbtOps.INSTANCE, deletedGroups.get(index)).getOrThrow());
            } catch (RuntimeException exception) {
                LOGGER.warn("Skipping invalid pending group deletion", exception);
            }
        }
        return store;
    }

    public record PendingDirectoryDeletion(String directoryId, Set<GroupKey> groups) {
        public PendingDirectoryDeletion {
            if (directoryId == null || directoryId.isEmpty()) {
                throw new IllegalArgumentException("Directory deletion id is required");
            }
            groups = Set.copyOf(groups);
        }
    }

    public record PendingItemDelivery(UUID operationId, List<ItemStack> items) {
        public PendingItemDelivery {
            if (operationId == null) throw new IllegalArgumentException("Delivery operation id is required");
            items = items.stream().filter(stack -> stack != null && !stack.isEmpty())
                .map(ItemStack::copy).toList();
            if (items.isEmpty()) throw new IllegalArgumentException("Delivery items are required");
        }
    }
}
