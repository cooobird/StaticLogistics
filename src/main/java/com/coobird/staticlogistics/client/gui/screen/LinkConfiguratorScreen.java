package com.coobird.staticlogistics.client.gui.screen;

import PortLib.extensions.net.minecraft.world.item.ItemStack.PortItemStackExtension;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.client.data.ClientConnection;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.client.data.SelectionContext;
import com.coobird.staticlogistics.client.gui.component.*;
import com.coobird.staticlogistics.client.key.SLKeyMappings;
import com.coobird.staticlogistics.client.render.SLGuiTextures;
import com.coobird.staticlogistics.content.item.ToolMode;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.node.FaceConfigurationEdit;
import com.coobird.staticlogistics.logistics.node.FaceTopology;
import com.coobird.staticlogistics.logistics.util.NodeDisplayText;
import com.coobird.staticlogistics.network.SLNetwork;
import com.coobird.staticlogistics.network.c2s.*;
import com.coobird.staticlogistics.transfer.LogisticsResource;
import com.coobird.staticlogistics.transfer.TransferTypeSelection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;

/**
 * 连接配置器的唯一主界面。
 *
 * <p>本类只负责组织整张界面、分发输入事件和协调区域状态，不直接实现各区域
 * 内部的复杂绘制。调整界面时按以下职责定位：
 * <ul>
 *     <li>{@link ToolModeBar}：顶部左侧的四个工具模式按钮；</li>
 *     <li>{@link TransferTypeGrid}：顶部中间随所选节点切换上下文的资源类型视窗；</li>
 *     <li>{@link NetworkPreviewPanel}：左上网络拓扑、节点和连线 Tooltip；</li>
 *     <li>{@link GroupPanel}：右上分组、链接列表、搜索、重命名和滚动条；</li>
 *     <li>{@link GroupPanel}：分组树末尾的内联新增分组输入；</li>
 *     <li>{@link NodeConfigurationPanel}：左下所选连接面的输入、输出、过滤器与升级配置；</li>
 *     <li>{@link LinkConfiguratorMenu}：真实槽位、服务端数据同步和编辑权限。</li>
 * </ul>
 *
 * <p>背景区域尺寸和 atlas UV 位于 {@link SLGuiTextures.LinkConfigurator}。
 * 输入/输出页签只修改当前 Menu 的可见侧状态，不得重新打开 Screen 或 Menu，
 * 否则会重置鼠标、焦点和拖动状态。
 */
public class LinkConfiguratorScreen extends AbstractConfiguratorScreen<LinkConfiguratorMenu> {
    private static final int TITLE_RIGHT_PADDING = 8;
    private static final int TITLE_Y = 6;
    private static final int HIDDEN_VANILLA_INVENTORY_LABEL_Y = 1000;
    private static final int PREVIEW_TITLE_X = 22;
    private static final int GROUP_TITLE_X = 316;
    private static final int REGION_TITLE_Y = 28;
    private static final int INVENTORY_TITLE_Y_OFFSET = 2;
    private static final int EXPANDED_PREVIEW_FRAME_X = 8;
    private static final int EXPANDED_PREVIEW_FRAME_Y = 38;
    private static final int EXPANDED_PREVIEW_FRAME_WIDTH = 462;
    private static final int EXPANDED_PREVIEW_FRAME_HEIGHT = 224;
    private static final int PREVIEW_TOGGLE_X = 267;
    private static final int PREVIEW_TOGGLE_Y = 26;

    // 当前连接摘要在右侧信息框中的内边距和行偏移。
    private static final int CONNECTION_TEXT_INSET = 5;
    private static final int CONNECTION_FIRST_LINE_Y = 13;
    private static final int CONNECTION_SECOND_LINE_Y = 25;
    private static final int CONNECTION_SINGLE_LINE_Y = 14;
    private static final int CONNECTION_EMPTY_HINT_Y = 18;

    private static final int COLOR_ACCENT = 0xFF98FB98;
    private static final int COLOR_SECONDARY = 0xFF55D7FF;
    private static final int COLOR_DISABLED = 0xFF777777;

    // 区域组件分别保存自己的滚动、选择或编辑状态。
    private final NetworkPreviewPanel preview = new NetworkPreviewPanel();

    private int modeIdx;
    private GroupPanel groupPanel;
    private NodeConfigurationPanel nodeConfigurationPanel;
    private ConfirmationDialog confirmationDialog;
    private boolean previewExpanded;

    public LinkConfiguratorScreen(LinkConfiguratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        this.imageWidth = SLGuiTextures.LinkConfigurator.CONTENT_WIDTH;
        this.imageHeight = SLGuiTextures.LinkConfigurator.CONTENT_HEIGHT;
        super.init();
        this.titleLabelX = this.imageWidth - this.font.width(this.title) - TITLE_RIGHT_PADDING;
        this.titleLabelY = TITLE_Y;
        // 原版物品栏标签由 atlas 区域标题替代，移出可视范围避免重复绘制。
        this.inventoryLabelY = HIDDEN_VANILLA_INVENTORY_LABEL_Y;

        // 顶部工具状态来自配置器物品。
        ItemStack stack = toolStack();
        SelectionContext.syncFromItem(stack);
        modeIdx = ToolMode.fromId(PortItemStackExtension.getDataOrDefault(
            stack, SLDataComponents.TOOL_MODE.get(), 0)).getId();
        String initialGroup = SelectionContext.getSelectedGroupId();
        PortItemStackExtension.setData(stack, SLDataComponents.SELECTED_GROUP.get(), initialGroup);

        // 右上分组区和左上网络预览共享同一个选中分组。
        groupPanel = new GroupPanel(font, leftPos, topPos);
        groupPanel.setInitialState(stack);
        preview.setGroup(PortItemStackExtension.getData(stack, SLDataComponents.SELECTED_GROUP_KEY.get()));
        if (menu.hasTarget()) {
            preview.selectNode(menu.getTargetNode());
        }

        // 左下节点区复用当前 Menu；页签切换不会创建新容器界面。
        nodeConfigurationPanel = new NodeConfigurationPanel(
            menu, font, this::openFilter, this::selectSide,
            SoundUtil::playClickSound, preview::getSelectedNodes);
        nodeConfigurationPanel.init(leftPos, topPos, this::addRenderableWidget);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        ItemStack stack = toolStack();
        int contentInset = SLGuiTextures.LinkConfigurator.CONTENT_INSET;
        graphics.blit(SLGuiTextures.GUI_ATLAS,
            leftPos - contentInset, topPos - contentInset,
            SLGuiTextures.LinkConfigurator.U, SLGuiTextures.LinkConfigurator.V,
            SLGuiTextures.LinkConfigurator.WIDTH, SLGuiTextures.LinkConfigurator.HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        renderTitle(graphics);
        renderToolbarLabels(graphics);
        ToolModeBar.render(graphics, font, leftPos, topPos, modeIdx);
        TransferTypeGrid.render(graphics, transferTypeView(), leftPos, topPos, mouseX, mouseY);
        renderPreviewToggle(graphics, mouseX, mouseY);
        if (!previewExpanded) {
            preview.render(graphics, font, previewLeft(), previewTop(),
                previewWidth(), previewHeight(), mouseX, mouseY);
            groupPanel.render(graphics, font, stack, preview.getSelectedConnection(),
                leftPos, topPos, mouseX, mouseY, partialTick);
            renderCurrentConnection(graphics);
            nodeConfigurationPanel.render(graphics, mouseX, mouseY);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (previewExpanded) renderExpandedPreview(graphics, mouseX, mouseY);
        if (confirmationDialog != null && confirmationDialog.isOpen()) {
            confirmationDialog.render(graphics, font, width, height, mouseX, mouseY);
            return;
        }

        /*
         * 玩家背包和快捷栏必须完整走原版容器 Tooltip 流程，才能保留物品名称、
         * 附魔、数据组件以及其他模组追加的 Tooltip。配置槽由节点区域在原版
         * 物品 Tooltip 后追加升级统计，因此不能在这里重复绘制。
         */
        if (previewExpanded) {
            preview.renderTooltip(graphics, font, mouseX, mouseY,
                previewLeft(), previewTop(), previewWidth(), previewHeight());
            renderPreviewToggleTooltip(graphics, mouseX, mouseY);
            return;
        }

        Slot hoveredSlot = getSlotUnderMouse();
        if (hoveredSlot != null
            && !hoveredSlot.getItem().isEmpty()
            && !menu.isConfigurationSlot(hoveredSlot)) {
            renderTooltip(graphics, mouseX, mouseY);
            return;
        }

        // Tooltip 必须最后绘制，否则会被节点区、分组区或真实槽位覆盖。
        preview.renderTooltip(
            graphics,
            font,
            mouseX,
            mouseY,
            previewLeft(),
            previewTop(),
            previewWidth(), previewHeight());
        nodeConfigurationPanel.renderTooltips(graphics, mouseX, mouseY);
        renderTopTooltips(graphics, mouseX, mouseY);
        renderPreviewToggleTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        nodeConfigurationPanel.tick();
        ItemStack stack = toolStack();
        if (!Objects.equals(
            PortItemStackExtension.getData(stack, SLDataComponents.SELECTED_CONNECTION_KEY.get()),
            SelectionContext.getFocusedConnectionKey())) {
            syncFocusedConnection(false);
        }
    }

    @Override
    public void removed() {
        preview.flushLayout();
        super.removed();
    }

    private void renderTitle(GuiGraphics graphics) {
        TitleBar.render(graphics, font, leftPos, topPos,
            SLGuiTextures.LinkConfigurator.CONTENT_WIDTH, title.getString());
    }

    private void renderToolbarLabels(GuiGraphics graphics) {
        graphics.drawString(font, Component.translatable("gui.staticlogistics.network_preview.title"), leftPos + PREVIEW_TITLE_X, topPos + REGION_TITLE_Y, COLOR_ACCENT, false);
        if (previewExpanded) return;
        graphics.drawString(font, Component.translatable("gui.staticlogistics.groups_and_connections"), leftPos + GROUP_TITLE_X, topPos + REGION_TITLE_Y, COLOR_ACCENT, false);
        graphics.drawString(font, playerInventoryTitle,
            leftPos + SLGuiTextures.LinkConfigurator.INVENTORY_X,
            topPos + SLGuiTextures.LinkConfigurator.INVENTORY_Y
                + INVENTORY_TITLE_Y_OFFSET,
            0xFFFFFFFF, false);
    }

    private void renderCurrentConnection(GuiGraphics graphics) {
        int x = leftPos + SLGuiTextures.LinkConfigurator.CONNECTION_X
            + CONNECTION_TEXT_INSET;
        int y = topPos + SLGuiTextures.LinkConfigurator.CONNECTION_Y
            + CONNECTION_TEXT_INSET;
        graphics.drawString(font, Component.translatable("gui.staticlogistics.current_connection"),
            x, y, COLOR_ACCENT, false);
        ClientConnection connection = preview.getSelectedConnection();
        LogisticsNode selectedNode = preview.getSelectedNode();
        if (connection != null) {
            String first = "A  " + compactNode(connection.first()).getString();
            String second = "B  " + compactNode(connection.second()).getString();
            int maximumWidth = SLGuiTextures.LinkConfigurator.CONNECTION_WIDTH - 10;
            graphics.drawString(font, font.plainSubstrByWidth(first, maximumWidth),
                x, y + CONNECTION_FIRST_LINE_Y, COLOR_ACCENT, false);
            graphics.drawString(font, font.plainSubstrByWidth(second, maximumWidth),
                x, y + CONNECTION_SECOND_LINE_Y, COLOR_SECONDARY, false);
        } else if (selectedNode != null) {
            graphics.drawString(font, compactNode(selectedNode),
                x, y + CONNECTION_SINGLE_LINE_Y, COLOR_ACCENT, false);
        } else {
            int maximumWidth = SLGuiTextures.LinkConfigurator.CONNECTION_WIDTH
                - CONNECTION_TEXT_INSET * 2;
            List<FormattedCharSequence> lines = font.split(
                Component.translatable("gui.staticlogistics.network_preview.select_hint"),
                maximumWidth);
            for (int index = 0; index < Math.min(lines.size(), 2); index++) {
                graphics.drawString(font, lines.get(index), x,
                    y + CONNECTION_EMPTY_HINT_Y + index * font.lineHeight,
                    COLOR_DISABLED, false);
            }
        }
    }

    private Component compactNode(LogisticsNode node) {
        return NodeDisplayText.compact(node);
    }

    private void renderTopTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        TransferTypeGrid.View typeView = transferTypeView();
        var hoveredType = TransferTypeGrid.getHoveredType(mouseX, mouseY, typeView, leftPos, topPos);
        if (hoveredType != null) {
            TransferTypeGrid.renderTooltip(graphics, font, hoveredType, typeView, mouseX, mouseY);
        } else {
            renderGroupOrModeTooltip(graphics, mouseX, mouseY);
        }
        graphics.pose().popPose();
    }

    private void renderGroupOrModeTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        GroupRef hoveredGroup = groupPanel.getHoveredGroup();
        if (hoveredGroup != null) {
            groupPanel.renderGroupTooltip(graphics, font, mouseX, mouseY, hoveredGroup,
                SLKeyMappings.isKeyDown(SLKeyMappings.GROUP_DETAILS_AND_EXPORT));
            return;
        }
        ClientConnection hoveredConnection = groupPanel.getHoveredConnection();
        if (hoveredConnection != null) {
            groupPanel.renderConnectionTooltip(graphics, font, mouseX, mouseY, hoveredConnection);
            return;
        }
        ToolModeBar.renderTooltip(graphics, font, mouseX, mouseY, leftPos, topPos);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (confirmationDialog != null && confirmationDialog.isOpen()) {
            return confirmationDialog.mouseClicked(mouseX, mouseY, button, width, height);
        }
        if (button == 0 && NodeConfigControls.hitOpBtn(mouseX, mouseY,
            leftPos + PREVIEW_TOGGLE_X, topPos + PREVIEW_TOGGLE_Y)) {
            previewExpanded = !previewExpanded;
            SoundUtil.playClickSound();
            return true;
        }
        if (previewExpanded) {
            ConnectionKey focusBeforePreviewClick = SelectionContext.getFocusedConnectionKey();
            if (preview.mouseClicked(mouseX, mouseY, button,
                previewLeft(), previewTop(), previewWidth(), previewHeight())) {
                if (!Objects.equals(focusBeforePreviewClick,
                    SelectionContext.getFocusedConnectionKey())) syncFocusedConnection(false);
                LogisticsNode node = preview.getSelectedNode();
                if (node != null) openNode(node);
                return true;
            }
            return true;
        }
        if (groupPanel.searchBoxMouseClicked(mouseX, mouseY, button)) {
            setFocused(groupPanel.getSearchBox());
            return true;
        }
        if (groupPanel.renameBoxMouseClicked(mouseX, mouseY, button)) {
            setFocused(groupPanel.getRenameBox());
            return true;
        }
        if (groupPanel.newGroupBoxMouseClicked(mouseX, mouseY, button)) {
            setFocused(groupPanel.getNewGroupBox());
            return true;
        }
        if (groupPanel.isCreateRowHit(mouseX, mouseY, leftPos, topPos)) {
            groupPanel.beginCreateGroup();
            setFocused(groupPanel.getNewGroupBox());
            SoundUtil.playClickSound();
            return true;
        }
        if (TransferTypeGrid.handleNavigationClick(mouseX, mouseY, leftPos, topPos)) {
            SoundUtil.playClickSound();
            return true;
        }
        TransferTypeGrid.View typeView = transferTypeView();
        var clickedType = TransferTypeGrid.getHoveredType(mouseX, mouseY, typeView, leftPos, topPos);
        if (clickedType != null) {
            handleTypeClick(clickedType, typeView);
            return true;
        }
        int clickedMode = ToolModeBar.getClickedMode(mouseX, mouseY, leftPos, topPos);
        if (clickedMode >= 0) {
            modeIdx = clickedMode;
            ItemStack stack = toolStack();
            PortItemStackExtension.setData(
                stack, SLDataComponents.TOOL_MODE.get(), modeIdx);
            SLNetwork.HANDLER.sendToServer(
                new C2SUpdateToolModePayload(modeIdx));
            ToolModeFeedback.show(minecraft.player, stack, ToolMode.fromId(modeIdx));
            SoundUtil.playClickSound();
            return true;
        }
        if (groupPanel.isRenaming()
            && !groupPanel.getRenameBox().isMouseOver(mouseX, mouseY)) {
            handleConfirmRename();
        }
        if (groupPanel.isCreatingGroup()
            && !groupPanel.getNewGroupBox().isMouseOver(
            mouseX, mouseY)) {
            groupPanel.cancelCreateGroup();
        }
        setFocused(null);

        if (groupPanel.isSearchTriggerHit(mouseX, mouseY, leftPos, topPos)) {
            groupPanel.triggerSearch();
            return true;
        }
        if (groupPanel.handleScrollbarClick(mouseX, mouseY, leftPos, topPos)) return true;
        GroupPanel.ClickResult listResult = groupPanel.handleListClick(
            mouseX, mouseY, button, leftPos, topPos, toolStack(),
            SLKeyMappings.isKeyDown(SLKeyMappings.GROUP_DETAILS_AND_EXPORT));
        if (listResult != null) {
            handleGroupResult(listResult);
            return true;
        }

        ConnectionKey focusBeforePreviewClick = SelectionContext.getFocusedConnectionKey();
        if (preview.mouseClicked(mouseX, mouseY, button,
            previewLeft(), previewTop(), previewWidth(), previewHeight())) {
            if (!Objects.equals(
                focusBeforePreviewClick,
                SelectionContext.getFocusedConnectionKey())) {
                syncFocusedConnection(false);
            }
            LogisticsNode node = preview.getSelectedNode();
            if (node != null) {
                openNode(node);
            } else {
                clearNodeTarget();
            }
            return true;
        }
        if (nodeConfigurationPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        nodeConfigurationPanel.unfocusEditors(mouseX, mouseY);
        return handled;
    }

    private void handleGroupResult(GroupPanel.ClickResult result) {
        switch (result.getAction()) {
            case SELECT -> {
                SelectionContext.clearConnectionFocus();
                syncGroup(result.getGroup(), true);
                syncFocusedConnection(false);
                preview.setGroup(result.getGroup().key());
            }
            case RENAME -> {
                groupPanel.startRename(result.getGroup(), leftPos, topPos);
                setFocused(groupPanel.getRenameBox());
            }
            case EXPORT -> {
                groupPanel.exportToChat(result.getGroup());
                onClose();
            }
            case DELETE -> {
                GroupRef group = result.getGroup();
                confirmationDialog = new ConfirmationDialog(
                    Component.translatable("gui.staticlogistics.confirm_delete"),
                    Component.translatable("gui.staticlogistics.confirm_delete_group", group.displayName()),
                    () -> deleteGroup(group));
            }
            case OPEN_CONNECTION -> {
                syncGroup(result.getGroup(), false);
                preview.setGroup(result.getGroup().key());
                preview.selectConnection(Objects.requireNonNull(result.getConnection()));
                syncFocusedConnection(false);
                SoundUtil.playClickSound();
            }
            case RENAME_CONNECTION -> {
                groupPanel.startRename(
                    Objects.requireNonNull(result.getConnection()), leftPos, topPos);
                setFocused(groupPanel.getRenameBox());
            }
            case DELETE_CONNECTION -> {
                ClientConnection connection = Objects.requireNonNull(result.getConnection());
                Component name = connection.displayName().isEmpty()
                    ? Component.translatable("gui.staticlogistics.connection")
                    : Component.literal(connection.displayName());
                confirmationDialog = new ConfirmationDialog(
                    Component.translatable("gui.staticlogistics.confirm_delete"),
                    Component.translatable("gui.staticlogistics.confirm_delete_connection", name),
                    () -> deleteConnection(connection));
            }
            case CONSUME -> {
            }
        }
    }

    private void deleteGroup(GroupRef group) {
        preview.removeLayout(group.key());
        if (preview.isShowingGroup(group.key())) preview.setGroup(null);
        if (group.key().equals(PortItemStackExtension.getData(
            toolStack(), SLDataComponents.SELECTED_GROUP_KEY.get()))) {
            SelectionContext.clearConnectionFocus();
            syncGroup("", false);
            syncFocusedConnection(false);
        }
        if (menu.hasTarget() && group.key().equals(menu.getRemoteGroupKey())) menu.clearTarget();
        SLNetwork.HANDLER.sendToServer(new C2SDeleteGroupPayload(group.key()));
        SoundUtil.playClickSound();
    }

    private void deleteConnection(ClientConnection connection) {
        ClientConnection selected = preview.getSelectedConnection();
        if (selected != null && selected.key().equals(connection.key())) {
            preview.selectConnection(null);
            syncFocusedConnection(false);
        }
        if (menu.hasTarget() && connection.groupKey().equals(menu.getRemoteGroupKey())
            && (menu.getTargetNode().equals(connection.first())
            || menu.getTargetNode().equals(connection.second()))) menu.clearTarget();
        SLNetwork.HANDLER.sendToServer(new C2SDeleteConnectionPayload(connection.key()));
        SoundUtil.playClickSound();
    }

    /**
     * 网络预览选择新节点时请求服务端验证并切换权威数据源。
     * 验证成功后由确认包更新当前 Menu，整个过程不会重建 Screen 或 Menu。
     */
    private void openNode(LogisticsNode node) {
        GroupRef group = selectedGroup();
        if (group == null) return;
        FaceTopology topology = ClientLinkData.INSTANCE.getTopology(node);
        boolean inputSide = topology != null
            && topology.role().canReceive() && !topology.role().canSend();
        SLNetwork.HANDLER.sendToServer(new C2SOpenLinkEndpointPayload(
            group.key(), node, inputSide));
    }

    /**
     * 预览不再选中节点时，同时解除客户端菜单与服务端权威目标的绑定。
     */
    private void clearNodeTarget() {
        if (!menu.hasTarget()) return;
        menu.clearTarget();
        SLNetwork.HANDLER.sendToServer(new C2SClearLinkEndpointPayload());
    }

    /**
     * 在同一个 Menu 内切换输入或输出页签。
     *
     * <p>客户端先更新可见状态以保证操作即时，服务端按网络包顺序更新活动槽位和
     * 编辑权限；这里不得调用打开菜单的数据包。
     */
    private void selectSide(boolean inputSide) {
        menu.selectVisibleSide(inputSide);
        SLNetwork.HANDLER.sendToServer(
            new C2SSelectLinkEndpointSidePayload(inputSide));
    }

    private void openFilter() {
        FilterConfiguratorScreen.prepareNestedOpen(this);
        SLNetwork.HANDLER.sendToServer(new C2SOpenNodeFilterPayload(
            menu.getPos(), menu.getFace(), menu.isInputSideVisible()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (TransferTypeGrid.mouseScrolled(
            mouseX, mouseY, scrollY, leftPos, topPos)) return true;
        if (groupPanel.mouseScrolled(mouseX, mouseY, scrollY, leftPos, topPos)) return true;
        if (preview.mouseScrolled(mouseX, mouseY, scrollY,
            previewLeft(), previewTop(), previewWidth(), previewHeight())) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (groupPanel.mouseDragged(mouseY, topPos)) return true;
        if (preview.mouseDragged(dragX, dragY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        groupPanel.mouseReleased();
        ConnectionKey focusBeforeRelease = SelectionContext.getFocusedConnectionKey();
        if (preview.mouseReleased()) {
            if (!Objects.equals(focusBeforeRelease,
                SelectionContext.getFocusedConnectionKey())) syncFocusedConnection(false);
            if (preview.getSelectedNode() == null) clearNodeTarget();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirmationDialog != null && confirmationDialog.isOpen()) {
            return confirmationDialog.keyPressed(keyCode);
        }
        if (groupPanel.getSearchBox().canConsumeInput()
            || groupPanel.getRenameBox().canConsumeInput()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (groupPanel.isSearchBoxFocused()) groupPanel.triggerSearch();
                else if (groupPanel.isRenameBoxVisible()) handleConfirmRename();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (nodeConfigurationPanel.keyPressed(keyCode)) return true;
        if (groupPanel.isCreatingGroup()
            && groupPanel.getNewGroupBox().canConsumeInput()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                handleNewGroupSubmit();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                groupPanel.cancelCreateGroup();
                setFocused(null);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (Minecraft.getInstance().options.keyInventory.matches(keyCode, scanCode)
            || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void handleNewGroupSubmit() {
        SoundUtil.playClickSound();
        String name = groupPanel.confirmCreateGroup();
        if (!name.isEmpty()) {
            SLNetwork.HANDLER.sendToServer(new C2SCreateEmptyGroupPayload(name));
        }
        setFocused(null);
    }

    private void handleConfirmRename() {
        GroupRef editingGroup = groupPanel.getEditingGroup();
        ClientConnection editingConnection = groupPanel.getEditingConnection();
        if (editingGroup == null && editingConnection == null) return;
        String newName = groupPanel.confirmRename();
        if (editingConnection != null
            && !Objects.equals(editingConnection.displayName(), newName)) {
            SLNetwork.HANDLER.sendToServer(new C2SRenameConnectionPayload(editingConnection.key(), newName));
        } else if (editingGroup != null && !newName.isEmpty()
            && !Objects.equals(editingGroup.displayName(), newName)) {
            SLNetwork.HANDLER.sendToServer(
                new C2SGroupRenamePayload(editingGroup.key(), newName));
        }
    }

    private GroupRef selectedGroup() {
        ItemStack stack = toolStack();
        var key = PortItemStackExtension.getData(stack, SLDataComponents.SELECTED_GROUP_KEY.get());
        return key == null ? null : ClientLinkData.INSTANCE.findGroupRef(key);
    }

    private TransferTypeGrid.View transferTypeView() {
        if (!menu.hasTarget()) {
            return new TransferTypeGrid.View(
                TransferTypeGrid.getToolSelectedTypeIds(toolStack()),
                TransferTypeGrid.Context.TOOL_DEFAULT,
                true);
        }
        if (menu.isOutputSideVisible()) {
            return new TransferTypeGrid.View(
                menu.getSelectedTypeIds(),
                TransferTypeGrid.Context.NODE_OUTPUT,
                menu.isGlobalOutputEnabled());
        }
        FaceTopology topology = ClientLinkData.INSTANCE.getTopology(menu.getTargetNode());
        return new TransferTypeGrid.View(
            topology == null ? List.of() : topology.acceptedTypeIds(),
            TransferTypeGrid.Context.NODE_INPUT,
            false);
    }

    private void handleTypeClick(LogisticsResource<?> type, TransferTypeGrid.View view) {
        if (!view.editable()) {
            return;
        }
        if (view.context() == TransferTypeGrid.Context.TOOL_DEFAULT) {
            ItemStack stack = toolStack();
            TransferTypeGrid.toggleToolType(stack, type);
            List<ResourceLocation> selectedTypeIds =
                TransferTypeGrid.getToolSelectedTypeIds(stack);
            int legacyMask = PortItemStackExtension.getDataOrDefault(
                stack, SLDataComponents.SELECTED_TYPES_MASK.get(), 0);
            SLNetwork.HANDLER.sendToServer(
                new C2SUpdateToolTypesPayload(selectedTypeIds, legacyMask));
            SoundUtil.playClickSound();
            return;
        }
        List<ResourceLocation> selection = TransferTypeSelection.toggle(
            menu.getSelectedTypeIds(), type);
        menu.setSelectedTypeIds(selection);
        FaceConfigurationEdit edit = new FaceConfigurationEdit.SelectedTypesEdit(selection);
        List<LogisticsNode> nodes = preview.getSelectedNodes();
        if (nodes.size() > 1) {
            SLNetwork.HANDLER.sendToServer(new C2SConfigureFacesPayload(
                menu.getRemoteGroupKey(), nodes, edit));
        } else {
            SLNetwork.HANDLER.sendToServer(new C2SConfigureFacePayload(
                menu.getPos(), menu.getFace(), edit));
        }
        SoundUtil.playClickSound();
    }

    private void syncGroup(GroupRef group, boolean playSound) {
        syncGroup(group.displayName(), group, playSound);
    }

    private void syncGroup(String groupName, boolean playSound) {
        syncGroup(groupName, null, playSound);
    }

    private void syncGroup(String groupName, GroupRef group, boolean playSound) {
        ItemStack stack = toolStack();
        GroupKey groupKey = group == null ? null : group.key();
        ConnectionKey connectionKey = SelectionContext.getFocusedConnectionKey();
        if (connectionKey != null
            && !Objects.equals(groupKey, connectionKey.groupKey())) {
            connectionKey = null;
        }
        SelectionContext.setGroupSelection(groupName, groupKey);
        if (connectionKey != null) SelectionContext.focusConnection(connectionKey);
        PortItemStackExtension.setData(stack, SLDataComponents.SELECTED_GROUP.get(), groupName);
        if (group == null) PortItemStackExtension.removeData(stack, SLDataComponents.SELECTED_GROUP_KEY.get());
        else PortItemStackExtension.setData(stack, SLDataComponents.SELECTED_GROUP_KEY.get(), group.key());
        if (connectionKey == null) {
            PortItemStackExtension.removeData(stack, SLDataComponents.SELECTED_CONNECTION_KEY.get());
        } else {
            PortItemStackExtension.setData(stack, SLDataComponents.SELECTED_CONNECTION_KEY.get(), connectionKey);
        }
        SLNetwork.HANDLER.sendToServer(
            new C2SUpdateToolGroupPayload(groupName, groupKey));
        if (playSound) SoundUtil.playClickSound();
    }

    private void syncFocusedConnection(boolean playSound) {
        ItemStack stack = toolStack();
        ConnectionKey connectionKey = SelectionContext.getFocusedConnectionKey();
        GroupKey groupKey = PortItemStackExtension.getData(
            stack, SLDataComponents.SELECTED_GROUP_KEY.get());
        if (connectionKey != null
            && !Objects.equals(groupKey, connectionKey.groupKey())) {
            connectionKey = null;
            SelectionContext.clearConnectionFocus();
        }
        if (connectionKey == null) {
            PortItemStackExtension.removeData(
                stack, SLDataComponents.SELECTED_CONNECTION_KEY.get());
        } else {
            PortItemStackExtension.setData(
                stack, SLDataComponents.SELECTED_CONNECTION_KEY.get(), connectionKey);
        }
        SLNetwork.HANDLER.sendToServer(
            new C2SUpdateToolConnectionPayload(connectionKey));
        if (playSound) SoundUtil.playClickSound();
    }

    private ItemStack toolStack() {
        return menu.getToolStack();
    }

    private int previewLeft() {
        return leftPos + (previewExpanded
            ? EXPANDED_PREVIEW_FRAME_X + 2
            : SLGuiTextures.LinkConfigurator.PREVIEW_X);
    }

    private int previewTop() {
        return topPos + (previewExpanded
            ? EXPANDED_PREVIEW_FRAME_Y + 2
            : SLGuiTextures.LinkConfigurator.PREVIEW_Y);
    }

    private int previewWidth() {
        return previewExpanded
            ? EXPANDED_PREVIEW_FRAME_WIDTH - 4
            : SLGuiTextures.LinkConfigurator.PREVIEW_WIDTH;
    }

    private int previewHeight() {
        return previewExpanded
            ? EXPANDED_PREVIEW_FRAME_HEIGHT - 4
            : SLGuiTextures.LinkConfigurator.PREVIEW_HEIGHT;
    }

    private void renderPreviewToggle(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos + PREVIEW_TOGGLE_X;
        int y = topPos + PREVIEW_TOGGLE_Y;
        NodeConfigControls.renderOpBtn(graphics, x, y, !previewExpanded,
            NodeConfigControls.hitOpBtn(mouseX, mouseY, x, y));
    }

    private void renderExpandedPreview(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 350);
        NetworkPreviewFrame.render(graphics,
            leftPos + EXPANDED_PREVIEW_FRAME_X,
            topPos + EXPANDED_PREVIEW_FRAME_Y,
            EXPANDED_PREVIEW_FRAME_WIDTH,
            EXPANDED_PREVIEW_FRAME_HEIGHT);
        preview.render(graphics, font, previewLeft(), previewTop(),
            previewWidth(), previewHeight(), mouseX, mouseY);
        graphics.pose().popPose();
    }

    private void renderPreviewToggleTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos + PREVIEW_TOGGLE_X;
        int y = topPos + PREVIEW_TOGGLE_Y;
        if (!NodeConfigControls.hitOpBtn(mouseX, mouseY, x, y)) return;
        graphics.renderTooltip(font, Component.translatable(previewExpanded
            ? "gui.staticlogistics.network_preview.restore"
            : "gui.staticlogistics.network_preview.expand"), mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void renderCustomContent(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    /**
     * 新主界面的区域标题均自行定位；禁止继承旧底板的“物品栏”标签坐标。
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    public boolean hasNodeTarget() {
        return menu.hasTarget();
    }
}
