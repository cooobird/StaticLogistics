package com.coobird.staticlogistics.mixin.create;

import com.coobird.staticlogistics.integration.create.CreateContraptionService;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.AbstractContraptionEntity", remap = false)
public abstract class AbstractContraptionEntityMixin {
    @Inject(method = "setContraption", at = @At("TAIL"), remap = false, require = 0, expect = 1)
    private void staticlogistics$registerContraption(Contraption contraption, CallbackInfo callbackInfo) {
        CreateContraptionService.onContraptionSet((AbstractContraptionEntity) (Object) this, contraption);
    }

    /**
     * 机械动力的生成数据会直接写入客户端结构，不会调用 setContraption。
     */
    @Inject(method = "readAdditional", at = @At("TAIL"), remap = false, require = 0, expect = 1)
    private void staticlogistics$registerClientContraption(
        CompoundTag tag, boolean spawnData, CallbackInfo callbackInfo
    ) {
        AbstractContraptionEntity entity = (AbstractContraptionEntity) (Object) this;
        CreateContraptionService.onContraptionSet(entity, entity.getContraption());
    }
}
