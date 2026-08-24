package com.coobird.staticlogistics.logistics.redstone;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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
            new SavedData.Factory<>(RedstoneControlStore::new,
                (tag, provider) -> load(tag)),
            DATA_NAME);
    }

    @Nullable
    public RedstoneControlBinding getBinding(ConnectionKey connection) {
        return bindings.get(connection);
    }

    /**
     * 返回一个物流分组内已绑定红石控制的连接快照。
     */
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

    /**
     * 原子地把多条连接并入同一个控制集合，容量不足时不留下部分绑定。
     */
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

    /**
     * 解除一个物流分组内共享同一检测点和模式的整套红石控制。
     */
    public int unbindGroup(GroupKey groupKey, RedstoneControlBinding binding) {
        if (groupKey == null || binding == null) return 0;
        int before = bindings.size();
        bindings.entrySet().removeIf(entry ->
            groupKey.equals(entry.getKey().groupKey())
                && binding.equals(entry.getValue()));
        int removed = before - bindings.size();
        if (removed > 0) setDirty();
        return removed;
    }

    public void removeNode(LogisticsNode node) {
        if (node != null && bindings.keySet().removeIf(key ->
            key.first().equals(node) || key.second().equals(node))) {
            setDirty();
        }
    }

    public void removeGroup(GroupKey groupKey) {
        if (groupKey != null && bindings.keySet().removeIf(key -> key.groupKey().equals(groupKey))) {
            setDirty();
        }
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
            && (level.getBestNeighborSignal(position) > 0
            || emitsSignal(level, position));
        signalCache.put(controller, new SignalSample(tick, powered));
        if (signalCache.size() > 4096) {
            signalCache.entrySet().removeIf(entry -> entry.getValue().tick() != tick);
        }
        return powered;
    }

    /**
     * 同时支持点选受电方块和拉杆、按钮、中继器等主动输出方块。
     */
    private static boolean emitsSignal(ServerLevel level, BlockPos position) {
        for (Direction direction : Direction.values()) {
            if (level.getSignal(position, direction) > 0
                || level.getDirectSignal(position, direction) > 0) return true;
        }
        return false;
    }

    /**
     * Sable 装配或拆卸后同步连接端点以及控制检测点。
     */
    public Set<GroupKey> remapNodes(MinecraftServer server,
                                    Map<LogisticsNode, LogisticsNode> replacements,
                                    GlobalPos oldController, GlobalPos newController) {
        Map<GlobalPos, GlobalPos> controllerMoves = oldController == null || newController == null
            ? Map.of() : Map.of(oldController, newController);
        return remapNodes(server, replacements, controllerMoves);
    }

    /**
     * 原子重映射整座刚性结构，避免坐标互换时丢失红石控制绑定。
     */
    public Set<GroupKey> remapNodes(MinecraftServer server,
                                    Map<LogisticsNode, LogisticsNode> replacements,
                                    Map<GlobalPos, GlobalPos> controllerMoves) {
        if ((replacements == null || replacements.isEmpty())
            && (controllerMoves == null || controllerMoves.isEmpty())) return Set.of();
        Map<ConnectionKey, RedstoneControlBinding> remapped = new LinkedHashMap<>();
        Set<GroupKey> changedGroups = new LinkedHashSet<>();
        boolean changed = false;
        for (var entry : bindings.entrySet()) {
            ConnectionKey key = entry.getKey();
            LogisticsNode first = replacements == null
                ? key.first() : replacements.getOrDefault(key.first(), key.first());
            LogisticsNode second = replacements == null
                ? key.second() : replacements.getOrDefault(key.second(), key.second());
            ConnectionKey nextKey = first.equals(key.first()) && second.equals(key.second())
                ? key : new ConnectionKey(key.groupKey(), first, second);
            RedstoneControlBinding binding = entry.getValue();
            GlobalPos nextController = controllerMoves == null
                ? binding.controller() : controllerMoves.getOrDefault(binding.controller(), binding.controller());
            RedstoneControlBinding nextBinding = nextController.equals(binding.controller())
                ? binding : new RedstoneControlBinding(nextController, binding.mode());
            boolean entryChanged = nextKey != key || nextBinding != binding;
            changed |= entryChanged;
            if (entryChanged) changedGroups.add(key.groupKey());
            remapped.put(nextKey, nextBinding);
        }
        if (changed) {
            bindings.clear();
            bindings.putAll(remapped);
            signalCache.clear();
            setDirty();
        }
        return Set.copyOf(changedGroups);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
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
