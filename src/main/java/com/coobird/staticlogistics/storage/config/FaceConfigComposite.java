package com.coobird.staticlogistics.storage.config;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.config.serializer.ConfigSerializer;
import com.coobird.staticlogistics.core.service.GroupService;
import com.coobird.staticlogistics.util.LogisticsConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class FaceConfigComposite {
    public final FaceConfig faceConfig;
    public final LinkConfig linkConfig;
    public final FilterConfig filterConfig;
    public ContainerConfig sharedContainerConfig;

    private final Set<LogisticsNode> linkedNodes = ConcurrentHashMap.newKeySet();
    private int selectedTypesMask = 0;
    private int version = 0;
    private Consumer<FaceConfigComposite> onDirty = (c) -> {
    };

    private boolean globalInputEnabled = false;
    private boolean globalOutputEnabled = false;

    private List<LogisticsNode> cachedTargets = null;
    private int targetsCacheVersion = -1;
    private static final Map<Integer, List<LogisticsNode>> globalTargetCache = new ConcurrentHashMap<>();

    public FaceConfigComposite() {
        this.faceConfig = new FaceConfig();
        this.linkConfig = new LinkConfig();
        this.filterConfig = new FilterConfig();
        setupDirtyCallback();
    }

    private void setupDirtyCallback() {
        this.faceConfig.setOnDirty(c -> markDirty());
        this.linkConfig.setOnDirty(c -> markDirty());
        this.filterConfig.setOnDirty(c -> markDirty());
    }

    public void markDirty() {
        version++;
        targetsCacheVersion = -1;
        if (onDirty != null) onDirty.accept(this);
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
        return linkedNodes;
    }

    public void addLinkedNode(LogisticsNode node) {
        if (linkedNodes.add(node)) markDirty();
    }

    public void removeLinkedNode(LogisticsNode node) {
        if (linkedNodes.remove(node)) markDirty();
    }

    public boolean isGlobalInputEnabled() {
        return globalInputEnabled;
    }

    public void setGlobalInputEnabled(boolean enabled) {
        if (this.globalInputEnabled != enabled) {
            this.globalInputEnabled = enabled;
            if (enabled && linkConfig.getInputChannel() == LinkConfig.DISABLED_CHANNEL) {
                linkConfig.setInputChannel(LinkConfig.MIN_CHANNEL);
            }
            markDirty();
        }
    }

    public boolean isGlobalOutputEnabled() {
        return globalOutputEnabled;
    }

    public void setGlobalOutputEnabled(boolean enabled) {
        if (this.globalOutputEnabled != enabled) {
            this.globalOutputEnabled = enabled;
            if (enabled && linkConfig.getOutputChannel() == LinkConfig.DISABLED_CHANNEL) {
                linkConfig.setOutputChannel(LinkConfig.MIN_CHANNEL);
            }
            markDirty();
        }
    }

    public NodeRole determineRole() {
        boolean canSend = globalOutputEnabled;
        boolean canReceive = globalInputEnabled;
        if (canSend && canReceive) return NodeRole.BOTH;
        if (canSend) return NodeRole.SENDER;
        if (canReceive) return NodeRole.RECEIVER;
        return NodeRole.NONE;
    }

    public int getTransferLimit(TransferType type) {
        if (sharedContainerConfig == null) {
            return Math.min(type.getBaseStackSize(), SLConfig.getMaxTransferLimit());
        }
        int stackMult = sharedContainerConfig.getStackMultiplier();
        long limit = (long) type.getBaseStackSize() * stackMult;
        int maxAllowed = SLConfig.getMaxTransferLimit();
        if (limit > maxAllowed) return maxAllowed;
        return (int) limit;
    }

    public int getVersion() {
        return version;
    }

    void setVersion(int version) {
        this.version = version;
    }

    @Nullable
    public List<LogisticsNode> getCachedTargets(int currentVersion) {
        if (cachedTargets != null && targetsCacheVersion == currentVersion) {
            return new ArrayList<>(cachedTargets);
        }
        return null;
    }

    public void setCachedTargets(List<LogisticsNode> targets, int currentVersion) {
        if (targets != null && !targets.isEmpty()) {
            int cacheSize = Math.min(targets.size(), LogisticsConstants.Cache.getTargetCacheSize());
            this.cachedTargets = new ArrayList<>(targets.subList(0, cacheSize));
            this.targetsCacheVersion = currentVersion;

            addToGlobalCache(currentVersion, this.cachedTargets);
        } else {
            clearCache();
        }
    }

    private void addToGlobalCache(int version, List<LogisticsNode> targets) {
        if (globalTargetCache.size() >= LogisticsConstants.Cache.getGlobalTargetCacheSize()) {
            evictOldestGlobalCacheConcurrent();
        }
        globalTargetCache.put(version, new ArrayList<>(targets));
    }

    private void evictOldestGlobalCache() {
        if (!globalTargetCache.isEmpty()) {
            Integer oldestKey = globalTargetCache.keySet().iterator().next();
            globalTargetCache.remove(oldestKey);
        }
    }

    private void evictOldestGlobalCacheConcurrent() {
        globalTargetCache.keySet().stream()
            .min(Integer::compareTo)
            .ifPresent(globalTargetCache::remove);
    }

    private void clearCache() {
        this.cachedTargets = null;
        this.targetsCacheVersion = -1;
    }

    public static void clearGlobalCache() {
        globalTargetCache.clear();
    }

    public static int getGlobalCacheSize() {
        return globalTargetCache.size();
    }

    public CompoundTag serializeNBT(HolderLookup.Provider p) {
        CompoundTag tag = ConfigSerializer.serializeNBT(this, p);
        tag.putInt("version", version);
        tag.putBoolean("globalInput", globalInputEnabled);
        tag.putBoolean("globalOutput", globalOutputEnabled);
        if (!linkedNodes.isEmpty()) {
            CompoundTag nodesTag = new CompoundTag();
            int i = 0;
            for (LogisticsNode node : linkedNodes) {
                nodesTag.put(String.valueOf(i++), LogisticsNode.CODEC.encodeStart(NbtOps.INSTANCE, node).getOrThrow());
            }
            tag.put("linkedNodes", nodesTag);
        }
        return tag;
    }

    public void deserializeNBT(HolderLookup.Provider p, CompoundTag nbt) {
        ConfigSerializer.deserializeNBT(this, p, nbt);
        if (nbt.contains("version")) version = nbt.getInt("version");
        globalInputEnabled = nbt.getBoolean("globalInput");
        globalOutputEnabled = nbt.getBoolean("globalOutput");
        linkedNodes.clear();
        if (nbt.contains("linkedNodes")) {
            CompoundTag nodesTag = nbt.getCompound("linkedNodes");
            for (String key : nodesTag.getAllKeys()) {
                LogisticsNode.CODEC.parse(NbtOps.INSTANCE, nodesTag.get(key)).resultOrPartial(err -> {
                }).ifPresent(linkedNodes::add);
            }
        }
        targetsCacheVersion = -1;
        cachedTargets = null;
    }

    public boolean isDefault() {
        return faceConfig.isDefault() && linkConfig.isDefault() && filterConfig.isDefault() &&
            (sharedContainerConfig == null || sharedContainerConfig.isDefault()) &&
            linkedNodes.isEmpty() && !globalInputEnabled && !globalOutputEnabled;
    }

    public int getSelectedTypesMask() {
        return selectedTypesMask;
    }

    public void setSelectedTypesMask(int mask) {
        this.selectedTypesMask = mask;
        markDirty();
    }

    public boolean isTypeSelected(TransferType type) {
        return (selectedTypesMask & type.getFlag()) != 0;
    }
}