package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.ExtractionMode;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import com.coobird.staticlogistics.transfer.TransferTypeSelection;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.List;

/// 单次、类型安全的面配置修改。
/// 网络层只能构造这些明确的修改类型，业务层不再解释字符串键或通用 NBT。
public sealed interface ChannelConfigurationEdit permits ChannelConfigurationEdit.BooleanEdit,
    ChannelConfigurationEdit.NumberEdit,
    ChannelConfigurationEdit.StrategyEdit, ChannelConfigurationEdit.ExtractionEdit,
    ChannelConfigurationEdit.SelectedTypesEdit, ChannelConfigurationEdit.TypePatchEdit, ChannelConfigurationEdit.RedstoneEdit, ChannelConfigurationEdit.EnabledEdit, ChannelConfigurationEdit.RoleEdit, ChannelConfigurationEdit.FilterEdit {

    record FilterEdit(FilterData filter) implements ChannelConfigurationEdit {
    }

    record RoleEdit(NodeRole role) implements ChannelConfigurationEdit {
    }

    record EnabledEdit(boolean enabled) implements ChannelConfigurationEdit {
    }

    record RedstoneEdit(ChannelRedstoneMode mode) implements ChannelConfigurationEdit {
    }

    enum BooleanField {
        GLOBAL_INPUT,
        GLOBAL_OUTPUT
    }

    enum NumberField {
        PRIORITY,
        KEEP_STOCK
    }

    record BooleanEdit(BooleanField field, boolean enabled) implements ChannelConfigurationEdit {
    }

    record NumberEdit(NumberField field, long value) implements ChannelConfigurationEdit {
        public NumberEdit {
            if (field == NumberField.KEEP_STOCK && value < 0) {
                throw new IllegalArgumentException("Keep stock must not be negative");
            }
        }
    }

    record StrategyEdit(DistributionStrategy strategy) implements ChannelConfigurationEdit {
    }

    record ExtractionEdit(ExtractionMode mode) implements ChannelConfigurationEdit {
    }

    record SelectedTypesEdit(List<ResourceLocation> typeIds) implements ChannelConfigurationEdit {
        public SelectedTypesEdit {
            typeIds = TransferTypeSelection.sanitize(typeIds);
        }
    }

    /// 仅修改明确勾选或取消的类型，保留每个节点其余配置。
    record TypePatchEdit(List<ResourceLocation> added,
                         List<ResourceLocation> removed) implements ChannelConfigurationEdit {
        public TypePatchEdit {
            added = TransferTypeSelection.sanitize(added);
            removed = TransferTypeSelection.sanitize(removed);
            if (added.stream().anyMatch(removed::contains))
                throw new IllegalArgumentException("Overlapping type changes");
        }

        public List<ResourceLocation> apply(List<ResourceLocation> current) {
            var result = new LinkedHashSet<>(current);
            removed.forEach(result::remove);
            result.addAll(added);
            return List.copyOf(result);
        }
    }
}
