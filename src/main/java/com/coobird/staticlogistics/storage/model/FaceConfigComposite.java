package com.coobird.staticlogistics.storage.model;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.LogisticsResource;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.logic.group.GroupService;
import com.coobird.staticlogistics.storage.ConfigSerializer;
import com.coobird.staticlogistics.util.LogisticsCalculator;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 面复合配置 —— 组合 FaceConfig + LinkConfig + FilterConfig 三个子配置，
 * 管理链接节点集合、全局输入/输出开关、目标缓存和序列化。
 * 这是整个物流系统最核心的数据模型。
 */
public class FaceConfigComposite {
    public final FaceConfig faceConfig;
    public final LinkConfig linkConfig;
    public final FilterConfig filterConfig;
    public ContainerConfig sharedContainerConfig;

    private final Set<LogisticsNode> linkedNodes = new LinkedHashSet<>();
    private int selectedTypesMask = 0;
    private long version = 0;
    private Consumer<FaceConfigComposite> onDirty = (c) -> {
    };

    private boolean globalInputEnabled = false;
    private boolean globalOutputEnabled = false;

    private final TargetCacheManager targetCacheManager = new TargetCacheManager();

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
        targetCacheManager.clear();
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
        targetCacheManager.clear();
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
     * 从缓存获取目标列表（版本匹配时命中）。
     * 返回内部引用——调用方只读遍历，不做修改。
     */
    @Nullable
    public List<LogisticsNode> getCachedTargets(long currentVersion) {
        return targetCacheManager.getCachedTargets(currentVersion);
    }

    /**
     * 设置目标缓存。直接缓存调用方提供的列表引用（调用方已创建新列表）。
     */
    public void setCachedTargets(List<LogisticsNode> targets, long currentVersion) {
        targetCacheManager.setCachedTargets(targets, currentVersion);
    }

    private void clearCache() {
        targetCacheManager.clear();
    }

    /**
     * 序列化为 NBT（含版本、全局开关、链接节点）
     */
    public CompoundTag serializeNBT(HolderLookup.Provider p) {
        CompoundTag tag = ConfigSerializer.serializeNBT(this, p);
        tag.putLong("version", version);
        tag.putBoolean("globalInput", globalInputEnabled);
        tag.putBoolean("globalOutput", globalOutputEnabled);
        if (!linkedNodes.isEmpty()) {
            CompoundTag nodesTag = new CompoundTag();
            int i = 0;
            for (LogisticsNode node : linkedNodes) {
                nodesTag.put(String.valueOf(i++), LogisticsNode.CODEC.encodeStart(NbtOps.INSTANCE, node).getOrThrow(false, s -> {
                }));
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
        if (nbt.contains("version")) version = nbt.getLong("version");
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
        targetCacheManager.clear();
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

    public boolean isTypeSelected(LogisticsResource<?> type) {
        return (selectedTypesMask & type.getFlag()) != 0;
    }
}