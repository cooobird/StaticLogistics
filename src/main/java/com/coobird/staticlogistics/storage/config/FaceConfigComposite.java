package com.coobird.staticlogistics.storage.config;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.api.type.TransferType;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 面容复合配置 —— 组合 FaceConfig + LinkConfig + FilterConfig 三个子配置，
 * 管理链接节点集合、全局输入/输出开关、目标缓存和序列化。
 * 这是整个物流系统最核心的数据模型。
 */
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

    public FaceConfigComposite() {
        this.faceConfig = new FaceConfig();
        this.linkConfig = new LinkConfig();
        this.filterConfig = new FilterConfig();
        setupDirtyCallback();
    }

    private boolean bulkEditing = false;

    private void setupDirtyCallback() {
        this.faceConfig.setOnDirty(c -> markDirty());
        this.linkConfig.setOnDirty(c -> markDirty());
        this.filterConfig.setOnDirty(c -> markDirty());
    }

    public BulkEdit beginBulkEdit() {
        this.bulkEditing = true;
        return new BulkEdit(this);
    }

    private void endBulkEdit() {
        this.bulkEditing = false;
        version++;
        targetsCacheVersion = -1;
        if (onDirty != null) onDirty.accept(this);
    }

    public record BulkEdit(FaceConfigComposite owner) implements AutoCloseable {
        @Override
        public void close() {
            owner.endBulkEdit();
        }
    }

    public void markDirty() {
        if (bulkEditing) return;
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

    public boolean isGlobalInputEnabled() {
        return globalInputEnabled;
    }

    /**
     * 开启全局输入，如果频道是禁用状态则自动设到最小频道
     */
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

    /**
     * 开启全局输出，如果频道是禁用状态则自动设到最小频道
     */
    public void setGlobalOutputEnabled(boolean enabled) {
        if (this.globalOutputEnabled != enabled) {
            this.globalOutputEnabled = enabled;
            if (enabled && linkConfig.getOutputChannel() == LinkConfig.DISABLED_CHANNEL) {
                linkConfig.setOutputChannel(LinkConfig.MIN_CHANNEL);
            }
            markDirty();
        }
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
     * 不做外部截断——上限由源/目标能力自然约束。
     * 溢出防护：stackMult >= INFINITY_MARKER 时返回 Integer.MAX_VALUE。
     */
    public int getTransferLimit(TransferType type) {
        if (sharedContainerConfig == null) {
            return type.getBaseStackSize();
        }
        int stackMult = sharedContainerConfig.getStackMultiplier();
        if (stackMult == ContainerConfig.INFINITY_MARKER) {
            return Integer.MAX_VALUE;
        }
        long limit = (long) type.getBaseStackSize() * stackMult;
        return (int) Math.min(limit, Integer.MAX_VALUE);
    }

    public int getVersion() {
        return version;
    }

    /**
     * 从缓存获取目标列表（版本匹配时命中）。
     * 返回内部引用——调用方只读遍历，不做修改。
     */
    @Nullable
    public List<LogisticsNode> getCachedTargets(int currentVersion) {
        if (cachedTargets != null && targetsCacheVersion == currentVersion) {
            return cachedTargets;
        }
        return null;
    }

    /**
     * 设置目标缓存，同时写入全局缓存
     */
    public void setCachedTargets(List<LogisticsNode> targets, int currentVersion) {
        if (targets != null && !targets.isEmpty()) {
            int cacheSize = Math.min(targets.size(), LogisticsConstants.Cache.getTargetCacheSize());
            this.cachedTargets = new ArrayList<>(targets.subList(0, cacheSize));
            this.targetsCacheVersion = currentVersion;

        } else {
            clearCache();
        }
    }

    private void clearCache() {
        this.cachedTargets = null;
        this.targetsCacheVersion = -1;
    }

    /**
     * 序列化为 NBT（含版本、全局开关、链接节点）
     */
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

    /**
     * 从 NBT 反序列化
     */
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
        if (this.selectedTypesMask != mask) {
            this.selectedTypesMask = mask;
            markDirty();
        }
    }

    public boolean isTypeSelected(TransferType type) {
        return (selectedTypesMask & type.getFlag()) != 0;
    }
}