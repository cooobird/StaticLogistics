package com.coobird.staticlogistics.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * 能力获取器 —— 从方块位置获取传输能力对象。
 * <p>
 * 标准实现：通过 NeoForge 的 {@code BlockCapability} 查找。
 * 自定义实现：直接调用模组 API（如 Botania 的 ManaReceiver.LOOKUP）。
 * <p>
 * 第三方模组集成时，如果目标模组没有暴露 BlockCapability，
 * 可以实现此接口来提供自定义的能力查找逻辑。
 */
@FunctionalInterface
public interface CapGetter<C> {
    /**
     * 获取指定位置和方向的能力对象
     *
     * @param level 服务端世界
     * @param pos   方块位置
     * @param face  方块面
     * @return 能力对象，不可用返回 null
     */
    @Nullable C get(ServerLevel level, BlockPos pos, Direction face);
}
