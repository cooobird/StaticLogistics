package com.coobird.staticlogistics.mixin;

import com.coobird.staticlogistics.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.item.util.ToolMode;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.Tags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 拦截 ItemStack.is(TagKey)，在非扳手模式下让配置器"伪装"成不是扳手。
 */
@Mixin(ItemStack.class)
public class ItemStackMixin {

    @Inject(method = "is(Lnet/minecraft/tags/TagKey;)Z", at = @At("HEAD"), cancellable = true)
    private void staticlogistics$onIsTag(TagKey<Item> tag, CallbackInfoReturnable<Boolean> cir) {
        if (tag != Tags.Items.TOOLS_WRENCH) return;
        ItemStack self = (ItemStack) (Object) this;
        if (self.getItem() instanceof LinkConfiguratorItem item) {
            if (item.getSettings(self).mode() != ToolMode.WRENCH) {
                cir.setReturnValue(false);
            }
        }
    }
}
