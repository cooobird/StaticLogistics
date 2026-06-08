package com.coobird.staticlogistics.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * 传输提供者，一种较老的简化集成方式。
 *
 * <p>适合有明确 extract/insert 语义并通过 NeoForge BlockCapability 暴露的资源。
 * 新项目推荐使用 {@link LogisticsResource}，支持更完整的集成等级。
 *
 * @see LogisticsResource 推荐的替代方式
 */
public interface TransferProvider<C> {

    /**
     * 检查指定位置是否支持此传输类型。
     */
    boolean isAvailable(ServerLevel level, BlockPos pos, Direction face);

    /**
     * 获取指定位置对面的能力对象。
     */
    @Nullable C resolve(ServerLevel level, BlockPos pos, Direction face);

    /**
     * 当前可提取的最大数量。
     */
    int getMaxExtract(C cap);

    /**
     * 从能力对象中提取资源。
     */
    int extract(C cap, int max);

    /**
     * 向能力对象中注入资源。
     */
    int insert(C cap, int amount);

    /**
     * 检查能力对象是否为空。
     */
    boolean isEmpty(C cap);
}
