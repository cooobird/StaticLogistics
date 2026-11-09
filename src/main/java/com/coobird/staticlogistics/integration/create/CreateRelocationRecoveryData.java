package com.coobird.staticlogistics.integration.create;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/// 保存未完成的机械动力换址，避免冲突期间或服务器重启后把旧节点当作孤儿删除。
final class CreateRelocationRecoveryData extends SavedData {
    private static final String DATA_NAME = "static_logistics_create_relocations_v2";
    private static final int MAX_MOVES_PER_RELOCATION = 1_000_000;

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final Map<BlockPos, Integer> sourceReferences = new LinkedHashMap<>();

    static CreateRelocationRecoveryData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new Factory<>(CreateRelocationRecoveryData::new,
                (tag, provider) -> load(tag)), DATA_NAME);
    }

    List<Entry> entries() {
        return List.copyOf(entries.values());
    }

    boolean containsSource(BlockPos position) {
        return sourceReferences.containsKey(position);
    }

    void add(Map<BlockPos, BlockPos> moves, Map<Direction, Direction> faces) {
        if (moves.isEmpty()) return;
        for (Entry entry : entries.values()) {
            if (entry.moves().equals(moves)) return;
        }
        if (moves.size() > MAX_MOVES_PER_RELOCATION) {
            throw new IllegalStateException("Create relocation recovery limit exceeded");
        }
        addEntry(moves, faces);
        setDirty();
    }

    private void addEntry(Map<BlockPos, BlockPos> moves,
                          Map<Direction, Direction> faces) {
        UUID id = UUID.randomUUID();
        Entry entry = new Entry(id, moves, faces);
        entries.put(id, entry);
        entry.moves().keySet().forEach(source -> sourceReferences.merge(source, 1, Integer::sum));
    }

    void remove(UUID id) {
        Entry removed = entries.remove(id);
        if (removed != null) {
            removed.moves().keySet().forEach(source -> sourceReferences.computeIfPresent(
                source, (ignored, count) -> count <= 1 ? null : count - 1));
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag storedEntries = new ListTag();
        for (Entry entry : entries.values()) {
            CompoundTag storedEntry = new CompoundTag();
            storedEntry.putUUID("id", entry.id());
            ListTag moves = new ListTag();
            entry.moves().forEach((before, after) -> {
                CompoundTag move = new CompoundTag();
                move.putLong("before", before.asLong());
                move.putLong("after", after.asLong());
                moves.add(move);
            });
            storedEntry.put("moves", moves);
            CompoundTag faces = new CompoundTag();
            entry.faces().forEach((before, after) ->
                faces.putInt(before.getName(), after.get3DDataValue()));
            storedEntry.put("faces", faces);
            storedEntries.add(storedEntry);
        }
        tag.put("entries", storedEntries);
        return tag;
    }

    private static CreateRelocationRecoveryData load(CompoundTag tag) {
        CreateRelocationRecoveryData data = new CreateRelocationRecoveryData();
        if (!tag.contains("entries", Tag.TAG_LIST)) return data;
        ListTag storedEntries = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int index = 0; index < storedEntries.size(); index++) {
            CompoundTag storedEntry = storedEntries.getCompound(index);
            if (!storedEntry.hasUUID("id") || !storedEntry.contains("moves", Tag.TAG_LIST)) continue;
            ListTag storedMoves = storedEntry.getList("moves", Tag.TAG_COMPOUND);
            if (storedMoves.isEmpty() || storedMoves.size() > MAX_MOVES_PER_RELOCATION) continue;
            Map<BlockPos, BlockPos> moves = new LinkedHashMap<>();
            for (int moveIndex = 0; moveIndex < storedMoves.size(); moveIndex++) {
                CompoundTag move = storedMoves.getCompound(moveIndex);
                if (!move.contains("before", Tag.TAG_LONG)
                    || !move.contains("after", Tag.TAG_LONG)) continue;
                moves.put(BlockPos.of(move.getLong("before")), BlockPos.of(move.getLong("after")));
            }
            if (moves.isEmpty()) continue;
            Map<Direction, Direction> faces = new EnumMap<>(Direction.class);
            CompoundTag storedFaces = storedEntry.getCompound("faces");
            for (Direction direction : Direction.values()) {
                if (storedFaces.contains(direction.getName(), Tag.TAG_INT)) {
                    faces.put(direction, Direction.from3DDataValue(
                        storedFaces.getInt(direction.getName())));
                }
            }
            UUID id = storedEntry.getUUID("id");
            if (!data.entries.containsKey(id)) data.addEntry(id, moves, faces);
        }
        return data;
    }

    private void addEntry(UUID id, Map<BlockPos, BlockPos> moves,
                          Map<Direction, Direction> faces) {
        Entry entry = new Entry(id, moves, faces);
        entries.put(id, entry);
        entry.moves().keySet().forEach(source -> sourceReferences.merge(source, 1, Integer::sum));
    }

    record Entry(UUID id, Map<BlockPos, BlockPos> moves,
                 Map<Direction, Direction> faces) {
        Entry {
            moves = Map.copyOf(moves);
            faces = Map.copyOf(faces);
        }
    }
}
