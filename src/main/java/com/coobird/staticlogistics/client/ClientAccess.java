package com.coobird.staticlogistics.client;

import com.coobird.staticlogistics.client.gui.LinkConfiguratorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class ClientAccess {

    @OnlyIn(Dist.CLIENT)
    public static void openLinkerScreen(ItemStack stack) {
        Minecraft.getInstance().setScreen(new LinkConfiguratorScreen(stack));
    }
}