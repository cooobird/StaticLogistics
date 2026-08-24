package com.coobird.staticlogistics.mixin.sable;

import com.coobird.staticlogistics.integration.sable.SableNodeRelocationService;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;

/**
 * 在 Sable 搬移方块的完整事务外层保护并提交物流节点换址。
 */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.api.SubLevelAssemblyHelper", remap = false)
public abstract class SableSubLevelAssemblyHelperMixin {
    @WrapMethod(method = "moveBlocks", require = 0, expect = 1)
    private static void staticlogistics$aroundMoveBlocks(
        ServerLevel originLevel, @Coerce Object rawTransform, Iterable<BlockPos> blocks,
        Operation<Void> original
    ) {
        SableAssemblyTransformAccessor transform = (SableAssemblyTransformAccessor) rawTransform;
        try (SableNodeRelocationService.AssemblyScope scope = SableNodeRelocationService.beginAssemblyMove(
            originLevel,
            transform.staticlogistics$getResultingLevel(),
            blocks,
            transform::staticlogistics$apply,
            transform.staticlogistics$getRotation())) {
            original.call(originLevel, rawTransform, blocks);
            scope.commit();
        }
    }
}
