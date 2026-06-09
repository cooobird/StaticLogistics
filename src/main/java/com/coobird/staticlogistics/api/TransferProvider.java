package com.coobird.staticlogistics.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * 传输提供者 —— 封装某种资源的"提取/插入/检查"逻辑。
 * <p>
 * 第三方模组只需实现此接口，即可注册为一种新的传输类型。
 * 不需要关心 capability、CapGetter、TransferProtocol 等内部管线。
 * <p>
 * 使用示例：
 * <pre>{@code
 *   TransferProvider<MyCap, MyStack> provider = new TransferProvider<>() {
 *       public boolean isAvailable(ServerLevel level, BlockPos pos, Direction face) {
 *           return level.getBlockEntity(pos) instanceof MyBlockEntity;
 *       }
 *       public MyCap resolve(ServerLevel level, BlockPos pos, Direction face) {
 *           return ((MyBlockEntity) level.getBlockEntity(pos)).getMyCap();
 *       }
 *       public int getMaxExtract(MyCap cap) { return cap.getStored(); }
 *       public int extract(MyCap cap, int max) { return cap.extract(max); }
 *       public int insert(MyCap cap, int amount) { return cap.insert(amount); }
 *       public boolean isEmpty(MyCap cap) { return cap.getStored() <= 0; }
 *   };
 *   TransferRegistries.registerProvider(
 *       ResourceLocation.fromNamespaceAndPath("mymod", "my_resource"),
 *       0xFF0000, 3, "mymod.my_resource", provider, () -> 100
 *   );
 * }</pre>
 *
 * @param <C> 能力对象类型（如 IItemHandler、IFluidHandler 或自定义类型）
 */
public interface TransferProvider<C> {

    /**
     * 检查指定位置是否支持此传输类型（用于链接验证）
     */
    boolean isAvailable(ServerLevel level, BlockPos pos, Direction face);

    /**
     * 获取指定位置的能力对象
     *
     * @return 能力对象，不可用返回 null
     */
    @Nullable C resolve(ServerLevel level, BlockPos pos, Direction face);

    /**
     * 获取可提取的最大数量
     */
    int getMaxExtract(C cap);

    /**
     * 从能力对象中提取资源
     *
     * @param cap 能力对象
     * @param max 最大提取量
     * @return 实际提取量
     */
    int extract(C cap, int max);

    /**
     * 向能力对象中插入资源
     *
     * @param cap    能力对象
     * @param amount 插入量
     * @return 实际插入量
     */
    int insert(C cap, int amount);

    /**
     * 检查能力对象是否为空（无可提取资源）
     */
    boolean isEmpty(C cap);
}
