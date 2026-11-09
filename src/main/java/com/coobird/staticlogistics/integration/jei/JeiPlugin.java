package com.coobird.staticlogistics.integration.jei;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.client.gui.screen.BaseFilterScreen;
import com.coobird.staticlogistics.client.gui.screen.LinkConfiguratorScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

@mezz.jei.api.JeiPlugin
public class JeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return StaticLogistics.asResource("jei_integration");
    }


    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiScreenHandler(LinkConfiguratorScreen.class, screen -> {
            Rect2i area = screen.overlayAreas().getFirst();
            return new IGuiProperties() {
                public Class<? extends Screen> screenClass() {
                    return screen.getClass();
                }

                public int guiLeft() {
                    return area.getX();
                }

                public int guiTop() {
                    return area.getY();
                }

                public int guiXSize() {
                    return area.getWidth();
                }

                public int guiYSize() {
                    return area.getHeight();
                }

                public int screenWidth() {
                    return screen.width;
                }

                public int screenHeight() {
                    return screen.height;
                }
            };
        });
        registration.addGuiContainerHandler(LinkConfiguratorScreen.class, new IGuiContainerHandler<LinkConfiguratorScreen>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(LinkConfiguratorScreen screen) {
                return screen.overlayAreas();
            }
        });
        registration.addGhostIngredientHandler(BaseFilterScreen.class, new GhostIngredientHandler());
    }
}
