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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
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
                                   Map<TransferType, Map<UUID, List<StaticLink>>> groupedLinks) {
    }

    public record ActionResult(boolean success, int count, @Nullable MutableComponent message) {
        public static ActionResult fail(MutableComponent msg) {
            return new ActionResult(false, 0, msg);
        }

        public static ActionResult ok(int count) {
            return new ActionResult(true, count, null);
        }
    }

    public void onBlocksRemovedBulk(Collection<BlockPos> positions, ServerLevel level) {
        List<StaticLink> toRemove = new ArrayList<>();
        for (BlockPos pos : positions) collectLinksAt(pos, toRemove);
        if (!toRemove.isEmpty()) removeLinksBulk(toRemove, level, null);
    }

    public boolean onBlockRemovedWithResult(BlockPos pos, ServerLevel level) {
        List<StaticLink> toRemove = new ArrayList<>();
        collectLinksAt(pos, toRemove);
        if (toRemove.isEmpty()) return false;
        ActionResult result = removeLinksBulk(toRemove, level, null);
        return result.success() && result.count() > 0;
    }

    private void collectLinksAt(BlockPos pos, List<StaticLink> collector) {
        for (Direction dir : Direction.values()) {
            List<StaticLink> outgoing = links.get(posToKey(pos, dir));
            if (outgoing != null) collector.addAll(outgoing);
        }
        LongSet sourceKeys = reverseLinks.get(pos.asLong());
        if (sourceKeys != null) {
            for (long sKey : sourceKeys) {
                List<StaticLink> sLinks = links.get(sKey);
                if (sLinks != null) {
                    for (StaticLink l : sLinks) if (l.destPos().equals(pos)) collector.add(l);
                }
            }
        }
    }

    public void refreshCache(long key) {
        LogisticsTicker.wakeup(key);
        TransferUtils.clearCache();
        List<StaticLink> linkList = links.get(key);
        FaceConfig config = faceConfigs.get(key);

        if (linkList != null && !linkList.isEmpty() && config != null) {
            Map<TransferType, Map<UUID, List<StaticLink>>> typeOwnerMap = new EnumMap<>(TransferType.class);
            boolean hasActiveOutput = false;

            for (TransferType type : TransferType.values()) {
                List<StaticLink> filteredByType = linkList.stream()
                    .filter(l -> l.hasType(type))
                    .sorted(Comparator.comparingInt(StaticLink::priority).reversed())
                    .toList();

                if (!filteredByType.isEmpty()) {
                    Map<UUID, List<StaticLink>> ownerMap = new HashMap<>();
                    for (StaticLink link : filteredByType) {
                        ownerMap.computeIfAbsent(link.owner(), k -> new ArrayList<>()).add(link);
                    }
                    typeOwnerMap.put(type, ownerMap);
                    if (config.getSettings(type).allowsOutput()) hasActiveOutput = true;
                }
            }
            if (hasActiveOutput) {
                activeSourceCache.add(key);
                cachedSourceObjects.put(key, new CachedSourceData(BlockPos.of(key >> 3), Direction.from3DDataValue((int) (key & 0x7)), config, typeOwnerMap));
                return;
            }
        }
        activeSourceCache.remove(key);
        cachedSourceObjects.remove(key);
    }

    public void addLinksBulk(List<StaticLink> newLinks, ServerLevel level, @Nullable Player player) {
        LongSet affected = new LongOpenHashSet();
        for (StaticLink link : newLinks) {
            addLinkInternal(link);
            affected.add(posToKey(link.sourcePos(), link.sourceFace()));
        }
        affected.forEach(this::refreshCache);
        this.setDirty();
        CustomPacketPayload payload = new S2CAddLinksBulkPayload(newLinks);
        if (player instanceof ServerPlayer sp && GroupService.isFtbLoaded()) {
            GroupService.syncToTeamMembers(sp, payload);
        } else {
            PacketDistributor.sendToAllPlayers(payload);
        }
    }

    public ActionResult removeLinksBulk(List<StaticLink> toRemove, ServerLevel level, @Nullable Player player) {
        if (toRemove.isEmpty()) return ActionResult.ok(0);
        List<UUID> removedIds = new ArrayList<>();
        LongSet affectedKeys = new LongOpenHashSet();

        for (StaticLink link : toRemove) {
            if (player != null && !GroupService.canModify(link, player)) continue;
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
            PacketDistributor.sendToAllPlayers(new S2CRemoveLinksBulkPayload(removedIds));
        }
        return ActionResult.ok(removedIds.size());
    }

    public ActionResult removeAllLinksContextual(ServerLevel level, BlockPos pos, Direction face, Player player) {
        List<StaticLink> toRemove = new ArrayList<>();
        long key = posToKey(pos, face);
        List<StaticLink> outgoing = links.get(key);
        if (outgoing != null) toRemove.addAll(outgoing);

        LongSet sourceKeys = reverseLinks.get(pos.asLong());
        if (sourceKeys != null) {
            for (long sKey : sourceKeys) {
                List<StaticLink> sLinks = links.get(sKey);
                if (sLinks != null) {
                    for (StaticLink l : sLinks) {
                        if (l.destPos().equals(pos) && l.destFace() == face) toRemove.add(l);
                    }
                }
            }
        }
        return toRemove.isEmpty() ? ActionResult.ok(0) : removeLinksBulk(toRemove, level, player);
    }

    public ActionResult executeBatchLink(ServerLevel level, Player player, List<NodeEntry> storedNodes, BlockPos currentPos, Direction currentFace, TransferType type, String groupId, int priority, LinkConfiguratorItem.ToolMode mode) {
        List<StaticLink> newLinks = new ArrayList<>();
        MinecraftServer server = level.getServer();
        if (server == null) return ActionResult.fail(null);

        for (NodeEntry node : storedNodes) {
            BlockPos srcPos, dstPos;
            Direction srcFace, dstFace;
            ResourceKey<Level> srcDim, dstDim;

            if (mode == LinkConfiguratorItem.ToolMode.LINK_AS_INPUT) {
                srcPos = node.pos().pos();
                srcFace = node.face();
                srcDim = node.pos().dimension();
                dstPos = currentPos;
                dstFace = currentFace;
                dstDim = level.dimension();
            } else {
                srcPos = currentPos;
                srcFace = currentFace;
                srcDim = level.dimension();
                dstPos = node.pos().pos();
                dstFace = node.face();
                dstDim = node.pos().dimension();
            }

            ServerLevel srcLevel = server.getLevel(srcDim);
            if (srcLevel != null) {
                LinkManager srcMgr = LinkManager.get(srcLevel);
                if (srcMgr != null) {
                    FaceConfig srcCfg = srcMgr.getOrCreateFaceConfig(srcPos, srcFace, srcLevel);
                    srcCfg.getSettings(type).mode = ConnectionMode.OUTPUT;
                    srcCfg.markDirty();
                }
            }

            ServerLevel dstLevel = server.getLevel(dstDim);
            if (dstLevel != null) {
                LinkManager dstMgr = LinkManager.get(dstLevel);
                if (dstMgr != null) {
                    FaceConfig dstCfg = dstMgr.getOrCreateFaceConfig(dstPos, dstFace, dstLevel);
                    dstCfg.getSettings(type).mode = ConnectionMode.INPUT;
                    dstCfg.markDirty();
                }
            }

            newLinks.add(new StaticLink(UUID.randomUUID(), srcPos, srcFace, srcDim, dstPos, dstFace, dstDim, type.getFlag(), priority, player.getUUID(), player.getGameProfile().getName(), groupId, 0, !srcDim.equals(dstDim)));
        }
        if (!newLinks.isEmpty()) addLinksBulk(newLinks, level, player);
        return ActionResult.ok(newLinks.size());
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

    public void updateLinkOwner(StaticLink oldLink, UUID newOwner, String newOwnerName, ServerLevel level) {
        long key = posToKey(oldLink.sourcePos(), oldLink.sourceFace());
        List<StaticLink> list = links.get(key);
        if (list != null && list.remove(oldLink)) {
            removeFromIndex(oldLink);
            StaticLink newLink = new StaticLink(oldLink.linkId(), oldLink.sourcePos(), oldLink.sourceFace(), oldLink.sourceDimension(), oldLink.destPos(), oldLink.destFace(), oldLink.destDimension(), oldLink.transferFlags(), oldLink.priority(), newOwner, newOwnerName, oldLink.groupId(), oldLink.tier(), oldLink.allowCrossDim());
            list.add(newLink);
            addLinkToIndex(newLink);
            this.setDirty();
            refreshCache(key);
            PacketDistributor.sendToAllPlayers(new S2CAddLinksBulkPayload(List.of(newLink)));
        }
    }

    public long posToKey(BlockPos pos, Direction face) {
        return (pos.asLong() << 3) | (long) (face.get3DDataValue() & 0x7);
    }

    public Set<String> getGroupsByOwner(UUID owner) {
        return ownerGroupsIndex.getOrDefault(owner, Collections.emptySet());
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

    public void syncFaceConfigToAll(ServerLevel level, BlockPos pos, Direction face, FaceConfig config) {
        PacketDistributor.sendToPlayersInDimension(level, new S2CSyncFaceConfigPacket(pos, face, config));
    }

    public static void syncAllDimensionsToPlayer(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        List<StaticLink> authorizedLinks = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            LinkManager manager = LinkManager.get(level);
            if (manager == null) continue;
            for (StaticLink link : manager.getLinksList()) {
                if (GroupService.canAccess(link, player)) authorizedLinks.add(link);
            }
        }
        if (!authorizedLinks.isEmpty())
            PacketDistributor.sendToPlayer(player, new S2CSyncLinksPacket(authorizedLinks, true));

        for (ServerLevel level : server.getAllLevels()) {
            LinkManager manager = LinkManager.get(level);
            if (manager == null) continue;
            manager.faceConfigs.forEach((key, config) -> PacketDistributor.sendToPlayer(player, new S2CSyncFaceConfigPacket(BlockPos.of(key >> 3), Direction.from3DDataValue((int) (key & 0x7)), config)));
        }
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

    public static @Nullable LinkManager get(Level level) {
        return (level instanceof ServerLevel sl) ? sl.getDataStorage().computeIfAbsent(FACTORY, "static_logistics_links") : null;
    }

    public LongSet getActiveSourceKeys() {
        return this.activeSourceCache;
    }

    public CachedSourceData getCachedSource(long key) {
        return this.cachedSourceObjects.get(key);
    }

    public List<StaticLink> getLinksList() {
        List<StaticLink> all = new ArrayList<>();
        links.values().forEach(all::addAll);
        return all;
    }

    public List<StaticLink> getLinksByKey(long key) {
        return links.getOrDefault(key, Collections.emptyList());
    }
}