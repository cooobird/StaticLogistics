package com.coobird.staticlogistics.mixin.create;

import com.coobird.staticlogistics.integration.create.CreateContraptionService;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.StructureTransform;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.Contraption", remap = false)
public abstract class ContraptionMixin {
    @Inject(method = "addBlocksToWorld", at = @At("TAIL"), remap = false, require = 0, expect = 1)
    private void staticlogistics$relocatePlacedNodes(
        Level level, StructureTransform transform, CallbackInfo callbackInfo
    ) {
        CreateContraptionService.onBlocksPlaced(level, (Contraption) (Object) this, transform);
    }
}
