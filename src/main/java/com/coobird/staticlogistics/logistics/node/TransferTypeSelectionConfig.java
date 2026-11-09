package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.transfer.LogisticsResource;
import com.coobird.staticlogistics.transfer.TransferTypeSelection;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/// 资源类型以 ID 保存，运行时位号不参与持久化。
public class TransferTypeSelectionConfig {
    private List<ResourceLocation> selectedTypeIds = List.of();
    private Set<ResourceLocation> selectedTypeIdSet = Set.of();
    private Consumer<TransferTypeSelectionConfig> onDirty = c -> {
    };

    public void setOnDirty(Consumer<TransferTypeSelectionConfig> onDirty) {
        this.onDirty = onDirty;
    }

    public boolean isDefault() {
        return selectedTypeIds.isEmpty();
    }

    public List<ResourceLocation> getSelectedTypeIds() {
        return selectedTypeIds;
    }

    public void setSelectedTypeIds(Collection<ResourceLocation> ids) {
        List<ResourceLocation> selected = TransferTypeSelection.sanitize(ids);
        if (!selectedTypeIds.equals(selected)) {
            replaceSelectedTypeIds(selected);
            onDirty.accept(this);
        }
    }

    public boolean isTypeSelected(LogisticsResource<?> type) {
        return selectedTypeIdSet.contains(type.typeId());
    }

    void restoreSnapshot(TransferTypeSelectionConfig snapshot) {
        setSelectedTypeIds(snapshot.selectedTypeIds);
    }

    private void replaceSelectedTypeIds(List<ResourceLocation> ids) {
        selectedTypeIds = List.copyOf(ids);
        selectedTypeIdSet = Set.copyOf(selectedTypeIds);
    }
}
