package com.coobird.staticlogistics.storage;

import com.coobird.staticlogistics.SLConfig;
import com.coobird.staticlogistics.common.util.TransferUtils;
import com.coobird.staticlogistics.core.ConnectionMode;
import com.coobird.staticlogistics.core.FaceConfig;
import com.coobird.staticlogistics.core.StaticLink;
import com.coobird.staticlogistics.network.s2c.S2CAddLinksBulkPayload;
import com.coobird.staticlogistics.network.s2c.S2CRemoveLinksBulkPayload;
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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
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

    private final Map<UUID, Set<String>> ownerGroupsIndex = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> groupReferenceCounts = new HashMap<>();

    public record CachedSourceData(BlockPos pos, Direction face, FaceConfig config,
                                   Map<TransferType, List<StaticLink>> sortedLinks) {
    }

    public record ActionResult(boolean success, @Nullable MutableComponent message) {
    }

    public LinkManager() {
    }

    public LongSet getActiveSourceKeys() {
        return this.activeSourceCache;
    }

    @Nullable
    public CachedSourceData getCachedSource(long key) {
        return this.cachedSourceObjects.get(key);
    }

    public Set<String> getGroupsByOwner(UUID ownerId) {
        return ownerGroupsIndex.getOrDefault(ownerId, Collections.emptySet());
    }

    public List<StaticLink> getLinksByPos(BlockPos pos) {
        List<StaticLink> allOut = new ArrayList<>();
        for (Direction dir : Direction.values()) allOut.addAll(getLinksByKey(posToKey(pos, dir)));
        return allOut;
    }

    public void removeLink(StaticLink link, ServerLevel level, Player actor) {
        this.removeLinksBulk(Collections.singletonList(link), level, actor);
    }

    public int removeLinksBulk(List<StaticLink> toRemove, ServerLevel level, Player actor) {
        if (toRemove.isEmpty()) return 0;
        List<StaticLink> removed = new ArrayList<>();
        LongSet affectedKeys = new LongOpenHashSet();
        for (StaticLink template : toRemove) {
            long srcKey = posToKey(template.sourcePos(), template.sourceFace());
            List<StaticLink> list = links.get(srcKey);
            if (list != null) {
                Iterator<StaticLink> it = list.iterator();
                while (it.hasNext()) {
                    StaticLink existing = it.next();
                    boolean match = (existing.linkId() != null && existing.linkId().equals(template.linkId())) || isMatch(existing, template);
                    if (match && GroupService.canModify(existing, actor)) {
                        it.remove();
                        updateReverseIndex(existing, srcKey);
                        removeFromIndex(existing);
                        affectedKeys.add(srcKey);
                        removed.add(existing);
                    }
                }
                if (list.isEmpty()) links.remove(srcKey);
            }
        }
        if (!removed.isEmpty()) {
            affectedKeys.forEach(this::refreshCache);
            this.setDirty();
            PacketDistributor.sendToPlayersInDimension(level, new S2CRemoveLinksBulkPayload(removed));
        }
        return removed.size();
    }

    private void removeFromIndex(StaticLink link) {
        Map<String, Integer> counts = groupReferenceCounts.get(link.owner());
        if (counts != null) {
            int newCount = counts.getOrDefault(link.groupId(), 1) - 1;
            if (newCount <= 0) {
                counts.remove(link.groupId());
                Set<String> groups = ownerGroupsIndex.get(link.owner());
                if (groups != null) {
                    groups.remove(link.groupId());
                    if (groups.isEmpty()) ownerGroupsIndex.remove(link.owner());
                }
            } else {
                counts.put(link.groupId(), newCount);
            }
        }
    }

    private boolean isMatch(StaticLink a, StaticLink b) {
        return a.sourcePos().equals(b.sourcePos()) && a.sourceFace() == b.sourceFace() &&
            a.sourceDimension().equals(b.sourceDimension()) &&
            a.destPos().equals(b.destPos()) && a.destFace() == b.destFace() &&
            a.destDimension().equals(b.destDimension()) &&
            a.groupId().equals(b.groupId());
    }

    private void updateReverseIndex(StaticLink link, long srcKey) {
        LongSet rev = reverseLinks.get(link.destPos().asLong());
        if (rev != null) {
            rev.remove(srcKey);
            if (rev.isEmpty()) reverseLinks.remove(link.destPos().asLong());
        }
    }

    public void updateLinkOwner(StaticLink link, UUID newOwnerUuid, String newOwnerName, ServerLevel level) {
        long srcKey = posToKey(link.sourcePos(), link.sourceFace());
        List<StaticLink> list = links.get(srcKey);
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                StaticLink existing = list.get(i);
                if (existing.linkId().equals(link.linkId())) {
                    PacketDistributor.sendToPlayersInDimension(level, new S2CRemoveLinksBulkPayload(Collections.singletonList(existing)));
                    removeFromIndex(existing);
                    StaticLink updated = new StaticLink(existing.linkId(), existing.sourcePos(), existing.sourceFace(), existing.sourceDimension(),
                        existing.destPos(), existing.destFace(), existing.destDimension(),
                        existing.transferFlags(), existing.priority(), newOwnerUuid, newOwnerName,
                        existing.groupId(), existing.maxRange(), existing.allowCrossDim());
                    list.set(i, updated);
                    addLinkToIndex(updated);
                    PacketDistributor.sendToPlayersInDimension(level, new S2CAddLinksBulkPayload(Collections.singletonList(updated)));
                    this.setDirty();
                    refreshCache(srcKey);
                    break;
                }
            }
        }
    }

    public ActionResult tryAddLink(Player player, BlockPos srcPos, Direction srcFace, ResourceKey<Level> srcDim, BlockPos dstPos, Direction dstFace, ResourceKey<Level> dstDim, TransferType type, String groupId, int priority, ServerLevel level) {
        if (srcPos.equals(dstPos) && srcFace == dstFace && srcDim.equals(dstDim)) {
            return new ActionResult(false, Component.translatable("msg.staticlogistics.cannot_link_self"));
        }
        FaceConfig config = getOrCreateFaceConfig(srcPos, srcFace, level);
        StaticLink newLink = new StaticLink(UUID.randomUUID(), srcPos, srcFace, srcDim, dstPos, dstFace, dstDim, (1 << type.ordinal()), priority, player.getUUID(), player.getGameProfile().getName(), groupId, 1, !srcDim.equals(dstDim));
        if (!newLink.canTransfer(level, config)) {
            int effectiveRange = SLConfig.getDefaultRadius() * config.getMaxRangeMultiplier();
            return new ActionResult(false, Component.translatable("msg.staticlogistics.out_of_range", effectiveRange));
        }
        this.addLink(newLink, level, player);
        return new ActionResult(true, null);
    }

    public void addLink(StaticLink newLink, ServerLevel level, Player player) {
        this.addLinksBulk(Collections.singletonList(newLink), level, player);
    }

    public void addLinksBulk(List<StaticLink> toAdd, ServerLevel level, Player player) {
        if (toAdd.isEmpty()) return;
        LongSet affectedKeys = new LongOpenHashSet();
        for (StaticLink link : toAdd) {
            addLinkInternal(link);
            long key = posToKey(link.sourcePos(), link.sourceFace());
            affectedKeys.add(key);
            autoConfigureFace(link, level);
        }
        affectedKeys.forEach(this::refreshCache);
        this.setDirty();
        PacketDistributor.sendToPlayersInDimension(level, new S2CAddLinksBulkPayload(toAdd));
    }

    private void autoConfigureFace(StaticLink link, ServerLevel level) {
        FaceConfig config = getOrCreateFaceConfig(link.sourcePos(), link.sourceFace(), level);
        boolean changed = false;
        for (TransferType type : TransferType.values()) {
            if (link.hasType(type)) {
                FaceConfig.SideData side = config.getSettings(type);
                if (side.mode == ConnectionMode.DISABLED) {
                    side.mode = ConnectionMode.OUTPUT;
                    changed = true;
                }
            }
        }
        if (changed) syncFaceConfigToAll(level, link.sourcePos(), link.sourceFace(), config);
    }

    public void onBlockRemoved(BlockPos pos, ServerLevel level) {
        List<StaticLink> removed = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            long key = posToKey(pos, dir);
            List<StaticLink> out = links.remove(key);
            if (out != null) {
                out.forEach(this::removeFromIndex);
                removed.addAll(out);
            }
            faceConfigs.remove(key);
            activeSourceCache.remove(key);
            cachedSourceObjects.remove(key);
            LogisticsTicker.wakeup(key);
        }
        LongSet affectedSources = reverseLinks.remove(pos.asLong());
        if (affectedSources != null) {
            for (long srcKey : affectedSources) {
                List<StaticLink> srcLinks = links.get(srcKey);
                if (srcLinks != null) {
                    srcLinks.removeIf(l -> {
                        if (l.destPos().equals(pos) && l.destDimension().equals(level.dimension())) {
                            removeFromIndex(l);
                            return true;
                        }
                        return false;
                    });
                    if (srcLinks.isEmpty()) links.remove(srcKey);
                    refreshCache(srcKey);
                }
            }
        }
        if (!removed.isEmpty()) {
            this.setDirty();
            PacketDistributor.sendToPlayersInDimension(level, new S2CRemoveLinksBulkPayload(removed));
        }
    }

    public void refreshCache(long key) {
        LogisticsTicker.wakeup(key);
        TransferUtils.clearCache();
        List<StaticLink> linkList = links.get(key);
        FaceConfig config = faceConfigs.get(key);
        if (linkList != null && !linkList.isEmpty() && config != null) {
            Map<TransferType, List<StaticLink>> sortedMap = new EnumMap<>(TransferType.class);
            boolean active = false;
            for (TransferType type : TransferType.values()) {
                List<StaticLink> filtered = linkList.stream().filter(l -> l.hasType(type)).sorted(Comparator.comparingInt(StaticLink::priority).reversed()).toList();
                sortedMap.put(type, filtered);
                if (config.getSettings(type).mode.allowsOutput()) active = true;
            }
            if (active) {
                activeSourceCache.add(key);
                cachedSourceObjects.put(key, new CachedSourceData(BlockPos.of(key >> 3), Direction.from3DDataValue((int) (key & 0x7)), config, sortedMap));
                return;
            }
        }
        activeSourceCache.remove(key);
        cachedSourceObjects.remove(key);
    }

    public void syncAllToPlayer(ServerPlayer player) {
        this.syncLinksFull(player);
        faceConfigs.forEach((key, config) -> {
            BlockPos pos = BlockPos.of(key >> 3);
            Direction face = Direction.from3DDataValue((int) (key & 0x7));
            PacketDistributor.sendToPlayer(player, new S2CSyncFaceConfigPacket(pos, face, config));
        });
    }

    public void syncLinksFull(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new S2CSyncLinksPacket(this.getLinksList(), true));
    }

    public void syncFaceConfigToAll(ServerLevel level, BlockPos pos, Direction face, FaceConfig config) {
        PacketDistributor.sendToPlayersInDimension(level, new S2CSyncFaceConfigPacket(pos, face, config));
    }

    public long posToKey(BlockPos pos, Direction face) {
        return (pos.asLong() << 3) | (long) (face.get3DDataValue() & 0x7);
    }

    public List<StaticLink> getLinksList() {
        List<StaticLink> all = new ArrayList<>();
        links.values().forEach(all::addAll);
        return all;
    }

    public List<StaticLink> getLinksByKey(long key) {
        return links.getOrDefault(key, Collections.emptyList());
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

    private void addLinkInternal(StaticLink link) {
        long srcKey = posToKey(link.sourcePos(), link.sourceFace());
        List<StaticLink> list = links.computeIfAbsent(srcKey, k -> new ArrayList<>(2));
        if (list.stream().noneMatch(l -> l.linkId().equals(link.linkId()) || isMatch(l, link))) {
            list.add(link);
            reverseLinks.computeIfAbsent(link.destPos().asLong(), k -> new LongOpenHashSet()).add(srcKey);
            addLinkToIndex(link);
        }
    }

    private void addLinkToIndex(StaticLink link) {
        ownerGroupsIndex.computeIfAbsent(link.owner(), k -> new HashSet<>()).add(link.groupId());
        groupReferenceCounts.computeIfAbsent(link.owner(), k -> new HashMap<>())
            .merge(link.groupId(), 1, Integer::sum);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag linkList = new ListTag();
        links.values().forEach(list -> list.forEach(link -> StaticLink.CODEC.encodeStart(NbtOps.INSTANCE, link).result().ifPresent(linkList::add)));
        tag.put("links", linkList);
        CompoundTag fConfigs = new CompoundTag();
        faceConfigs.forEach((k, v) -> fConfigs.put(k.toString(), v.serializeNBT(provider)));
        tag.put("face_configs", fConfigs);
        return tag;
    }

    public static LinkManager load(CompoundTag tag, HolderLookup.Provider provider) {
        LinkManager m = new LinkManager();
        if (tag.contains("links"))
            tag.getList("links", Tag.TAG_COMPOUND).forEach(t -> StaticLink.CODEC.parse(NbtOps.INSTANCE, t).result().ifPresent(m::addLinkInternal));
        if (tag.contains("face_configs")) {
            CompoundTag fTag = tag.getCompound("face_configs");
            fTag.getAllKeys().forEach(keyStr -> {
                FaceConfig cfg = new FaceConfig();
                cfg.deserializeNBT(provider, fTag.getCompound(keyStr));
                long k = Long.parseLong(keyStr);
                cfg.setOnDirty(c -> {
                    m.setDirty();
                    m.refreshCache(k);
                });
                m.faceConfigs.put(k, cfg);
                m.refreshCache(k);
            });
        }
        return m;
    }

    public static final SavedData.Factory<LinkManager> FACTORY = new SavedData.Factory<>(LinkManager::new, LinkManager::load);

    public static LinkManager get(Level level) {
        return (level instanceof ServerLevel sl) ? sl.getDataStorage().computeIfAbsent(FACTORY, "static_logistics_links") : null;
    }
}