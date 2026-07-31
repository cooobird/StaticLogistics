package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.ExtractionMode;
import com.coobird.staticlogistics.transfer.TransferTypeSelection;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/**
 * 单次、类型安全的面配置修改。
 *
 * <p>网络层只能构造这些明确的修改类型，业务层不再解释字符串键或通用 NBT。</p>
 */
public sealed interface FaceConfigurationEdit permits FaceConfigurationEdit.BooleanEdit,
    FaceConfigurationEdit.NumberEdit,
    FaceConfigurationEdit.StrategyEdit, FaceConfigurationEdit.ExtractionEdit,
    FaceConfigurationEdit.SelectedTypesEdit {

    enum BooleanField {
        GLOBAL_INPUT,
        GLOBAL_OUTPUT
    }

    enum NumberField {
        PRIORITY,
        KEEP_STOCK
    }

    record BooleanEdit(BooleanField field, boolean enabled) implements FaceConfigurationEdit {
        public BooleanEdit {
            Objects.requireNonNull(field, "Boolean field must not be null");
        }
    }

    record NumberEdit(NumberField field, int value) implements FaceConfigurationEdit {
        public NumberEdit {
            Objects.requireNonNull(field, "Number field must not be null");
            if (field == NumberField.KEEP_STOCK && value < 0) {
                throw new IllegalArgumentException("Keep stock must not be negative");
            }
        }
    }

    record StrategyEdit(DistributionStrategy strategy) implements FaceConfigurationEdit {
        public StrategyEdit {
            Objects.requireNonNull(strategy, "Distribution strategy must not be null");
        }
    }

    record ExtractionEdit(ExtractionMode mode) implements FaceConfigurationEdit {
        public ExtractionEdit {
            Objects.requireNonNull(mode, "Extraction mode must not be null");
        }
    }

    record SelectedTypesEdit(List<ResourceLocation> typeIds) implements FaceConfigurationEdit {
        public SelectedTypesEdit {
            typeIds = TransferTypeSelection.sanitize(typeIds);
        }
    }
}
