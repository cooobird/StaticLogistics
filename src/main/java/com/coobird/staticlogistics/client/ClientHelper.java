package com.coobird.staticlogistics.client;

import com.coobird.staticlogistics.gui.screen.BlueprintGroupScreen;
import com.coobird.staticlogistics.gui.screen.LinkConfiguratorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientHelper {

    public static void openBlueprintGroupScreen(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.setScreen(new BlueprintGroupScreen(stack));
    }

    public static void openLinkConfiguratorScreen(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.setScreen(new LinkConfiguratorScreen(stack));
    }
}
