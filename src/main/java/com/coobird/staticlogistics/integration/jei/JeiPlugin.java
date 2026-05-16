package com.coobird.staticlogistics.integration.jei;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.gui.screen.BaseFilterScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.resources.ResourceLocation;

@mezz.jei.api.JeiPlugin
public class JeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return Staticlogistics.asResource("jei_integration");
    }


    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(BaseFilterScreen.class, new GhostIngredientHandler());
    }
}