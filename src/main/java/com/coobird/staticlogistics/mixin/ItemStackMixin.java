package com.coobird.staticlogistics.mixin;

import com.coobird.staticlogistics.content.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.content.item.ToolMode;
import com.coobird.staticlogistics.content.registry.SLTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 拦截 ItemStack.is(TagKey<Item>)，在非扳手模式下让配置器"伪装"成不是扳手/剪线钳。
 * GregTech 的 ToolHelper.getToolTypes() 和 shouldRenderGrid() 都依赖此方法。
 */
@Mixin(ItemStack.class)
public class ItemStackMixin {

    @Inject(method = "m_204117_", at = @At("HEAD"), cancellable = true, remap = false)
    private void staticlogistics$onIsTag(TagKey<Item> tag, CallbackInfoReturnable<Boolean> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (!(self.getItem() instanceof LinkConfiguratorItem item)) return;
        if (tag == SLTags.Items.TOOLS_WRENCH) {
            if (item.getSettings(self).mode() != ToolMode.WRENCH) {
                cir.setReturnValue(false);
            }
        }
    }
}
