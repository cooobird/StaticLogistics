package com.coobird.staticlogistics.storage;

import com.coobird.staticlogistics.transfer.TransferSettings;
import com.coobird.staticlogistics.core.StaticLink;
import com.coobird.staticlogistics.core.TransferType;
import com.coobird.staticlogistics.network.s2c.S2CSyncLinksPacket;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class LinkManager extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Long2ObjectMap<List<StaticLink>> links = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<TransferSettings> posSettings = new Long2ObjectOpenHashMap<>();

    public LinkManager() {}

    public void addLink(StaticLink newLink) {
        long key = posToKey(newLink.sourcePos(), newLink.sourceFace());
        List<StaticLink> list = links.computeIfAbsent(key, k -> new ArrayList<>());
        for (int i = 0; i < list.size(); i++) {
            StaticLink existing = list.get(i);

            if (existing.destPos().equals(newLink.destPos()) &&
                existing.destFace() == newLink.destFace() &&
                existing.groupId() == newLink.groupId()) {

                int combinedFlags = existing.transferFlags() | newLink.transferFlags();
                list.set(i, new StaticLink(
                    newLink.sourcePos(),
                    newLink.sourceFace(),
                    newLink.destPos(),
                    newLink.destFace(),
                    combinedFlags,
                    newLink.priority(),
                    newLink.groupId()
                ));
                this.setDirty();
                return;
            }
        }
        list.add(newLink);
        this.setDirty();
    }

    public boolean smartRemoveLinks(BlockPos pos, Direction face) {
        long sourceKey = posToKey(pos, face);
        boolean removed = links.remove(sourceKey) != null;

        for (List<StaticLink> linkList : links.values()) {
            removed |= linkList.removeIf(link -> link.destPos().equals(pos) && link.destFace() == face);
        }

        if (removed) {
            for (TransferType type : TransferType.values()) {
                posSettings.remove(settingsKey(pos, face, type));
            }
            this.setDirty();
        }
        return removed;
    }

    public void onBlockRemoved(BlockPos pos) {
        boolean anyRemoved = false;
        for (Direction face : Direction.values()) {
            anyRemoved |= smartRemoveLinks(pos, face);
        }
        if (anyRemoved) {
            this.setDirty();
        }
    }

    public void removeLink(StaticLink linkToRemove) {
        long key = posToKey(linkToRemove.sourcePos(), linkToRemove.sourceFace());
        List<StaticLink> list = links.get(key);
        if (list == null) return;

        for (int i = 0; i < list.size(); i++) {
            StaticLink existing = list.get(i);
            if (existing.destPos().equals(linkToRemove.destPos()) &&
                existing.destFace() == linkToRemove.destFace() &&
                existing.groupId() == linkToRemove.groupId()) {

                int newFlags = existing.transferFlags() & ~linkToRemove.transferFlags();

                if (newFlags <= 0) {
                    list.remove(i);
                } else {
                    list.set(i, new StaticLink(
                        existing.sourcePos(), existing.sourceFace(),
                        existing.destPos(), existing.destFace(),
                        newFlags, existing.priority(),
                        existing.groupId()
                    ));
                }
                this.setDirty();
                return;
            }
        }
    }

    public TransferSettings getSettings(long sourceKey, TransferType type) {
        BlockPos pos = BlockPos.of(sourceKey >> 3);
        Direction face = Direction.from3DDataValue((int)(sourceKey & 0x7));
        return posSettings.getOrDefault(settingsKey(pos, face, type), TransferSettings.DEFAULT);
    }

    public void setSettings(BlockPos pos, Direction face, TransferType type, TransferSettings settings) {
        posSettings.put(settingsKey(pos, face, type), settings);
        this.setDirty();
    }

    public LongSet getAllSourceKeys() {
        return links.keySet();
    }

    public @Nullable List<StaticLink> getLinksByKey(long key) {
        return links.get(key);
    }

    public @Nullable StaticLink getLinkBetween(BlockPos sPos, Direction sFace, BlockPos dPos, Direction dFace, int groupId) {
        List<StaticLink> list = links.get(posToKey(sPos, sFace));
        if (list == null) return null;
        return list.stream()
            .filter(l -> l.destPos().equals(dPos) && l.destFace() == dFace && l.groupId() == groupId)
            .findFirst().orElse(null);
    }

    public List<StaticLink> getLinksList() {
        return links.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
    }

    private long posToKey(BlockPos pos, Direction face) {
        return (pos.asLong() << 3) | (long) (face.get3DDataValue() & 0x7);
    }

    private long settingsKey(BlockPos pos, Direction face, TransferType type) {
        return (pos.asLong() << 7) | ((long) (face.get3DDataValue() & 0x7) << 4) | (long) (type.ordinal() & 0xF);
    }

    public void syncToPlayer(ServerPlayer player) {
        player.connection.send(new S2CSyncLinksPacket(getLinksList()));
    }

    public void syncToAll(ServerLevel level) {
        S2CSyncLinksPacket packet = new S2CSyncLinksPacket(getLinksList());
        level.players().forEach(p -> p.connection.send(packet));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag linkList = new ListTag();
        for (List<StaticLink> list : links.values()) {
            for (StaticLink link : list) {
                StaticLink.CODEC.encodeStart(NbtOps.INSTANCE, link)
                    .resultOrPartial(err -> LOGGER.error("Link save error: {}", err))
                    .ifPresent(linkList::add);
            }
        }
        tag.put("links", linkList);

        CompoundTag settingsTag = new CompoundTag();
        posSettings.forEach((key, setting) -> {
            TransferSettings.CODEC.encodeStart(NbtOps.INSTANCE, setting)
                .resultOrPartial(err -> LOGGER.error("Settings save error: {}", err))
                .ifPresent(t -> settingsTag.put(String.valueOf(key), t));
        });
        tag.put("settings", settingsTag);

        return tag;
    }

    public static LinkManager load(CompoundTag tag, HolderLookup.Provider provider) {
        LinkManager manager = new LinkManager();
        if (tag.contains("links", Tag.TAG_LIST)) {
            ListTag list = tag.getList("links", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                StaticLink.CODEC.parse(NbtOps.INSTANCE, list.getCompound(i))
                    .resultOrPartial(err -> LOGGER.error("Link load error: {}", err))
                    .ifPresent(manager::addLink);
            }
        }
        if (tag.contains("settings", Tag.TAG_COMPOUND)) {
            CompoundTag sTag = tag.getCompound("settings");
            for (String k : sTag.getAllKeys()) {
                TransferSettings.CODEC.parse(NbtOps.INSTANCE, sTag.get(k))
                    .resultOrPartial(err -> LOGGER.error("Settings load error: {}", err))
                    .ifPresent(s -> manager.posSettings.put(Long.parseLong(k), s));
            }
        }
        return manager;
    }

    public static final SavedData.Factory<LinkManager> FACTORY = new SavedData.Factory<>(
        LinkManager::new,
        LinkManager::load,
        null
    );

    public static LinkManager get(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.getDataStorage().computeIfAbsent(FACTORY, "static_logistics_links");
        }
        throw new IllegalStateException("LinkManager is server-side only!");
    }
}