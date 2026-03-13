package com.coobird.staticlogistics.storage;

import com.coobird.staticlogistics.core.ConnectionMode;
import com.coobird.staticlogistics.core.FaceConfig;
import com.coobird.staticlogistics.core.StaticLink;
import com.coobird.staticlogistics.core.TransferType;
import com.coobird.staticlogistics.network.s2c.S2CSyncLinksPacket;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LinkManager extends SavedData {
    private final Long2ObjectMap<List<StaticLink>> links = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<LongSet> reverseLinks = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<FaceConfig> faceConfigs = new Long2ObjectOpenHashMap<>();

    public LinkManager() {
    }

    public void addLink(StaticLink newLink) {
        addLinkInternal(newLink);
        FaceConfig config = getOrCreateFaceConfig(newLink.sourcePos(), newLink.sourceFace());
        boolean modified = false;
        for (TransferType type : TransferType.values()) {
            if (newLink.hasType(type)) {
                FaceConfig.SideData sideData = config.getSettings(type);
                if (sideData.mode == ConnectionMode.DISABLED) {
                    sideData.mode = ConnectionMode.OUTPUT;
                    modified = true;
                }
            }
        }
        if (modified) this.setDirty();
    }

    private void addLinkInternal(StaticLink link) {
        long srcKey = posToKey(link.sourcePos(), link.sourceFace());
        List<StaticLink> list = links.computeIfAbsent(srcKey, k -> new ArrayList<>(2));
        boolean exists = false;
        for (StaticLink existing : list) {
            if (existing.equals(link)) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            list.add(link);
            reverseLinks.computeIfAbsent(link.destPos().asLong(), k -> new LongOpenHashSet()).add(srcKey);
            this.setDirty();
        }
    }

    public void removeLink(StaticLink link) {
        long srcKey = posToKey(link.sourcePos(), link.sourceFace());
        List<StaticLink> list = links.get(srcKey);
        if (list != null && list.remove(link)) {
            if (list.isEmpty()) links.remove(srcKey);
            LongSet sources = reverseLinks.get(link.destPos().asLong());
            if (sources != null) {
                sources.remove(srcKey);
                if (sources.isEmpty()) reverseLinks.remove(link.destPos().asLong());
            }
            this.setDirty();
        }
    }

    public boolean smartRemoveLinks(BlockPos pos, Direction face) {
        long key = posToKey(pos, face);
        List<StaticLink> removed = links.remove(key);
        boolean changed = removed != null;
        if (changed) {
            for (StaticLink l : removed) {
                LongSet sources = reverseLinks.get(l.destPos().asLong());
                if (sources != null) sources.remove(key);
            }
        }
        if (faceConfigs.remove(key) != null) changed = true;
        if (changed) this.setDirty();
        return changed;
    }

    public void onBlockRemoved(BlockPos pos) {
        boolean changed = false;
        long posLong = pos.asLong();
        for (int i = 0; i < 6; i++) {
            if (smartRemoveLinks(pos, Direction.from3DDataValue(i))) changed = true;
        }
        LongSet affectedSources = reverseLinks.remove(posLong);
        if (affectedSources != null) {
            for (long srcKey : affectedSources) {
                List<StaticLink> srcLinks = links.get(srcKey);
                if (srcLinks != null && srcLinks.removeIf(l -> l.destPos().equals(pos))) {
                    changed = true;
                    if (srcLinks.isEmpty()) links.remove(srcKey);
                }
            }
        }
        if (changed) this.setDirty();
    }

    public FaceConfig getOrCreateFaceConfig(BlockPos pos, Direction face) {
        long key = posToKey(pos, face);
        return faceConfigs.computeIfAbsent(key, k -> {
            FaceConfig config = new FaceConfig();
            config.setOnDirty(c -> this.setDirty());
            this.setDirty();
            return config;
        });
    }

    private long posToKey(BlockPos pos, Direction face) {
        return (pos.asLong() << 3) | (long) (face.get3DDataValue() & 0x7);
    }

    public void syncToAll(ServerLevel level) {
        S2CSyncLinksPacket packet = new S2CSyncLinksPacket(getLinksList());
        level.getServer().getPlayerList().getPlayers().forEach(p -> p.connection.send(packet));
    }

    public void syncToPlayer(ServerPlayer player) {
        player.connection.send(new S2CSyncLinksPacket(getLinksList()));
    }

    public List<StaticLink> getLinksList() {
        List<StaticLink> all = new ArrayList<>();
        for (List<StaticLink> subList : links.values()) all.addAll(subList);
        return all;
    }

    public LongSet getAllSourceKeys() {
        return links.keySet();
    }

    public List<StaticLink> getLinksByKey(long key) {
        return links.getOrDefault(key, Collections.emptyList());
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag linkList = new ListTag();
        for (List<StaticLink> list : links.values()) {
            for (StaticLink link : list) {
                StaticLink.CODEC.encodeStart(NbtOps.INSTANCE, link).result().ifPresent(linkList::add);
            }
        }
        tag.put("links", linkList);
        CompoundTag fConfigs = new CompoundTag();
        faceConfigs.forEach((k, v) -> fConfigs.put(k.toString(), v.serializeNBT(provider)));
        tag.put("face_configs", fConfigs);
        return tag;
    }

    public static LinkManager load(CompoundTag tag, HolderLookup.Provider provider) {
        LinkManager m = new LinkManager();
        if (tag.contains("links")) {
            ListTag list = tag.getList("links", Tag.TAG_COMPOUND);
            for (Tag t : list) StaticLink.CODEC.parse(NbtOps.INSTANCE, t).result().ifPresent(m::addLinkInternal);
        }
        if (tag.contains("face_configs")) {
            CompoundTag fTag = tag.getCompound("face_configs");
            for (String key : fTag.getAllKeys()) {
                FaceConfig config = new FaceConfig();
                config.deserializeNBT(provider, fTag.getCompound(key));
                try {
                    m.faceConfigs.put(Long.parseLong(key), config);
                } catch (Exception ignored) {
                }
            }
        }
        return m;
    }

    public static final SavedData.Factory<LinkManager> FACTORY = new SavedData.Factory<>(LinkManager::new, LinkManager::load);

    public static LinkManager get(Level level) {
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        return (overworld != null ? overworld : (ServerLevel) level).getDataStorage().computeIfAbsent(FACTORY, "static_logistics_links");
    }
}