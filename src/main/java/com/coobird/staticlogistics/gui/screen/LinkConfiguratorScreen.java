package com.coobird.staticlogistics.gui.screen;

import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.client.data.SelectionContext;
import com.coobird.staticlogistics.gui.screen.component.*;
import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
import com.coobird.staticlogistics.network.c2s.C2SDeleteGroupPayload;
import com.coobird.staticlogistics.network.c2s.C2SGroupRenamePayload;
import com.coobird.staticlogistics.network.c2s.C2SUpdateToolSettingsPayload;
import com.coobird.staticlogistics.registry.SLDataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Objects;

public class LinkConfiguratorScreen extends Screen {

    private final ItemStack stack;
    private int leftPos, topPos, modeIdx;
    private GroupPanel groupPanel;
    private NewGroupWidget newGroupWidget;

    public LinkConfiguratorScreen(ItemStack stack) {
        super(Component.translatable("gui.staticlogistics.linker_settings"));
        this.stack = stack;
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - SLGuiTextures.Background.WIDTH) / 2;
        this.topPos = (this.height - SLGuiTextures.Background.HEIGHT) / 2;
        SelectionContext.syncFromItem(this.stack);
        this.modeIdx = SelectionContext.getSelectedMode();
        String initialGroup = SelectionContext.getSelectedGroupId();
        this.stack.set(SLDataComponents.SELECTED_GROUP.get(), initialGroup);
        this.groupPanel = new GroupPanel(this.font, leftPos, topPos);
        this.groupPanel.setInitialState(this.stack);
        this.newGroupWidget = new NewGroupWidget(this.font);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.blit(SLGuiTextures.GUI_ATLAS, leftPos, topPos,
            SLGuiTextures.Background.U, SLGuiTextures.Background.V,
            SLGuiTextures.Background.WIDTH, SLGuiTextures.Background.HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

        TitleBar.render(g, this.font, leftPos, topPos,
            SLGuiTextures.Background.WIDTH, this.title.getString());
        LeftSidebar.render(g, this.font, leftPos, topPos, modeIdx);
        groupPanel.render(g, this.font, stack, leftPos, topPos, mx, my, pt);
        newGroupWidget.render(g, this.font, leftPos, topPos, mx, my, pt);
        TransferTypeGrid.render(g, stack, leftPos, topPos, mx, my);

        super.render(g, mx, my, pt);

        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        var hoveredType = TransferTypeGrid.getHoveredType(mx, my, stack, leftPos, topPos);
        if (hoveredType != null) TransferTypeGrid.renderTooltip(g, this.font, hoveredType, mx, my);
        String hoveredGroup = groupPanel.getHoveredGroupId();
        if (!hoveredGroup.isEmpty()) groupPanel.renderGroupTooltip(g, this.font, mx, my, hoveredGroup, hasShiftDown());
        LeftSidebar.renderTooltip(g, this.font, mx, my, leftPos, topPos);
        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (groupPanel.searchBoxMouseClicked(mx, my, button)) {
            this.setFocused(groupPanel.getSearchBox());
            return true;
        }
        if (groupPanel.renameBoxMouseClicked(mx, my, button)) {
            this.setFocused(groupPanel.getRenameBox());
            return true;
        }

        if (newGroupWidget.isAddButtonHit(mx, my, leftPos, topPos)) {
            handleNewGroupSubmit();
            return true;
        }

        if (newGroupWidget.isTextBoxHit(mx, my, leftPos, topPos)) {
            newGroupWidget.beginEdit();
            this.setFocused(newGroupWidget.getEditBox());
            return true;
        }

        var clickedType = TransferTypeGrid.handleClick(mx, my, stack, leftPos, topPos);
        if (clickedType != null) {
            syncSettings(stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), ""), true);
            return true;
        }

        if (!groupPanel.getEditingGroupId().isEmpty() && !groupPanel.getRenameBox().isMouseOver(mx, my))
            handleConfirmRename();
        if (newGroupWidget.isEditing() && !newGroupWidget.isEditBoxMouseOver(mx, my))
            newGroupWidget.cancelEdit();
        this.setFocused(null);

        int clickedMode = LeftSidebar.getClickedMode(mx, my, leftPos, topPos, modeIdx);
        if (clickedMode >= 0) {
            this.modeIdx = clickedMode;
            syncSettings(stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), ""), true);
            return true;
        }

        if (groupPanel.isSearchTriggerHit(mx, my, leftPos, topPos)) {
            groupPanel.triggerSearch();
            return true;
        }

        if (groupPanel.handleScrollbarClick(mx, my, leftPos, topPos)) return true;

        GroupPanel.ClickResult listResult = groupPanel.handleListClick(mx, my, button, leftPos, topPos, stack, hasShiftDown());
        if (listResult != null) {
            switch (listResult.getAction()) {
                case SELECT -> syncSettings(listResult.getGroupId(), true);
                case RENAME -> {
                    groupPanel.startRename(listResult.getGroupId(), leftPos, topPos);
                    this.setFocused(groupPanel.getRenameBox());
                }
                case EXPORT -> {
                    groupPanel.exportToChat(listResult.getGroupId());
                    this.onClose();
                }
                case DELETE -> {
                    PacketDistributor.sendToServer(new C2SDeleteGroupPayload(listResult.getGroupId()));
                    var player = Minecraft.getInstance().player;
                    if (player != null)
                        com.coobird.staticlogistics.client.data.ClientLinkData.INSTANCE.removeKnownGroup(player.getUUID(), listResult.getGroupId());
                    syncSettings("", true);
                }
            }
            return true;
        }

        boolean inMain = mx >= leftPos && mx <= leftPos + SLGuiTextures.Background.WIDTH && my >= topPos && my <= topPos + SLGuiTextures.Background.HEIGHT;
        boolean inSide = mx >= leftPos + GroupPanel.SIDE_PANEL_X && mx <= leftPos + GroupPanel.SIDE_PANEL_X + SLGuiTextures.Background.BY_GROUP_WIDTH && my >= topPos && my <= topPos + SLGuiTextures.Background.BY_GROUP_HEIGHT;
        if ((inMain || inSide) && !stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "").isEmpty()) {
            syncSettings("", false);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (groupPanel.mouseScrolled(mx, my, dy, leftPos, topPos)) return true;
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (groupPanel.mouseDragged(my, topPos)) return true;
        return super.mouseDragged(mx, my, b, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int b) {
        groupPanel.mouseReleased();
        return super.mouseReleased(mx, my, b);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (groupPanel.getSearchBox().canConsumeInput() || groupPanel.getRenameBox().canConsumeInput()) {
            if (keyCode == 257 || keyCode == 335) {
                if (groupPanel.isSearchBoxFocused()) groupPanel.triggerSearch();
                else if (groupPanel.isRenameBoxVisible()) handleConfirmRename();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (newGroupWidget.isEditing() && newGroupWidget.getEditBox().canConsumeInput()) {
            if (keyCode == 257 || keyCode == 335) {
                handleNewGroupSubmit();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (Minecraft.getInstance().options.keyInventory.matches(keyCode, scanCode) || keyCode == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void handleNewGroupSubmit() {
        playClickSound();
        String name = newGroupWidget.confirmInput();
        if (name.isEmpty()) return;
        syncSettings(name, true);
        var player = Minecraft.getInstance().player;
        if (player != null) {
            ClientLinkData.INSTANCE.addKnownGroup(player.getUUID(), player.getGameProfile().getName(), name);
        }
        this.setFocused(null);
    }

    private void handleConfirmRename() {
        String editingId = groupPanel.getEditingGroupId();
        String newId = groupPanel.confirmRename();
        if (newId == null) return;
        if (!Objects.equals(editingId, newId)) {
            PacketDistributor.sendToServer(new C2SGroupRenamePayload(editingId, newId));
            var player = Minecraft.getInstance().player;
            if (player != null)
                ClientLinkData.INSTANCE.removeKnownGroup(player.getUUID(), editingId);
            if (stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "").equals(editingId))
                syncSettings(newId, false);
        }
    }

    private void syncSettings(String groupId, boolean playSound) {
        SelectionContext.setSelection(groupId, modeIdx);
        stack.set(SLDataComponents.SELECTED_GROUP.get(), groupId);
        stack.set(SLDataComponents.TOOL_MODE.get(), modeIdx);
        int typeMask = stack.getOrDefault(SLDataComponents.SELECTED_TYPES_MASK.get(), 0);
        PacketDistributor.sendToServer(new C2SUpdateToolSettingsPayload(groupId, modeIdx, typeMask));

        if (playSound) playClickSound();
    }

    private void playClickSound() {
        com.coobird.staticlogistics.util.SoundUtil.playClickSound();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
