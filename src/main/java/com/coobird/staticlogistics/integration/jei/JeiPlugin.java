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
import org.jetbrains.annotations.Nullable;

import java.util.List;

@mezz.jei.api.JeiPlugin
public class JeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return StaticLogistics.asResource("jei_integration");
    }


    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiScreenHandler(LinkConfiguratorScreen.class, JeiPlugin::properties);
        registration.addGuiScreenHandler(LinkConfiguratorScreen.Window.class, window -> properties(window.host()));
        registration.addGuiContainerHandler(LinkConfiguratorScreen.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(LinkConfiguratorScreen screen) {
                return screen.overlayAreas();
            }
        });
        registration.addGuiContainerHandler(LinkConfiguratorScreen.Window.class, new IGuiContainerHandler<LinkConfiguratorScreen.Window>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(LinkConfiguratorScreen.Window window) {
                return window.host().overlayAreas();
            }
        });
        registration.addGhostIngredientHandler(BaseFilterScreen.class, new GhostIngredientHandler());
    }

    private static @Nullable IGuiProperties properties(LinkConfiguratorScreen screen) {
        if (screen.width <= 0 || screen.height <= 0) return null;
        Rect2i area = screen.overlayAreas().getFirst();
        if (area.getWidth() <= 0 || area.getHeight() <= 0) return null;
        return new PanelProperties(screen.getClass(), area.getX(), area.getY(), area.getWidth(), area.getHeight(), screen.width, screen.height);
    }

    private record PanelProperties(Class<? extends Screen> screenClass, int guiLeft, int guiTop, int guiXSize,
                                   int guiYSize, int screenWidth, int screenHeight) implements IGuiProperties {
    }
}
