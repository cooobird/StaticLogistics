package com.coobird.staticlogistics.storage.model;

import com.coobird.staticlogistics.api.LogisticsResource;
import com.coobird.staticlogistics.logic.type.TransferRegistries;
import com.coobird.staticlogistics.logic.type.TransferTypeSelection;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * 面配置中的资源类型选择子配置。
 *
 * <p>内部使用类型 ID 列表作为长期格式，旧 int 掩码只作为兼容读写值。
 */
public class TransferTypeSelectionConfig {
    private List<ResourceLocation> selectedTypeIds = List.of();
    private Consumer<TransferTypeSelectionConfig> onDirty = c -> {
    };

    public void setOnDirty(Consumer<TransferTypeSelectionConfig> onDirty) {
        this.onDirty = onDirty == null ? c -> {
        } : onDirty;
    }

    public boolean isDefault() {
        return selectedTypeIds.isEmpty();
    }

    public List<ResourceLocation> getSelectedTypeIds() {
        return selectedTypeIds;
    }

    public void setSelectedTypeIds(Collection<ResourceLocation> ids) {
        List<ResourceLocation> sanitized = TransferTypeSelection.sanitize(ids);
        if (!selectedTypeIds.equals(sanitized)) {
            selectedTypeIds = sanitized;
            onDirty.accept(this);
        }
    }

    public int getLegacyMask() {
        return TransferTypeSelection.toMask(selectedTypeIds, TransferRegistries.getAllActive());
    }

    public void setLegacyMask(int mask) {
        setSelectedTypeIds(TransferTypeSelection.fromMask(mask, TransferRegistries.getAllActive()));
    }

    public boolean isTypeSelected(LogisticsResource<?> type) {
        return TransferTypeSelection.isSelected(selectedTypeIds, type);
    }
}
