package com.coobird.staticlogistics.logistics;

import com.coobird.staticlogistics.transfer.UpgradeTier;
import com.coobird.staticlogistics.transfer.UpgradeType;
import org.jetbrains.annotations.Nullable;

/**
 * 物流域读取升级物语义所需的最小契约。
 */
public interface LogisticsUpgrade {
    UpgradeType getType();

    @Nullable
    UpgradeTier getTier();

    default boolean isFilterUpgrade() {
        UpgradeType type = getType();
        return type == UpgradeType.BASIC_FILTER
            || type == UpgradeType.TAG_FILTER
            || type == UpgradeType.NBT_FILTER;
    }
}
