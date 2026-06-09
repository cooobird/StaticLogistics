package com.coobird.staticlogistics.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * 能力获取器 —— 从方块位置获取传输能力对象。
 */
@FunctionalInterface
public interface CapGetter<C> {
    @Nullable C get(ServerLevel level, BlockPos pos, Direction face);
}
