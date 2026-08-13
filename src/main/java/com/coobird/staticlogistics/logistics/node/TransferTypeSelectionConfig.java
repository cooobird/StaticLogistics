package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.transfer.LogisticsResource;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import com.coobird.staticlogistics.transfer.TransferTypeSelection;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 面配置中的资源类型选择子配置。
 *
 * <p>内部使用类型 ID 列表作为长期格式，旧 int 掩码只作为兼容读写值。
 */
public class TransferTypeSelectionConfig {
    private List<ResourceLocation> selectedTypeIds = List.of();
    private Set<ResourceLocation> selectedTypeIdSet = Set.of();
    /**
     * 当前未注册适配器对应的旧位；必须原样跨加载与保存保留。
     */
    private int unresolvedLegacyMask;
    private Consumer<TransferTypeSelectionConfig> onDirty = c -> {
    };

    public void setOnDirty(Consumer<TransferTypeSelectionConfig> onDirty) {
        this.onDirty = onDirty == null ? c -> {
        } : onDirty;
    }

    public boolean isDefault() {
        return selectedTypeIds.isEmpty() && unresolvedLegacyMask == 0;
    }

    public List<ResourceLocation> getSelectedTypeIds() {
        return selectedTypeIds;
    }

    public void setSelectedTypeIds(Collection<ResourceLocation> ids) {
        List<ResourceLocation> sanitized = TransferTypeSelection.sanitize(ids);
        if (unresolvedLegacyMask != 0) {
            java.util.LinkedHashSet<ResourceLocation> restored = new java.util.LinkedHashSet<>(sanitized);
            restored.addAll(TransferTypeSelection.fromMask(
                unresolvedLegacyMask, TransferRegistries.getAllActive()));
            sanitized = TransferTypeSelection.sanitize(restored);
        }
        int retainedLegacyMask = unresolvedLegacyMask & ~activeTypeMask();
        if (!selectedTypeIds.equals(sanitized) || unresolvedLegacyMask != retainedLegacyMask) {
            replaceSelectedTypeIds(sanitized);
            unresolvedLegacyMask = retainedLegacyMask;
            onDirty.accept(this);
        }
    }

    public int getLegacyMask() {
        return TransferTypeSelection.toMask(selectedTypeIds, TransferRegistries.getAllActive())
            | unresolvedLegacyMask;
    }

    public void setLegacyMask(int mask) {
        List<ResourceLocation> resolved = TransferTypeSelection.fromMask(
            mask, TransferRegistries.getAllActive());
        int unresolved = mask & ~activeTypeMask();
        if (!selectedTypeIds.equals(resolved) || unresolvedLegacyMask != unresolved) {
            replaceSelectedTypeIds(resolved);
            unresolvedLegacyMask = unresolved;
            onDirty.accept(this);
        }
    }

    /**
     * ID 列表已成为权威时，仅补回其中无法表达的未注册旧位。
     */
    public void loadUnresolvedLegacyMask(int mask) {
        int unresolved = mask & ~activeTypeMask();
        java.util.LinkedHashSet<ResourceLocation> restored = new java.util.LinkedHashSet<>(selectedTypeIds);
        restored.addAll(TransferTypeSelection.fromMask(mask, TransferRegistries.getAllActive()));
        List<ResourceLocation> resolvedIds = TransferTypeSelection.sanitize(restored);
        if (!selectedTypeIds.equals(resolvedIds) || unresolvedLegacyMask != unresolved) {
            replaceSelectedTypeIds(resolvedIds);
            unresolvedLegacyMask = unresolved;
            onDirty.accept(this);
        }
    }

    public boolean isTypeSelected(LogisticsResource<?> type) {
        return selectedTypeIdSet.contains(type.typeId());
    }

    /**
     * 以快照中的稳定类型 ID 列表替换当前选择。
     */
    void restoreSnapshot(TransferTypeSelectionConfig snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("Transfer type snapshot must not be null");
        if (!selectedTypeIds.equals(snapshot.selectedTypeIds)
            || unresolvedLegacyMask != snapshot.unresolvedLegacyMask) {
            replaceSelectedTypeIds(snapshot.selectedTypeIds);
            unresolvedLegacyMask = snapshot.unresolvedLegacyMask;
            onDirty.accept(this);
        }
    }

    /**
     * 同时替换持久化顺序列表与运行时查询索引，避免 Tick 热路径线性扫描类型 ID。
     */
    private void replaceSelectedTypeIds(List<ResourceLocation> ids) {
        selectedTypeIds = List.copyOf(ids);
        selectedTypeIdSet = Set.copyOf(selectedTypeIds);
    }

    private static int activeTypeMask() {
        int mask = 0;
        for (LogisticsResource<?> type : TransferRegistries.getAllActive()) {
            int bit = type.bitOffset();
            if (bit >= 0 && bit < Integer.SIZE) mask |= 1 << bit;
        }
        return mask;
    }
}
