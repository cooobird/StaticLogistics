package com.coobird.staticlogistics.storage;

import com.coobird.staticlogistics.common.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.common.util.TransferUtils;
import com.coobird.staticlogistics.core.ConnectionMode;
import com.coobird.staticlogistics.core.FaceConfig;
import com.coobird.staticlogistics.core.NodeEntry;
import com.coobird.staticlogistics.core.StaticLink;
import com.coobird.staticlogistics.network.s2c.S2CAddLinksBulkPayload;
import com.coobird.staticlogistics.network.s2c.S2CRemoveLinksBulkPayload;
import com.coobird.staticlogistics.network.s2c.S2CSyncFaceConfigPacket;
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

    public record ActionResult(boolean success, int count, @Nullable MutableComponent message) {
        public static ActionResult fail(MutableComponent msg) {
            return new ActionResult(false, 0, msg);
        }

        public static ActionResult ok(int count) {
            return new ActionResult(true, count, null);
        }
    }

    private void activateSourceOutput(ServerLevel level, BlockPos pos, Direction face, TransferType type, ResourceKey<Level> dim) {
        ServerLevel targetLevel = level.dimension().equals(dim) ? level : level.getServer().getLevel(dim);
        if (targetLevel != null) {
            FaceConfig config = getOrCreateFaceConfig(pos, face, targetLevel);
            if (config.getSettings(type).mode == ConnectionMode.DISABLED) {
                config.getSettings(type).mode = ConnectionMode.OUTPUT;
                syncFaceConfigToAll(targetLevel, pos, face, config);
            }
        }
    }

    private void purgeFaceData(long key, List<UUID> removedIds) {
        faceConfigs.remove(key);
        List<StaticLink> out = links.remove(key);
        if (out != null) {
            out.forEach(l -> {
                removeFromIndex(l);
                removedIds.add(l.linkId());
                updateReverseIndex(l, key);
            });
        }
        if (activeSourceCache.remove(key)) {
            cachedSourceObjects.remove(key);
            LogisticsTicker.wakeup(key);
        }
    }

    public ActionResult executeBatchLink(ServerLevel level, Player player, List<NodeEntry> storedNodes, BlockPos pos, Direction face, TransferType type, String groupId, int priority, LinkConfiguratorItem.ToolMode currentMode) {
        if (storedNodes.isEmpty())
            return ActionResult.fail(Component.translatable("msg.staticlogistics.no_nodes_stored"));

        List<StaticLink> newLinks = new ArrayList<>();
        UUID owner = player.getUUID();
        String ownerName = player.getName().getString();

        for (NodeEntry node : storedNodes) {
            boolean isInput = (currentMode == LinkConfiguratorItem.ToolMode.LINK_AS_INPUT);
            BlockPos srcPos = isInput ? node.pos().pos() : pos;
            Direction srcFace = isInput ? node.face() : face;
            ResourceKey<Level> srcDim = isInput ? node.pos().dimension() : level.dimension();
            BlockPos destPos = isInput ? pos : node.pos().pos();
            Direction destFace = isInput ? face : node.face();
            ResourceKey<Level> destDim = isInput ? level.dimension() : node.pos().dimension();

            activateSourceOutput(level, srcPos, srcFace, type, srcDim);
            newLinks.add(new StaticLink(UUID.randomUUID(), srcPos, srcFace, srcDim, destPos, destFace, destDim, type.getFlag(), priority, owner, ownerName, groupId, 0, true));
        }

        addLinksBulk(newLinks, level, player);
        return ActionResult.ok(newLinks.size());
    }

    public void addLinksBulk(List<StaticLink> newLinks, ServerLevel level, @Nullable Player player) {
        LongSet affected = new LongOpenHashSet();
        for (StaticLink link : newLinks) {
            addLinkInternal(link);
            affected.add(posToKey(link.sourcePos(), link.sourceFace()));
        }
        affected.forEach(this::refreshCache);
        this.setDirty();
        PacketDistributor.sendToPlayersInDimension(level, new S2CAddLinksBulkPayload(newLinks));
    }

    public void removeLinksBulk(List<StaticLink> toRemove, ServerLevel level, @Nullable Player player) {
        if (toRemove.isEmpty()) return;
        List<UUID> removedIds = new ArrayList<>();
        LongSet affectedKeys = new LongOpenHashSet();

        for (StaticLink link : toRemove) {
            long key = posToKey(link.sourcePos(), link.sourceFace());
            List<StaticLink> list = links.get(key);
            if (list != null && list.remove(link)) {
                removedIds.add(link.linkId());
                affectedKeys.add(key);
                removeFromIndex(link);
                updateReverseIndex(link, key);
            }
        }

        if (!removedIds.isEmpty()) {
            affectedKeys.forEach(this::refreshCache);
            this.setDirty();
            PacketDistributor.sendToPlayersInDimension(level, new S2CRemoveLinksBulkPayload(removedIds));
            if (player != null)
                player.displayClientMessage(Component.translatable("msg.staticlogistics.links_removed", removedIds.size()), true);
        }
    }

    public void updateLinkOwner(StaticLink oldLink, UUID newOwner, String newOwnerName, ServerLevel level) {
        StaticLink newLink = new StaticLink(oldLink.linkId(), oldLink.sourcePos(), oldLink.sourceFace(), oldLink.sourceDimension(), oldLink.destPos(), oldLink.destFace(), oldLink.destDimension(), oldLink.transferFlags(), oldLink.priority(), newOwner, newOwnerName, oldLink.groupId(), oldLink.tier(), oldLink.allowCrossDim());
        removeLinksBulk(Collections.singletonList(oldLink), level, null);
        addLinksBulk(Collections.singletonList(newLink), level, null);
    }

    public boolean onBlockRemovedWithResult(BlockPos pos, ServerLevel level) {
        boolean removed = false;
        List<UUID> removedIds = new ArrayList<>();
        LongSet toRefresh = new LongOpenHashSet();
        for (Direction dir : Direction.values()) {
            long key = posToKey(pos, dir);
            if (faceConfigs.containsKey(key) || links.containsKey(key)) {
                purgeFaceData(key, removedIds);
                removed = true;
            }
        }

        LongSet srcKeys = reverseLinks.remove(pos.asLong());
        if (srcKeys != null) {
            for (long srcKey : srcKeys) {
                List<StaticLink> srcLinks = links.get(srcKey);
                if (srcLinks != null) {
                    Iterator<StaticLink> it = srcLinks.iterator();
                    while (it.hasNext()) {
                        StaticLink l = it.next();
                        if (l.destPos().equals(pos) && l.destDimension().equals(level.dimension())) {
                            removedIds.add(l.linkId());
                            it.remove();
                            toRefresh.add(srcKey);
                            removed = true;
                        }
                    }
                }
            }
        }

        if (removed) {
            toRefresh.forEach(this::refreshCache);
            this.setDirty();

            if (!removedIds.isEmpty()) {
                PacketDistributor.sendToPlayersInDimension(level, new S2CRemoveLinksBulkPayload(removedIds));
            }
        }
        return removed;
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
                if (!filtered.isEmpty()) {
                    sortedMap.put(type, filtered);
                    if (config.getSettings(type).mode.allowsOutput()) active = true;
                }
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

    private void addLinkInternal(StaticLink link) {
        long key = posToKey(link.sourcePos(), link.sourceFace());
        List<StaticLink> list = links.computeIfAbsent(key, k -> new ArrayList<>(2));
        if (list.stream().noneMatch(l -> l.destPos().equals(link.destPos()) && l.destFace() == link.destFace() && l.transferFlags() == link.transferFlags())) {
            list.add(link);
            reverseLinks.computeIfAbsent(link.destPos().asLong(), k -> new LongOpenHashSet()).add(key);
            addLinkToIndex(link);
        }
    }

    private void addLinkToIndex(StaticLink link) {
        ownerGroupsIndex.computeIfAbsent(link.owner(), k -> new HashSet<>()).add(link.groupId());
        groupReferenceCounts.computeIfAbsent(link.owner(), k -> new HashMap<>()).merge(link.groupId(), 1, Integer::sum);
    }

    private void removeFromIndex(StaticLink link) {
        Map<String, Integer> counts = groupReferenceCounts.get(link.owner());
        if (counts != null && counts.merge(link.groupId(), -1, Integer::sum) <= 0) {
            counts.remove(link.groupId());
            ownerGroupsIndex.getOrDefault(link.owner(), Collections.emptySet()).remove(link.groupId());
        }
    }

    private void updateReverseIndex(StaticLink link, long srcKey) {
        LongSet rev = reverseLinks.get(link.destPos().asLong());
        if (rev != null && rev.remove(srcKey) && rev.isEmpty()) reverseLinks.remove(link.destPos().asLong());
    }

    public long posToKey(BlockPos pos, Direction face) {
        return (pos.asLong() << 3) | (long) (face.get3DDataValue() & 0x7);
    }

    public FaceConfig getOrCreateFaceConfig(BlockPos pos, Direction face, @Nullable ServerLevel level) {
        long key = posToKey(pos, face);
        return faceConfigs.computeIfAbsent(key, k -> {
            FaceConfig cfg = new FaceConfig();
            cfg.setOnDirty(c -> {
                this.setDirty();
                refreshCache(key);
                if (level != null) syncFaceConfigToAll(level, pos, face, c);
            });
            this.setDirty();
            return cfg;
        });
    }

    public List<StaticLink> getLinksByKey(long key) {
        return links.getOrDefault(key, Collections.emptyList());
    }

    public List<StaticLink> getLinksList() {
        List<StaticLink> all = new ArrayList<>();
        links.values().forEach(all::addAll);
        return all;
    }

    public List<StaticLink> getLinksAt(BlockPos pos) {
        List<StaticLink> found = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            long key = posToKey(pos, dir);
            List<StaticLink> faceLinks = links.get(key);
            if (faceLinks != null) {
                found.addAll(faceLinks);
            }
        }
        return found;
    }

    public void syncAllToPlayer(ServerPlayer player) {
        List<StaticLink> allLinks = getLinksList();
        if (!allLinks.isEmpty()) PacketDistributor.sendToPlayer(player, new S2CAddLinksBulkPayload(allLinks));
        faceConfigs.forEach((key, config) -> {
            if (!config.isDefault()) {
                BlockPos pos = BlockPos.of(key >> 3);
                Direction face = Direction.from3DDataValue((int) (key & 0x7));
                PacketDistributor.sendToPlayer(player, new S2CSyncFaceConfigPacket(pos, face, config));
            }
        });
    }

    public void syncFaceConfigToAll(ServerLevel level, BlockPos pos, Direction face, FaceConfig config) {
        PacketDistributor.sendToPlayersInDimension(level, new S2CSyncFaceConfigPacket(pos, face, config));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag linkList = new ListTag();
        links.values().forEach(list -> list.forEach(l -> StaticLink.CODEC.encodeStart(NbtOps.INSTANCE, l).result().ifPresent(linkList::add)));
        tag.put("links", linkList);
        CompoundTag fConfigs = new CompoundTag();
        faceConfigs.forEach((k, v) -> {
            if (!v.isDefault()) fConfigs.put(k.toString(), v.serializeNBT(provider));
        });
        tag.put("face_configs", fConfigs);
        return tag;
    }

    public static LinkManager load(CompoundTag tag, HolderLookup.Provider provider) {
        LinkManager m = new LinkManager();
        if (tag.contains("links"))
            tag.getList("links", Tag.TAG_COMPOUND).forEach(t -> StaticLink.CODEC.parse(NbtOps.INSTANCE, t).result().ifPresent(m::addLinkInternal));
        if (tag.contains("face_configs")) {
            CompoundTag fTag = tag.getCompound("face_configs");
            fTag.getAllKeys().forEach(s -> {
                long k = Long.parseLong(s);
                FaceConfig cfg = new FaceConfig();
                cfg.deserializeNBT(provider, fTag.getCompound(s));
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

    public LongSet getActiveSourceKeys() {
        return this.activeSourceCache;
    }

    public CachedSourceData getCachedSource(long key) {
        return this.cachedSourceObjects.get(key);
    }

    public Set<String> getGroupsByOwner(UUID owner) {
        return ownerGroupsIndex.getOrDefault(owner, Collections.emptySet());
    }
}