package com.coobird.staticlogistics.storage;

import com.coobird.staticlogistics.common.util.TransferUtils;
import com.coobird.staticlogistics.core.ConnectionMode;
import com.coobird.staticlogistics.core.FaceConfig;
import com.coobird.staticlogistics.core.StaticLink;
import com.coobird.staticlogistics.network.s2c.S2CAddLinkPayload;
import com.coobird.staticlogistics.network.s2c.S2CRemoveLinkPayload;
import com.coobird.staticlogistics.network.s2c.S2CSyncFaceConfigPacket;
import com.coobird.staticlogistics.network.s2c.S2CSyncLinksPacket;
import com.coobird.staticlogistics.transfer.LogisticsTicker;
import com.coobird.staticlogistics.transfer.TransferType;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.*;

public class LinkManager extends SavedData {
    private final Long2ObjectMap<List<StaticLink>> links = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<LongSet> reverseLinks = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<FaceConfig> faceConfigs = new Long2ObjectOpenHashMap<>();
    private final LongSet activeSourceCache = new LongOpenHashSet();
    private final Map<Long, CachedSourceData> cachedSourceObjects = new HashMap<>();

    public record CachedSourceData(
        BlockPos pos,
        Direction face,
        FaceConfig config,
        Map<TransferType, List<StaticLink>> sortedLinks
    ) {
    }

    public LinkManager() {
    }

    public LongSet getActiveSourceKeys() {
        return activeSourceCache;
    }

    public CachedSourceData getCachedSource(long key) {
        return cachedSourceObjects.get(key);
    }

    public List<StaticLink> getLinksByKey(long key) {
        return links.getOrDefault(key, Collections.emptyList());
    }

    public FaceConfig getFaceConfig(BlockPos pos, Direction face) {
        return getOrCreateFaceConfig(pos, face, null);
    }

    public void refreshCache(long key) {
        LogisticsTicker.wakeup(key);
        TransferUtils.clearCache();

        List<StaticLink> linkList = links.get(key);
        FaceConfig config = faceConfigs.get(key);

        if (linkList != null && !linkList.isEmpty() && config != null) {
            Map<TransferType, List<StaticLink>> sortedMap = new EnumMap<>(TransferType.class);
            boolean anyOutputActive = false;

            for (TransferType type : TransferType.values()) {
                List<StaticLink> filteredAndSorted = linkList.stream()
                    .filter(l -> l.hasType(type))
                    .sorted(Comparator.comparingInt(StaticLink::priority).reversed())
                    .toList();
                sortedMap.put(type, filteredAndSorted);
                if (config.getSettings(type).mode.allowsOutput()) anyOutputActive = true;
            }

            if (anyOutputActive) {
                activeSourceCache.add(key);
                cachedSourceObjects.put(key, new CachedSourceData(
                    BlockPos.of(key >> 3),
                    Direction.from3DDataValue((int) (key & 0x7)),
                    config,
                    sortedMap
                ));
                return;
            }
        }
        activeSourceCache.remove(key);
        cachedSourceObjects.remove(key);
    }

    public void addLink(StaticLink newLink, ServerLevel level) {
        addLinkInternal(newLink);
        long key = posToKey(newLink.sourcePos(), newLink.sourceFace());
        FaceConfig config = getOrCreateFaceConfig(newLink.sourcePos(), newLink.sourceFace(), level);

        boolean configChanged = false;
        for (TransferType type : TransferType.values()) {
            if (newLink.hasType(type)) {
                FaceConfig.SideData sideData = config.getSettings(type);
                if (sideData.mode == ConnectionMode.DISABLED) {
                    sideData.mode = ConnectionMode.OUTPUT;
                    configChanged = true;
                }
            }
        }

        refreshCache(key);
        if (configChanged) {
            this.setDirty();
            syncFaceConfigToAll(level, newLink.sourcePos(), newLink.sourceFace(), config);
        }

        PacketDistributor.sendToPlayersInDimension(level, new S2CAddLinkPayload(newLink));
    }

    public void addLinksBulk(List<StaticLink> toAdd, ServerLevel level) {
        if (toAdd.isEmpty()) return;
        LongSet affectedKeys = new LongOpenHashSet();
        for (StaticLink link : toAdd) {
            addLinkInternal(link);
            affectedKeys.add(posToKey(link.sourcePos(), link.sourceFace()));
        }
        for (long key : affectedKeys) refreshCache(key);
        this.setDirty();
        PacketDistributor.sendToPlayersInDimension(level, new S2CSyncLinksPacket(toAdd, false));
    }

    public void addLinkInternal(StaticLink link) {
        long srcKey = posToKey(link.sourcePos(), link.sourceFace());
        List<StaticLink> list = links.computeIfAbsent(srcKey, k -> new ArrayList<>(2));
        if (list.stream().noneMatch(existing -> existing.equals(link))) {
            list.add(link);
            reverseLinks.computeIfAbsent(link.destPos().asLong(), k -> new LongOpenHashSet()).add(srcKey);
            this.setDirty();
        }
    }

    public void removeLink(StaticLink link, ServerLevel level) {
        long srcKey = posToKey(link.sourcePos(), link.sourceFace());
        List<StaticLink> list = links.get(srcKey);
        if (list != null && list.removeIf(l -> l.equals(link))) {
            if (list.isEmpty()) links.remove(srcKey);
            LongSet rev = reverseLinks.get(link.destPos().asLong());
            if (rev != null) {
                rev.remove(srcKey);
                if (rev.isEmpty()) reverseLinks.remove(link.destPos().asLong());
            }
            refreshCache(srcKey);
            this.setDirty();

            PacketDistributor.sendToPlayersInDimension(level, new S2CRemoveLinkPayload(link));
        }
    }

    public void removeLinksBulk(List<StaticLink> toRemove, ServerLevel level) {
        if (toRemove.isEmpty()) return;
        List<StaticLink> actuallyRemoved = new ArrayList<>();
        LongSet affectedKeys = new LongOpenHashSet();
        for (StaticLink link : toRemove) {
            long srcKey = posToKey(link.sourcePos(), link.sourceFace());
            List<StaticLink> list = links.get(srcKey);
            if (list != null && list.removeIf(l -> l.equals(link))) {
                if (list.isEmpty()) links.remove(srcKey);
                LongSet rev = reverseLinks.get(link.destPos().asLong());
                if (rev != null) {
                    rev.remove(srcKey);
                    if (rev.isEmpty()) reverseLinks.remove(link.destPos().asLong());
                }
                affectedKeys.add(srcKey);
                actuallyRemoved.add(link);
            }
        }
        if (!actuallyRemoved.isEmpty()) {
            for (long key : affectedKeys) refreshCache(key);
            this.setDirty();
            actuallyRemoved.forEach(link ->
                PacketDistributor.sendToPlayersInDimension(level, new S2CRemoveLinkPayload(link)));
        }
    }

    public void onBlockRemoved(BlockPos pos, ServerLevel level) {
        List<StaticLink> removedLinks = new ArrayList<>();
        long posLong = pos.asLong();
        TransferUtils.clearCache();

        for (Direction dir : Direction.values()) {
            long key = posToKey(pos, dir);
            List<StaticLink> outLinks = links.remove(key);
            if (outLinks != null) removedLinks.addAll(outLinks);

            faceConfigs.remove(key);
            activeSourceCache.remove(key);
            cachedSourceObjects.remove(key);
            LogisticsTicker.wakeup(key);
        }

        LongSet affectedSources = reverseLinks.remove(posLong);
        if (affectedSources != null) {
            for (long srcKey : affectedSources) {
                List<StaticLink> srcLinks = links.get(srcKey);
                if (srcLinks != null) {
                    srcLinks.stream().filter(l -> l.destPos().equals(pos)).forEach(removedLinks::add);
                    if (srcLinks.removeIf(l -> l.destPos().equals(pos))) {
                        if (srcLinks.isEmpty()) links.remove(srcKey);
                        refreshCache(srcKey);
                    }
                }
            }
        }
        if (!removedLinks.isEmpty()) {
            this.setDirty();
            removedLinks.forEach(link ->
                PacketDistributor.sendToPlayersInDimension(level, new S2CRemoveLinkPayload(link)));
        }
    }

    public FaceConfig getOrCreateFaceConfig(BlockPos pos, Direction face, @Nullable ServerLevel level) {
        long key = posToKey(pos, face);
        return faceConfigs.computeIfAbsent(key, k -> {
            FaceConfig config = new FaceConfig();
            config.setOnDirty(c -> {
                this.setDirty();
                refreshCache(key);
                if (level != null) syncFaceConfigToAll(level, pos, face, c);
            });
            this.setDirty();
            return config;
        });
    }

    public long posToKey(BlockPos pos, Direction face) {
        return (pos.asLong() << 3) | (long) (face.get3DDataValue() & 0x7);
    }

    public List<StaticLink> getLinksList() {
        List<StaticLink> all = new ArrayList<>();
        links.values().forEach(all::addAll);
        return all;
    }

    public void syncLinksToAll(ServerLevel level) {
        S2CSyncLinksPacket packet = new S2CSyncLinksPacket(getLinksList(), true);
        PacketDistributor.sendToPlayersInDimension(level, packet);
    }

    public void syncFaceConfigToAll(ServerLevel level, BlockPos pos, Direction face, FaceConfig config) {
        PacketDistributor.sendToPlayersInDimension(level, new S2CSyncFaceConfigPacket(pos, face, config));
    }

    public void syncAllToPlayer(ServerPlayer player) {
        player.connection.send(new S2CSyncLinksPacket(getLinksList(), true));
        faceConfigs.forEach((key, config) -> {
            BlockPos pos = BlockPos.of(key >> 3);
            Direction face = Direction.from3DDataValue((int) (key & 0x7));
            player.connection.send(new S2CSyncFaceConfigPacket(pos, face, config));
        });
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag linkList = new ListTag();
        links.values().forEach(list -> list.forEach(link ->
            StaticLink.CODEC.encodeStart(NbtOps.INSTANCE, link).result().ifPresent(linkList::add)));
        tag.put("links", linkList);
        CompoundTag fConfigs = new CompoundTag();
        faceConfigs.forEach((k, v) -> fConfigs.put(k.toString(), v.serializeNBT(provider)));
        tag.put("face_configs", fConfigs);
        return tag;
    }

    public static LinkManager load(CompoundTag tag, HolderLookup.Provider provider) {
        LinkManager m = new LinkManager();
        if (tag.contains("links")) {
            tag.getList("links", Tag.TAG_COMPOUND).forEach(t ->
                StaticLink.CODEC.parse(NbtOps.INSTANCE, t).result().ifPresent(m::addLinkInternal));
        }
        if (tag.contains("face_configs")) {
            CompoundTag fTag = tag.getCompound("face_configs");
            for (String keyStr : fTag.getAllKeys()) {
                FaceConfig config = new FaceConfig();
                config.deserializeNBT(provider, fTag.getCompound(keyStr));
                long key = Long.parseLong(keyStr);
                config.setOnDirty(c -> {
                    m.setDirty();
                    m.refreshCache(key);
                });
                m.faceConfigs.put(key, config);
                m.refreshCache(key);
            }
        }
        return m;
    }

    public static final SavedData.Factory<LinkManager> FACTORY = new SavedData.Factory<>(LinkManager::new, LinkManager::load);

    public static LinkManager get(Level level) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) return null;
        return serverLevel.getDataStorage().computeIfAbsent(FACTORY, "static_logistics_links");
    }

    public static LinkManager getByDimension(MinecraftServer server, ResourceKey<Level> dim) {
        ServerLevel target = server.getLevel(dim);
        return target != null ? get(target) : null;
    }
}