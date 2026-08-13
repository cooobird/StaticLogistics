package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.ExtractionMode;
import com.coobird.staticlogistics.logistics.group.GroupService;
import com.coobird.staticlogistics.logistics.group.OwnershipMutationPermit;
import com.coobird.staticlogistics.logistics.node.persistence.ConfigSerializer;
import com.coobird.staticlogistics.logistics.node.persistence.LogisticsDataMigration;
import com.coobird.staticlogistics.transfer.LogisticsCalculator;
import com.coobird.staticlogistics.transfer.LogisticsResource;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 面复合配置 —— 组合 FaceConfig + LinkConfig + FilterConfig 三个子配置，
 * 管理链接节点集合、全局输入/输出开关、目标缓存和序列化。
 * 这是整个物流系统最核心的数据模型。
 */
public class FaceConfigComposite {
    private static final LinkMutationPermit SNAPSHOT_DECODE_PERMIT = new LinkMutationPermit();
    public static final int MAX_LINKS_PER_FACE = 4_096;
    public final FaceConfig faceConfig;
    public final LinkConfig linkConfig;
    public final FilterConfig filterConfig;
    private final TransferTypeSelectionConfig typeSelectionConfig;
    private ContainerConfig sharedContainerConfig;

    private final Map<GroupKey, LinkedHashSet<LogisticsNode>> linkedNodesByGroup = new LinkedHashMap<>();
    private final Set<LogisticsNode> linkedNodes = new ScopedUnionSet();
    private long version = 0;
    private Consumer<FaceConfigComposite> onDirty = (c) -> {
    };
    private final AggregateChangeTracker changeTracker;

    private boolean globalInputEnabled = false;
    private boolean globalOutputEnabled = false;

    public FaceConfigComposite() {
        this.changeTracker = new AggregateChangeTracker(this::publishChange);
        this.faceConfig = new FaceConfig();
        this.linkConfig = new LinkConfig();
        this.filterConfig = new FilterConfig();
        this.typeSelectionConfig = new TransferTypeSelectionConfig();
        setupDirtyCallback();
    }

    /**
     * 将网络数据解码到全新的快照对象，不接触任何实时节点配置。
     */
    public static FaceConfigComposite decodeSnapshot(HolderLookup.Provider provider, CompoundTag tag) {
        FaceConfigComposite config = new FaceConfigComposite();
        config.deserializeNBT(SNAPSHOT_DECODE_PERMIT, provider, tag);
        return config;
    }

    /**
     * 以全新解码的聚合快照替换当前全部持久状态。
     * 该入口专供事务回滚，避免把旧快照增量合并进已经发生修改的对象。
     */
    void restoreSnapshot(Object permit, HolderLookup.Provider provider, CompoundTag tag) {
        FaceConfigComposite snapshot = decodeSnapshot(provider, tag);
        try (BulkEdit ignored = beginBulkEdit()) {
            faceConfig.restoreSnapshot(permit, snapshot.faceConfig);
            linkConfig.restoreSnapshot(snapshot.linkConfig);
            filterConfig.restoreSnapshot(snapshot.filterConfig);
            typeSelectionConfig.restoreSnapshot(snapshot.typeSelectionConfig);
            linkedNodesByGroup.clear();
            snapshot.linkedNodesByGroup.forEach((group, nodes) ->
                linkedNodesByGroup.put(group, new LinkedHashSet<>(nodes)));
            globalInputEnabled = snapshot.globalInputEnabled;
            globalOutputEnabled = snapshot.globalOutputEnabled;
            version = snapshot.version;
            markDirty();
        }
    }

    private void setupDirtyCallback() {
        this.faceConfig.setOnDirty(c -> markDirty());
        this.linkConfig.setOnDirty(c -> markDirty());
        this.filterConfig.setOnDirty(c -> markDirty());
        this.typeSelectionConfig.setOnDirty(c -> markDirty());
    }

    public BulkEdit beginBulkEdit() {
        return new BulkEdit(changeTracker.begin());
    }

    public static final class BulkEdit implements AutoCloseable {
        private final AggregateChangeTracker.Scope scope;
        private boolean closed;

        private BulkEdit(AggregateChangeTracker.Scope scope) {
            this.scope = scope;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                scope.close();
            }
        }
    }

    public void markDirty() {
        changeTracker.markChanged();
    }

    private void publishChange() {
        version++;
        if (onDirty != null) onDirty.accept(this);
    }

    /**
     * 仅设置运行时位置元数据，不产生持久化变更。
     */
    public void setPosition(BlockPos pos) {
        faceConfig.setPos(pos);
    }

    @Nullable
    public ContainerConfig getContainerConfig() {
        return sharedContainerConfig;
    }

    /**
     * 由配置服务绑定同一方块共享的容器级配置。
     */
    public void attachContainerConfig(@Nullable ContainerConfig containerConfig) {
        sharedContainerConfig = containerConfig;
    }

    /**
     * 解绑共享容器配置，并同步移除其面索引。
     */
    public void detachContainerConfig(FaceAddress faceKey) {
        if (sharedContainerConfig != null) {
            sharedContainerConfig.unlinkFace(faceKey);
            sharedContainerConfig = null;
        }
    }

    public void setOwner(Object permit, UUID owner, String ownerName, @Nullable GameProfile profile) {
        UUID previousOwner = faceConfig.getOwner();
        if (previousOwner != null || owner == null || linkedNodesByGroup.isEmpty()) {
            faceConfig.setOwner(permit, owner, ownerName, profile);
            return;
        }
        boolean hasForeignScope = linkedNodesByGroup.keySet().stream()
            .anyMatch(key -> !key.isLegacyUnowned());
        if (hasForeignScope) {
            throw new IllegalStateException("Unowned face contains a foreign group scope");
        }
        Map<GroupKey, LinkedHashSet<LogisticsNode>> claimedScopes = new LinkedHashMap<>();
        linkedNodesByGroup.forEach((key, nodes) -> claimedScopes
            .computeIfAbsent(key.withOwner(owner), ignored -> new LinkedHashSet<>()).addAll(nodes));
        try (BulkEdit ignored = beginBulkEdit()) {
            faceConfig.setOwner(permit, owner, ownerName, profile);
            linkedNodesByGroup.clear();
            linkedNodesByGroup.putAll(claimedScopes);
        }
    }

    public void setOwnerProfileTag(Object permit, CompoundTag tag) {
        faceConfig.setOwnerProfileTag(permit, tag);
    }

    public void addGroup(Object permit, GroupRef group) {
        faceConfig.addGroup(permit, group);
    }

    public void addLegacyGroup(Object permit, String displayName) {
        faceConfig.addGroupId(permit, displayName);
    }

    public void removeGroup(LinkMutationPermit permit, GroupKey groupKey) {
        faceConfig.removeGroup(permit, groupKey);
    }

    public void renameGroup(LinkMutationPermit permit, GroupKey groupKey, String displayName) {
        faceConfig.renameGroup(permit, groupKey, displayName);
    }

    /**
     * 将同一所有者下的来源分组身份及其链接作用域并入目标分组。
     */
    public void mergeGroup(LinkMutationPermit permit, GroupRef source, GroupRef target) {
        if (permit == null) throw new IllegalArgumentException("Link mutation permit is required");
        if (source == null || target == null) {
            throw new IllegalArgumentException("Source and target groups are required");
        }
        if (!source.key().ownerId().equals(target.key().ownerId())) {
            throw new IllegalArgumentException("Group merge requires the same owner");
        }
        if (source.key().equals(target.key())
            || !faceConfig.containsGroup(source.key())) return;

        try (BulkEdit ignored = beginBulkEdit()) {
            faceConfig.addGroup(permit, target);
            faceConfig.renameGroup(permit, target.key(), target.displayName());
            LinkedHashSet<LogisticsNode> sourceLinks = linkedNodesByGroup.remove(source.key());
            if (sourceLinks != null && !sourceLinks.isEmpty()) {
                linkedNodesByGroup
                    .computeIfAbsent(target.key(), key -> new LinkedHashSet<>())
                    .addAll(sourceLinks);
                markDirty();
            }
            faceConfig.removeGroup(permit, source.key());
        }
    }

    public boolean canPlayerAccess(Player player) {
        return GroupService.canAccess(faceConfig.getOwner(), player);
    }

    public boolean canPlayerModify(Player player) {
        return GroupService.canModify(faceConfig.getOwner(), player);
    }

    public void setOnDirty(@Nullable Consumer<FaceConfigComposite> onDirty) {
        this.onDirty = onDirty;
    }

    public Set<LogisticsNode> getLinkedNodes() {
        return Collections.unmodifiableSet(linkedNodes);
    }

    public boolean hasLinkedNodes() {
        return !linkedNodesByGroup.isEmpty();
    }

    public void addLinkedNode(LinkMutationPermit permit, GroupKey groupKey, LogisticsNode node) {
        if (permit == null) throw new IllegalArgumentException("Link mutation permit is required");
        if (groupKey == null || node == null) return;
        if (!faceConfig.containsGroup(groupKey)) {
            throw new IllegalArgumentException("Link group does not belong to face owner");
        }
        LinkedHashSet<LogisticsNode> scoped =
            linkedNodesByGroup.computeIfAbsent(groupKey, ignored -> new LinkedHashSet<>());
        if (!linkedNodes.contains(node) && linkedNodes.size() >= MAX_LINKS_PER_FACE) {
            throw new IllegalStateException("Face link limit exceeded");
        }
        if (scoped.add(node)) {
            markDirty();
        }
    }

    /**
     * 原子迁移面所有者，并保持全部分组内部身份和链接作用域一致。
     */
    public void transferOwnership(OwnershipMutationPermit permit, UUID newOwner, String ownerName,
                                  @Nullable GameProfile profile) {
        if (permit == null) throw new IllegalArgumentException("Ownership mutation permit is required");
        UUID previousOwner = faceConfig.getOwner();
        if (previousOwner == null || newOwner == null) {
            throw new IllegalStateException("Ownership transfer requires existing and new owners");
        }
        if (previousOwner.equals(newOwner)) {
            faceConfig.setOwner(permit, newOwner, ownerName, profile);
            return;
        }

        validateOwnershipTransfer(previousOwner);
        Map<GroupKey, LinkedHashSet<LogisticsNode>> remapped = new LinkedHashMap<>();
        for (var scope : linkedNodesByGroup.entrySet()) {
            GroupKey newKey = scope.getKey().withOwner(newOwner);
            remapped.computeIfAbsent(newKey, ignored -> new LinkedHashSet<>()).addAll(scope.getValue());
        }

        try (BulkEdit ignored = beginBulkEdit()) {
            linkedNodesByGroup.clear();
            linkedNodesByGroup.putAll(remapped);
            faceConfig.transferOwnership(newOwner, ownerName, profile);
        }
    }

    public void restoreOwnershipSnapshot(OwnershipMutationPermit permit, HolderLookup.Provider provider,
                                         CompoundTag snapshot) {
        if (permit == null) throw new IllegalArgumentException("Ownership mutation permit is required");
        try (BulkEdit ignored = beginBulkEdit()) {
            faceConfig.resetForOwnershipRestore(permit);
            deserializeNBT(permit, provider, snapshot);
            markDirty();
        }
    }

    public void validateOwnershipTransfer(UUID expectedOwner) {
        if (expectedOwner == null || !expectedOwner.equals(faceConfig.getOwner())) {
            throw new IllegalStateException("Face owner changed before ownership transfer");
        }
        boolean invalidScope = linkedNodesByGroup.keySet().stream()
            .anyMatch(groupKey -> !expectedOwner.equals(groupKey.ownerId()));
        if (invalidScope) {
            throw new IllegalStateException("Link scope owner does not match face owner");
        }
    }

    public Set<LogisticsNode> getLinkedNodes(GroupKey groupKey) {
        Set<LogisticsNode> nodes = linkedNodesByGroup.get(groupKey);
        return nodes == null ? Set.of() : Collections.unmodifiableSet(nodes);
    }

    public Map<GroupKey, Set<LogisticsNode>> getLinkedNodesByGroup() {
        Map<GroupKey, Set<LogisticsNode>> result = new LinkedHashMap<>();
        linkedNodesByGroup.forEach((key, value) -> result.put(key, Collections.unmodifiableSet(value)));
        return Collections.unmodifiableMap(result);
    }

    /**
     * 遍历内部链接作用域，不创建分组映射快照。回调只接收节点值，无法修改聚合根状态。
     */
    public void forEachLinkedNode(BiConsumer<GroupKey, LogisticsNode> consumer) {
        Objects.requireNonNull(consumer, "Linked node consumer must not be null");
        linkedNodesByGroup.forEach((groupKey, nodes) ->
            nodes.forEach(node -> consumer.accept(groupKey, node)));
    }

    public boolean removeLinkedNode(LinkMutationPermit permit, GroupKey groupKey, LogisticsNode node) {
        if (permit == null) throw new IllegalArgumentException("Link mutation permit is required");
        LinkedHashSet<LogisticsNode> nodes = linkedNodesByGroup.get(groupKey);
        if (nodes == null || !nodes.remove(node)) return false;
        if (nodes.isEmpty()) linkedNodesByGroup.remove(groupKey);
        disableRolesIfDisconnected();
        markDirty();
        return true;
    }

    /**
     * 从所有分组作用域移除指定节点，仅供链接生命周期管理层使用。
     */
    public boolean removeLinkedNode(LinkMutationPermit permit, LogisticsNode node) {
        if (permit == null) throw new IllegalArgumentException("Link mutation permit is required");
        boolean changed = linkedNodes.remove(node);
        if (changed) disableRolesIfDisconnected();
        return changed;
    }

    /**
     * 无连接的面不得保留输入或输出角色。
     * 该约束放在聚合根中，保证所有链接删除入口共享同一行为。
     */
    private void disableRolesIfDisconnected() {
        if (!linkedNodesByGroup.isEmpty()) return;
        setGlobalInputEnabled(false);
        setGlobalOutputEnabled(false);
    }

    public boolean isGlobalInputEnabled() {
        return globalInputEnabled;
    }

    /**
     * 开启或关闭当前面的全局输入角色。
     */
    public void setGlobalInputEnabled(boolean enabled) {
        if (this.globalInputEnabled != enabled) {
            this.globalInputEnabled = enabled;
            markDirty();
        }
    }

    public boolean isGlobalOutputEnabled() {
        return globalOutputEnabled;
    }

    /**
     * 开启或关闭当前面的全局输出角色。
     */
    public void setGlobalOutputEnabled(boolean enabled) {
        if (this.globalOutputEnabled != enabled) {
            this.globalOutputEnabled = enabled;
            markDirty();
        }
    }

    public void setDistributionStrategy(DistributionStrategy strategy) {
        linkConfig.setStrategy(strategy);
    }

    public void setExtractionMode(ExtractionMode mode) {
        linkConfig.setExtractionMode(mode);
    }

    public void setPriority(int priority) {
        linkConfig.setPriority(priority);
    }

    public void setKeepStock(int keepStock) {
        linkConfig.setKeepStock(keepStock);
    }

    /**
     * 根据全局输入/输出开关判断节点角色（发送/接收/双向/无）
     */
    public NodeRole determineRole() {
        boolean canSend = globalOutputEnabled;
        boolean canReceive = globalInputEnabled;
        if (canSend && canReceive) return NodeRole.BOTH;
        if (canSend) return NodeRole.SENDER;
        if (canReceive) return NodeRole.RECEIVER;
        return NodeRole.NONE;
    }

    /**
     * 计算考虑了容器升级（堆叠倍率）后的实际传输限制。
     * 委托给 LogisticsCalculator.calcTransferLimit() 统一计算。
     */
    public long getTransferLimit(LogisticsResource<?> type) {
        if (sharedContainerConfig == null) {
            return type.getBaseStackSize();
        }
        return LogisticsCalculator.calcTransferLimit(type, sharedContainerConfig.getStackMultiplier());
    }

    public long getVersion() {
        return version;
    }

    /**
     * 设置版本号（用于新建配置时继承 LinkManager 的全局计数器，确保跨对象版本单调递增）
     */
    public void setVersion(long v) {
        this.version = v;
    }

    /**
     * 序列化为 NBT（含版本、全局开关、链接节点）
     */
    public CompoundTag serializeNBT(HolderLookup.Provider p) {
        CompoundTag tag = ConfigSerializer.serializeNBT(this, p);
        tag.putLong("version", version);
        tag.putBoolean("globalInput", globalInputEnabled);
        tag.putBoolean("globalOutput", globalOutputEnabled);
        if (!linkedNodesByGroup.isEmpty()) {
            CompoundTag scopedTag = new CompoundTag();
            int scopeIndex = 0;
            for (var scope : linkedNodesByGroup.entrySet()) {
                CompoundTag scopeTag = new CompoundTag();
                scopeTag.putUUID("owner", scope.getKey().ownerId());
                scopeTag.putUUID("internal", scope.getKey().internalId());
                CompoundTag nodesTag = new CompoundTag();
                int nodeIndex = 0;
                for (LogisticsNode node : scope.getValue()) {
                    nodesTag.put(String.valueOf(nodeIndex++),
                        LogisticsNode.CODEC.encodeStart(NbtOps.INSTANCE, node)
                            .getOrThrow(false, message -> {
                                throw new IllegalStateException(message);
                            }));
                }
                scopeTag.put("nodes", nodesTag);
                scopedTag.put(String.valueOf(scopeIndex++), scopeTag);
            }
            tag.put("linkedNodesByGroup", scopedTag);
        } else if (!linkedNodes.isEmpty()) {
            CompoundTag nodesTag = new CompoundTag();
            int i = 0;
            for (LogisticsNode node : linkedNodes) {
                nodesTag.put(String.valueOf(i++), LogisticsNode.CODEC.encodeStart(NbtOps.INSTANCE, node)
                    .getOrThrow(false, message -> {
                        throw new IllegalStateException(message);
                    }));
            }
            tag.put("linkedNodes", nodesTag);
        }
        return tag;
    }

    /**
     * 从 NBT 反序列化
     */
    public void deserializeNBT(Object permit, HolderLookup.Provider p, CompoundTag nbt) {
        if (!(permit instanceof LinkMutationPermit)
            && !(permit instanceof OwnershipMutationPermit)) {
            throw new IllegalArgumentException("Configuration deserialization permit is required");
        }
        CompoundTag migrated = LogisticsDataMigration.migrateFace(nbt);
        globalInputEnabled = migrated.getBoolean("globalInput");
        globalOutputEnabled = migrated.getBoolean("globalOutput");
        ConfigSerializer.deserializeMigratedNBT(permit, this, p, migrated);
        if (migrated.contains("version")) version = migrated.getLong("version");
        linkedNodesByGroup.clear();
        if (migrated.contains("linkedNodesByGroup")) {
            CompoundTag scopedTag = migrated.getCompound("linkedNodesByGroup");
            if (scopedTag.getAllKeys().size()
                > GroupConstraints.MAX_GROUPS_PER_OWNER) {
                throw new IllegalStateException("Face link scope limit exceeded");
            }
            int decodedLinks = 0;
            for (String scopeIndex : scopedTag.getAllKeys()) {
                CompoundTag scopeTag = scopedTag.getCompound(scopeIndex);
                if (!scopeTag.hasUUID("owner") || !scopeTag.hasUUID("internal")) continue;
                GroupKey groupKey = new GroupKey(scopeTag.getUUID("owner"), scopeTag.getUUID("internal"));
                if (!faceConfig.containsGroup(groupKey)) continue;
                CompoundTag nodesTag = scopeTag.getCompound("nodes");
                decodedLinks = Math.addExact(decodedLinks, nodesTag.getAllKeys().size());
                if (decodedLinks > MAX_LINKS_PER_FACE) {
                    throw new IllegalStateException("Face link limit exceeded");
                }
                for (String key : nodesTag.getAllKeys()) {
                    LogisticsNode.CODEC.parse(NbtOps.INSTANCE, nodesTag.get(key)).result()
                        .ifPresent(node -> linkedNodesByGroup
                            .computeIfAbsent(groupKey, ignored -> new LinkedHashSet<>()).add(node));
                }
            }
        } else if (migrated.contains("linkedNodes")) {
            CompoundTag nodesTag = migrated.getCompound("linkedNodes");
            if (nodesTag.getAllKeys().size() > MAX_LINKS_PER_FACE) {
                throw new IllegalStateException("Face link limit exceeded");
            }
            for (String key : nodesTag.getAllKeys()) {
                LogisticsNode.CODEC.parse(NbtOps.INSTANCE, nodesTag.get(key)).resultOrPartial(err -> {
                }).ifPresent(node -> {
                    for (GroupKey groupKey : faceConfig.getGroupKeys()) {
                        linkedNodesByGroup.computeIfAbsent(groupKey, ignored -> new LinkedHashSet<>()).add(node);
                    }
                });
            }
        }
        disableRolesIfDisconnected();
    }

    public boolean isDefault() {
        return faceConfig.isDefault() && linkConfig.isDefault() && filterConfig.isDefault() &&
            (sharedContainerConfig == null || sharedContainerConfig.isDefault()) &&
            linkedNodes.isEmpty() && typeSelectionConfig.isDefault() && !globalInputEnabled && !globalOutputEnabled;
    }

    public List<ResourceLocation> getSelectedTypeIds() {
        return typeSelectionConfig.getSelectedTypeIds();
    }

    public void setSelectedTypeIds(Collection<ResourceLocation> ids) {
        typeSelectionConfig.setSelectedTypeIds(ids);
    }

    /**
     * 仅用于写出旧格式兼容投影，不作为运行时权威状态。
     */
    public int getLegacySelectedTypesMask() {
        return typeSelectionConfig.getLegacyMask();
    }

    /**
     * 仅用于没有类型 ID 列表的旧数据迁移。
     */
    public void loadLegacySelectedTypesMask(int mask) {
        typeSelectionConfig.setLegacyMask(mask);
    }

    /**
     * 已有稳定 ID 列表时，只保留当前注册表无法解析的历史位。
     */
    public void loadUnresolvedLegacySelectedTypesMask(int mask) {
        typeSelectionConfig.loadUnresolvedLegacyMask(mask);
    }

    public boolean isTypeSelected(LogisticsResource<?> type) {
        return typeSelectionConfig.isTypeSelected(type);
    }

    /**
     * 汇总所有分组作用域的内部并集视图。对外只暴露只读包装。
     */
    private final class ScopedUnionSet extends AbstractSet<LogisticsNode> {
        private LinkedHashSet<LogisticsNode> snapshot() {
            LinkedHashSet<LogisticsNode> result = new LinkedHashSet<>();
            linkedNodesByGroup.values().forEach(result::addAll);
            return result;
        }

        @Override
        public Iterator<LogisticsNode> iterator() {
            return snapshot().iterator();
        }

        @Override
        public int size() {
            return snapshot().size();
        }

        @Override
        public boolean contains(Object value) {
            return linkedNodesByGroup.values().stream().anyMatch(nodes -> nodes.contains(value));
        }

        @Override
        public boolean remove(Object value) {
            boolean changed = false;
            Iterator<LinkedHashSet<LogisticsNode>> iterator = linkedNodesByGroup.values().iterator();
            while (iterator.hasNext()) {
                LinkedHashSet<LogisticsNode> nodes = iterator.next();
                changed |= nodes.remove(value);
                if (nodes.isEmpty()) iterator.remove();
            }
            if (changed) markDirty();
            return changed;
        }

    }
}
