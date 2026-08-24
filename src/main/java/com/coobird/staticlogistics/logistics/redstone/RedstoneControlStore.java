package com.coobird.staticlogistics.logistics.redstone;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * 服务端权威的连接级红石控制存储。
 */
public final class RedstoneControlStore extends SavedData {
    private static final String DATA_NAME = "static_logistics_redstone_controls";
    private static final int MAX_BINDINGS = 1_000_000;
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<ConnectionKey, RedstoneControlBinding> bindings = new LinkedHashMap<>();
    private final Map<GlobalPos, SignalSample> signalCache = new LinkedHashMap<>();

    private RedstoneControlStore() {
    }

    public static RedstoneControlStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            RedstoneControlStore::load, RedstoneControlStore::new, DATA_NAME);
    }

    @Nullable
    public RedstoneControlBinding getBinding(ConnectionKey connection) {
        return bindings.get(connection);
    }

    public Map<ConnectionKey, RedstoneControlBinding> getBindings(GroupKey groupKey) {
        if (groupKey == null) return Map.of();
        Map<ConnectionKey, RedstoneControlBinding> result = new LinkedHashMap<>();
        bindings.forEach((connection, binding) -> {
            if (groupKey.equals(connection.groupKey())) result.put(connection, binding);
        });
        return Map.copyOf(result);
    }

    public boolean bind(ConnectionKey connection, RedstoneControlBinding binding) {
        if (connection == null || binding == null) return false;
        if (!bindings.containsKey(connection) && bindings.size() >= MAX_BINDINGS) return false;
        RedstoneControlBinding previous = bindings.put(connection, binding);
        if (!binding.equals(previous)) setDirty();
        return true;
    }

    public int bindAll(Collection<ConnectionKey> connections,
                       RedstoneControlBinding binding) {
        if (connections == null || binding == null) return 0;
        LinkedHashSet<ConnectionKey> unique = new LinkedHashSet<>(connections);
        unique.remove(null);
        long additions = unique.stream().filter(connection ->
            !bindings.containsKey(connection)).count();
        if ((long) bindings.size() + additions > MAX_BINDINGS) return 0;
        boolean changed = false;
        for (ConnectionKey connection : unique) {
            changed |= !binding.equals(bindings.put(connection, binding));
        }
        if (changed) setDirty();
        return unique.size();
    }

    public boolean unbind(ConnectionKey connection) {
        if (connection == null || bindings.remove(connection) == null) return false;
        setDirty();
        return true;
    }

    public int unbindGroup(GroupKey groupKey, RedstoneControlBinding binding) {
        if (groupKey == null || binding == null) return 0;
        int before = bindings.size();
        bindings.entrySet().removeIf(entry ->
            groupKey.equals(entry.getKey().groupKey()) && binding.equals(entry.getValue()));
        int removed = before - bindings.size();
        if (removed > 0) setDirty();
        return removed;
    }

    public void removeNode(LogisticsNode node) {
        if (node != null && bindings.keySet().removeIf(key ->
            key.first().equals(node) || key.second().equals(node))) setDirty();
    }

    public void removeGroup(GroupKey groupKey) {
        if (groupKey != null && bindings.keySet().removeIf(key ->
            key.groupKey().equals(groupKey))) setDirty();
    }

    public boolean isAllowed(MinecraftServer server, ConnectionKey connection) {
        RedstoneControlBinding binding = bindings.get(connection);
        return binding == null || binding.mode().allows(isPowered(server, binding.controller()));
    }

    public boolean isPowered(MinecraftServer server, GlobalPos controller) {
        long tick = server.overworld().getGameTime();
        SignalSample cached = signalCache.get(controller);
        if (cached != null && cached.tick() == tick) return cached.powered();
        ServerLevel level = server.getLevel(controller.dimension());
        BlockPos position = controller.pos();
        boolean powered = level != null && level.hasChunkAt(position)
            && (level.getBestNeighborSignal(position) > 0 || emitsSignal(level, position));
        signalCache.put(controller, new SignalSample(tick, powered));
        if (signalCache.size() > 4096) {
            signalCache.entrySet().removeIf(entry -> entry.getValue().tick() != tick);
        }
        return powered;
    }

    private static boolean emitsSignal(ServerLevel level, BlockPos position) {
        for (Direction direction : Direction.values()) {
            if (level.getSignal(position, direction) > 0
                || level.getDirectSignal(position, direction) > 0) return true;
        }
        return false;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        bindings.forEach((connection, binding) -> {
            DataResult<Tag> encodedConnection = ConnectionKey.CODEC.encodeStart(NbtOps.INSTANCE, connection);
            DataResult<Tag> encodedBinding = RedstoneControlBinding.CODEC.encodeStart(NbtOps.INSTANCE, binding);
            encodedConnection.result().ifPresent(connectionTag ->
                encodedBinding.result().ifPresent(bindingTag -> {
                    CompoundTag entry = new CompoundTag();
                    entry.put("connection", connectionTag);
                    entry.put("binding", bindingTag);
                    entries.add(entry);
                }));
        });
        tag.put("bindings", entries);
        return tag;
    }

    private static RedstoneControlStore load(CompoundTag tag) {
        RedstoneControlStore store = new RedstoneControlStore();
        if (!tag.contains("bindings", Tag.TAG_LIST)) return store;
        ListTag entries = tag.getList("bindings", Tag.TAG_COMPOUND);
        int count = Math.min(entries.size(), MAX_BINDINGS);
        for (int index = 0; index < count; index++) {
            CompoundTag entry = entries.getCompound(index);
            if (!entry.contains("connection") || !entry.contains("binding")) continue;
            ConnectionKey.CODEC.parse(NbtOps.INSTANCE, entry.get("connection"))
                .resultOrPartial(message -> LOGGER.warn("Skipping invalid redstone connection: {}", message))
                .ifPresent(connection -> RedstoneControlBinding.CODEC
                    .parse(NbtOps.INSTANCE, entry.get("binding"))
                    .resultOrPartial(message -> LOGGER.warn("Skipping invalid redstone binding: {}", message))
                    .ifPresent(binding -> store.bindings.put(connection, binding)));
        }
        return store;
    }

    private record SignalSample(long tick, boolean powered) {
    }
}
