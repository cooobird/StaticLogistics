package com.coobird.staticlogistics.gui.screen;

import com.coobird.staticlogistics.client.data.SelectionContext;
import com.coobird.staticlogistics.gui.screen.component.*;
import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
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

/**
 * 链接配置器界面。
 * {@link LeftSidebar} — 左侧模式标签栏
 * {@link TransferTypeGrid} — 中间传输类型按钮网格
 * {@link GroupPanel} — 右侧分组面板（搜索+列表+滚动条+行内重命名）
 * {@link NewGroupWidget} — 右侧最下方新建分组（独立编辑框+加号按钮）
 */
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {

        graphics.blit(SLGuiTextures.GUI_ATLAS, leftPos, topPos,
            SLGuiTextures.Background.U, SLGuiTextures.Background.V,
            SLGuiTextures.Background.WIDTH, SLGuiTextures.Background.HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

        super.render(graphics, mouseX, mouseY, partialTick);

        // ---- 标题 ----
        TitleBar.render(graphics, this.font, leftPos, topPos,
            SLGuiTextures.Background.WIDTH,
            this.title.getString());

        // ---- 左侧边栏 ----
        LeftSidebar.render(graphics, this.font, leftPos, topPos, modeIdx);

        // ---- 右侧组面板 ----
        groupPanel.render(graphics, this.font, stack, leftPos, topPos,
            mouseX, mouseY, partialTick);

        // ---- 右侧新建分组 ----
        newGroupWidget.render(graphics, this.font, leftPos, topPos,
            mouseX, mouseY, partialTick);

        // ---- 中间类型按钮 ----
        TransferTypeGrid.render(graphics, stack, leftPos, topPos, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);

        var hoveredType = TransferTypeGrid.getHoveredType(mouseX, mouseY, stack,
            leftPos, topPos);
        if (hoveredType != null) {
            TransferTypeGrid.renderTooltip(graphics, this.font, hoveredType,
                mouseX, mouseY);
        }

        String hoveredGroup = groupPanel.getHoveredGroupId();
        if (!hoveredGroup.isEmpty()) {
            groupPanel.renderGroupTooltip(graphics, this.font, mouseX, mouseY,
                hoveredGroup);
        }

        LeftSidebar.renderTooltip(graphics, this.font, mouseX, mouseY,
            leftPos, topPos);

        graphics.pose().popPose();
    }

    private void handleNewGroupSubmit() {
        String name = newGroupWidget.confirmInput();
        if (name.isEmpty()) return;
        syncSettings(name, true);
        playClickSound();
        this.setFocused(null);
    }

    private void handleConfirmRename() {
        String editingId = groupPanel.getEditingGroupId();
        String newId = groupPanel.confirmRename();
        if (newId == null) return;

        if (!Objects.equals(editingId, newId)) {
            PacketDistributor.sendToServer(
                new C2SGroupRenamePayload(editingId, newId));
            if (stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "")
                .equals(editingId)) {
                syncSettings(newId, false);
            }
        }
    }

    private void syncSettings(String groupId, boolean playSound) {
        SelectionContext.setSelection(groupId, modeIdx);
        stack.set(SLDataComponents.SELECTED_GROUP.get(), groupId);
        stack.set(SLDataComponents.TOOL_MODE.get(), modeIdx);
        int typeMask = stack.getOrDefault(SLDataComponents.SELECTED_TYPES_MASK.get(), 0);
        PacketDistributor.sendToServer(
            new C2SUpdateToolSettingsPayload(groupId, modeIdx, typeMask));
        // 注册到 ClientLinkData，确保新建无链接分组也能在列表中显示
        if (!groupId.isEmpty()) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                com.coobird.staticlogistics.client.data.ClientLinkData.INSTANCE
                    .addKnownGroup(player.getUUID(),
                        player.getName().getString(), groupId);
            }
        }
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
