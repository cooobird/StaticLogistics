package com.coobird.staticlogistics.client.gui.screen;

import com.coobird.staticlogistics.client.gui.component.AtlasButton;
import com.coobird.staticlogistics.client.gui.component.ExtractionTypePicker;
import com.coobird.staticlogistics.network.c2s.C2SUpdateToolTypesPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.function.BooleanSupplier;

import static com.coobird.staticlogistics.client.gui.ConfiguratorLayout.Hud.*;
import static com.coobird.staticlogistics.client.gui.ConfiguratorLayout.Tabs.*;

public final class QuickExtractionTypeScreen extends Screen {
    private final ExtractionTypePicker picker;
    private final List<ResourceLocation> initialTypes;
    private final BooleanSupplier stillHoldingTool;
    private int left, top, titleWidth;

    public QuickExtractionTypeScreen(List<ResourceLocation> selectedTypes, BooleanSupplier stillHoldingTool) {
        super(Component.translatable("gui.staticlogistics.tabs.types"));
        initialTypes = List.copyOf(selectedTypes);
        picker = new ExtractionTypePicker(initialTypes);
        this.stillHoldingTool = stillHoldingTool;
    }

    @Override
    protected void init() {
        int buttonHeight = 18;
        int columns = width - RIGHT_MARGIN >= RESOURCE_FLYOUT_WIDTH * 2 ? 2 : 1;
        picker.resize(height - BOTTOM_MARGIN - SCREEN_MARGIN, columns);
        left = Math.max(SCREEN_MARGIN, width - RIGHT_MARGIN - picker.width());
        top = Math.max(SCREEN_MARGIN, height - BOTTOM_MARGIN - picker.height());
        picker.setPosition(left, top);
        Component done = Component.translatable("gui.done");
        int buttonWidth = Math.min(font.width(done) + 12, picker.width() / 2 - FLYOUT_ROW_X);
        int buttonLeft = left + picker.width() - FLYOUT_ROW_X - buttonWidth;
        titleWidth = buttonLeft - left - FLYOUT_ROW_X * 2;
        addRenderableWidget(AtlasButton.atlasBuilder(done, button -> applyTypes())
            .bounds(buttonLeft, top + 5, buttonWidth, buttonHeight)
            .build());
    }

    private void applyTypes() {
        List<ResourceLocation> selected = picker.selectedTypes();
        if (stillHoldingTool.getAsBoolean() && !selected.equals(initialTypes)) {
            PacketDistributor.sendToServer(new C2SUpdateToolTypesPayload(selected));
        }
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawString(font, font.plainSubstrByWidth(getTitle().getString(), titleWidth),
            left + FLYOUT_ROW_X, top + FLYOUT_TITLE_Y, 0xFFFFFF, true);
        picker.render(graphics, font, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return picker.toggleAt(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return picker.mouseScrolled(mouseX, mouseY, scrollY)
            || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
