package com.coobird.staticlogistics.mixin.sable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Sable 装配变换的可选桥接接口；未安装 Sable 时不会应用。
 */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.api.SubLevelAssemblyHelper$AssemblyTransform", remap = false)
public interface SableAssemblyTransformAccessor {
    @Invoker("apply")
    BlockPos staticlogistics$apply(BlockPos position);

    @Accessor("resultingLevel")
    ServerLevel staticlogistics$getResultingLevel();

    @Accessor("rotation")
    Rotation staticlogistics$getRotation();
}
