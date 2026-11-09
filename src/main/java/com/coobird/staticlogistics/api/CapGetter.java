package com.coobird.staticlogistics.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/// 按方块位置和朝向查询资源能力。
@FunctionalInterface
public interface CapGetter<C> {
    /// 获取指定位置和方向的能力对象
    /// 参数 level：服务端世界
    /// 参数 pos：方块位置
    /// 参数 face：方块面
    /// 返回：能力对象，不可用返回 null
    @Nullable C get(ServerLevel level, BlockPos pos, Direction face);
}
