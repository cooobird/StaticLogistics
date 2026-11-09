package com.coobird.staticlogistics.client.gui.screen;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.client.data.*;
import com.coobird.staticlogistics.client.gui.component.*;
import com.coobird.staticlogistics.client.render.SLGuiTextures;
import com.coobird.staticlogistics.content.item.BlueprintModeHandler;
import com.coobird.staticlogistics.content.item.ToolMode;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.content.menu.MenuLayout;
import com.coobird.staticlogistics.logistics.LogisticsUpgrade;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import com.coobird.staticlogistics.logistics.node.ChannelConfigurationEdit;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.node.EndpointTopology;
import com.coobird.staticlogistics.logistics.node.PresetPreviewService;
import com.coobird.staticlogistics.logistics.util.NodeDisplayText;
import com.coobird.staticlogistics.network.c2s.*;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.coobird.staticlogistics.client.gui.ConfiguratorLayout.ConfiguratorScreen.*;

/// 组织连接配置界面，协调各面板和输入事件
public class LinkConfiguratorScreen extends AbstractConfiguratorScreen<LinkConfiguratorMenu> {
    private long presetPreviewSequence;
    private PendingPresetPreview pendingPresetPreview;

    private record PendingPresetPreview(long requestId, LogisticsNode focus, LogisticsNode inspected,
                                        List<ChannelConfigurationEdit> edits, Map<LogisticsNode, GroupKey> targets) {
    }

    // 配置界面外框宽度
    // 配置器外框和左右面板的布局。
    private static final int OUTER_WIDTH = SLGuiTextures.LinkConfigurator.WIDTH;
    // 外框底部相对界面原点的偏移
    private static final int OUTER_BOTTOM_OFFSET = SLGuiTextures.LinkConfigurator.HEIGHT - SLGuiTextures.LinkConfigurator.CONTENT_INSET;
    // 外框高度，由顶部和底部位置计算
    private static final int OUTER_HEIGHT = OUTER_BOTTOM_OFFSET - OUTER_TOP_OFFSET;

    /// 区域组件分别保存自己的滚动、选择或编辑状态
    private final GroupContentsPanel groupContents = new GroupContentsPanel();

    private GroupPanel groupPanel;
    private NodeConfigurationPanel nodeConfigurationPanel;
    private final ConfiguratorEditSession editSession;
    private Button resourceRatesButton;
    private Map<LogisticsNode, GroupKey> batchTargets = Map.of();

    private ResourceTypeDialog resourceDialog;
    private final ConfiguratorTabs tabs = new ConfiguratorTabs(this::toolStack, this::selectToolMode, this::showBlueprint);
    private BlueprintPage blueprintPage;
    private boolean blueprintVisible;
    private boolean tabPointer;
    private final ContainerWindow<LinkConfiguratorMenu> containerWindow;
    private final ScreenWidgetLayer screenWidgets = new ScreenWidgetLayer(this);
    private int screenWidgetButton = -1;

    public boolean isBlueprintPage() {
        return blueprintVisible;
    }

    public double interfaceScale() {
        return interfaceScale;
    }

    public int contentLeft() {
        return leftPos;
    }

    public int contentTop() {
        return topPos;
    }

    public void acceptBlueprintCopy(UUID request, boolean success) {
        if (blueprintPage != null) blueprintPage.copyResult(request, success);
    }

    private void selectToolMode(ToolMode mode) {
        blueprintVisible = false;
        if (ToolModeSession.currentMode(toolStack()) != mode
            || BlueprintModeHandler.isActive(toolStack()))
            ToolModeSession.select(toolStack(), mode);
    }

    private void showBlueprint() {
        nodeConfigurationPanel.unfocusEditors(-1, -1);
        containerWindow.setFocused(null);
        ToolModeSession.select(toolStack(), ToolMode.BLUEPRINT);
        displayBlueprint();
    }

    private void displayBlueprint() {
        blueprintVisible = true;
        if (blueprintPage == null) {
            blueprintPage = new BlueprintPage(toolStack(), this);
            blueprintPage.layout(minecraft, virtualWidth, virtualHeight);
        }
    }

    /// 对外提供屏幕坐标，不把缩放前的窗口坐标交给 JEI
    public List<Rect2i> overlayAreas() {
        List<Rect2i> areas = new ArrayList<>();
        areas.add(screenArea(new Rect2i(leftPos - tabs.width() - 6, topPos - 3,
            OUTER_WIDTH + tabs.width() + 12, OUTER_HEIGHT + 6)));
        if (resourceDialog != null) areas.add(screenArea(resourceDialog.bounds()));
        if (groupPanel != null) groupPanel.dialogBounds().forEach(bounds -> areas.add(screenArea(bounds)));
        return areas;
    }

    private Rect2i screenArea(Rect2i area) {
        int x = (int) Math.floor((area.getX() + horizontalOffset() + windowOffsetX) * interfaceScale);
        int y = (int) Math.floor((area.getY() + windowOffsetY) * interfaceScale);
        int right = (int) Math.ceil((area.getX() + area.getWidth() + horizontalOffset() + windowOffsetX) * interfaceScale);
        int bottom = (int) Math.ceil((area.getY() + area.getHeight() + windowOffsetY) * interfaceScale);
        return new Rect2i(x, y, right - x, bottom - y);
    }

    private int horizontalOffset() {
        return Math.max(0, tabs.width() + 7 - leftPos);
    }

    private int windowMinimumX() {
        return tabs.width() + 6 - horizontalOffset();
    }

    private int windowMaximumX() {
        int viewportWidth = (int) Math.floor(width / interfaceScale);
        return Math.max(windowMinimumX(), viewportWidth - horizontalOffset() - OUTER_WIDTH - 6);
    }

    private int windowMaximumY() {
        int viewportHeight = (int) Math.floor(height / interfaceScale);
        return Math.max(OUTER_FRAME_INSET, viewportHeight - SLGuiTextures.LinkConfigurator.HEIGHT - OUTER_FRAME_INSET);
    }

    private void clampWindowOffset() {
        windowOffsetX = Mth.clamp(leftPos + windowOffsetX, windowMinimumX(), windowMaximumX()) - leftPos;
        windowOffsetY = Mth.clamp(topPos + windowOffsetY, OUTER_FRAME_INSET, windowMaximumY()) - topPos;
    }

    private double interfaceScale = 1.0D;
    private int windowOffsetX, windowOffsetY;
    private final WindowDrag windowDrag = new WindowDrag();
    private int virtualWidth;
    private int virtualHeight;

    private record SavedView(GroupKey group, GroupPanel.ViewState directory, GroupContentsPanel.ViewState contents,
                             LogisticsNode node, boolean input, NodeConfigurationPanel.Draft draft,
                             BlueprintPage.ViewState blueprintView) {
    }

    private static final Map<ClientPacketListener, Map<UUID, SavedView>> SAVED_VIEWS = new WeakHashMap<>();
    private SavedView restoreView;

    private long beginRequest() {
        return beginRequest(null);
    }

    private long beginRequest(ConfiguratorEditSession.Target intent) {
        nodeConfigurationPanel.unfocusEditors(-1, -1);
        return editSession.begin(intent);
    }

    public void confirmConfiguration(long requestId, List<ResourceLocation> selectedTypes) {
        var completion = editSession.acknowledge(requestId);
        if (completion != ConfiguratorEditSession.Completion.IGNORED) menu.acceptSelectedTypeIds(selectedTypes);
        switch (completion) {
            case NUMBER -> nodeConfigurationPanel.tick();
            case REQUEST -> {
                discardBatch();
                refreshConfigurationView();
                editSession.resumeNavigation();
            }
            case IGNORED -> {
            }
        }
    }

    private int restoreTicks;
    private NodeConfigurationPanel.Draft restoreDraft;

    private SavedView captureView() {
        if (restoreView != null) return restoreView;
        ConfiguratorEditSession.Target intent = editSession.intendedTarget();
        var focus = editSession.pendingFocus();
        LogisticsNode node = intent != null ? intent.node() : focus != null ? focus.node()
            : menu.hasTarget() ? menu.getTargetNode() : null;
        EndpointTopology topology = node == null ? null : ClientLinkData.INSTANCE.getTopology(node);
        boolean input = intent != null ? intent.input() : focus != null
            ? topology != null && topology.role().canReceive() && !topology.role().canSend() : menu.isInputSideVisible();
        return new SavedView(toolStack().get(SLDataComponents.SELECTED_GROUP_KEY.get()), groupPanel.saveView(), groupContents.saveView(),
            node, input, restoreDraft != null ? restoreDraft : nodeConfigurationPanel.saveDraft(),
            blueprintPage == null ? null : blueprintPage.saveView());
    }

    @Override
    public void onClose() {
        nodeConfigurationPanel.unfocusEditors(-1, -1);
        editSession.clearBatchDraft();
        editSession.close();
        super.onClose();
    }

    @Override
    public void removed() {
        UUID toolSession = toolStack().get(SLDataComponents.TOOL_SESSION.get());
        if (groupPanel != null && toolSession != null && Minecraft.getInstance().getConnection() != null) {
            SAVED_VIEWS.computeIfAbsent(Minecraft.getInstance().getConnection(), ignored -> new HashMap<>()).put(toolSession, captureView());
        }
        super.removed();
    }

    public void acceptTarget(long requestId, GroupKey group, LogisticsNode node, boolean input, List<ResourceLocation> types) {
        if (!editSession.acceptsTarget(requestId, node)) return;
        discardBatch();
        menu.selectTarget(node, group, input, types);
        groupContents.selectNode(node);
    }

    private void refreshConfigurationView() {
        groupContents.selectNode(menu.hasTarget() ? menu.getTargetNode() : null);
        nodeConfigurationPanel.tick();
        nodeConfigurationPanel.restoreDraft(restoreDraft);
        restoreDraft = null;
    }

    public LinkConfiguratorScreen(LinkConfiguratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        containerWindow = new ContainerWindow<>(menu, inventory, title) {
            @Override
            protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
                LinkConfiguratorScreen.this.renderBg(graphics, partialTick, mouseX, mouseY);
            }

            @Override
            public void onClose() {
                LinkConfiguratorScreen.this.onClose();
            }
        };
        editSession = new ConfiguratorEditSession(submission -> PacketDistributor.sendToServer(
            new C2SConfigureFacePayload(submission.node().gPos().pos(), submission.node().face(),
                submission.node().channel(), List.of(submission.edit()), menu.containerId, submission.requestId(), Map.of(submission.node(), menu.getRemoteGroupKey()))));
    }

    public void acceptHierarchy(CompoundTag data) {
        ConnectionDirectoryModel.acceptOwnerPermissions(data);
        groupPanel.acceptHierarchy(data);
        if (blueprintPage != null) blueprintPage.acceptHierarchy(data);
    }

    public void acceptCreatedChannel(long requestId, ConnectionKey connection, LogisticsNode node) {
        editSession.acceptCreatedChannel(requestId, connection, node);
    }

    private void focusChannel(ConnectionKey connection, LogisticsNode node) {
        editSession.focusChannel(connection, node);
    }

    private ConnectionKey activeNodeConnection() {
        if (!menu.hasTarget()) return null;
        return groupPanel.getSelectedConnections().stream()
            .filter(key -> key.groupKey().equals(menu.getRemoteGroupKey()) && key.contains(menu.getTargetNode().gPos()))
            .findFirst().orElse(null);
    }

    private List<LogisticsNode> channelOptions() {
        if (!menu.hasTarget() || menu.getRemoteGroupKey() == null) return List.of();
        LogisticsNode current = menu.getTargetNode();
        return ClientLinkData.INSTANCE.getNodesForGroup(menu.getRemoteGroupKey()).stream()
            .filter(node -> !node.isConnectionFace() && node.gPos().equals(current.gPos()) && node.face() == current.face())
            .sorted(Comparator.comparingInt(LogisticsNode::channel)).toList();
    }

    @Override
    protected void init() {
        UUID toolSession = toolStack().get(SLDataComponents.TOOL_SESSION.get());
        restoreView = groupPanel != null ? captureView() : toolSession == null ? null
            : SAVED_VIEWS.getOrDefault(Minecraft.getInstance().getConnection(), Map.of()).get(toolSession);
        restoreTicks = 0;
        this.imageWidth = SLGuiTextures.LinkConfigurator.CONTENT_WIDTH;
        this.imageHeight = SLGuiTextures.LinkConfigurator.CONTENT_HEIGHT;
        updateResponsiveViewport();
        super.init();
        this.leftPos = (virtualWidth - OUTER_WIDTH) / 2
            + SLGuiTextures.LinkConfigurator.CONTENT_INSET;
        this.topPos = (virtualHeight - OUTER_HEIGHT) / 2 - OUTER_TOP_OFFSET;
        clampWindowOffset();
        windowDrag.release();
        screenWidgetButton = -1;
        containerWindow.layout(minecraft, virtualWidth, virtualHeight, leftPos, topPos, imageWidth, imageHeight);
        this.titleLabelX = this.imageWidth - this.font.width(this.title) - TITLE_RIGHT_PADDING;
        this.titleLabelY = TITLE_Y;
        /// 原版物品栏标签由 atlas 区域标题替代，移出可视范围避免重复绘制
        this.inventoryLabelY = HIDDEN_VANILLA_INVENTORY_LABEL_Y;

        /// 顶部工具状态来自配置器物品
        ItemStack stack = toolStack();

        /// 右上目录与左上链接组编辑区共享同一个选中分组
        groupPanel = new GroupPanel(font, leftPos, topPos);
        groupPanel.setContextAction(this::handleGroupResult);
        PacketDistributor.sendToServer(new C2SGroupHierarchyPayload("query", "", "", ""));
        groupContents.init(font, groupContentLeft(), groupContentTop(), groupContentWidth());
        groupContents.setGroup(stack.get(SLDataComponents.SELECTED_GROUP_KEY.get()));
        if (menu.hasTarget()) {
            groupContents.selectNode(menu.getTargetNode());
        }

        /// 左下节点区复用当前 Menu；页签切换不会创建新容器界面
        nodeConfigurationPanel = new NodeConfigurationPanel(menu, font, this::openFilter, SoundUtil::playClickSound);
        nodeConfigurationPanel.init(leftPos, topPos, containerWindow::addControl);
        nodeConfigurationPanel.setSubmission(this::submitConfiguration);
        nodeConfigurationPanel.setBatchActions(editSession, this::applyBatch, this::discardBatch);
        nodeConfigurationPanel.setPresetActions(this::savePreset, this::applyPreset);
        nodeConfigurationPanel.setPendingValues(editSession::pendingValue);
        nodeConfigurationPanel.tick();
        nodeConfigurationPanel.setChannelActions(() -> {
            var connection = activeNodeConnection();
            if (connection != null) {
                PacketDistributor.sendToServer(new C2SCreateChannelPayload(connection, menu.containerId, beginRequest()));
            }
        }, this::channelOptions, node -> {
            var connection = activeNodeConnection();
            if (connection != null) focusChannel(connection, node);
        });
        nodeConfigurationPanel.setChannelContext((channel, x, y) -> {
            var connection = activeNodeConnection();
            if (connection == null) return;
            groupPanel.openChannelContext(x, y, NodeConfigurationPanel.channelName(channel).getString(), (operation, name) -> {
                PacketDistributor.sendToServer(new C2SManageChannelPayload(menu.containerId, connection, channel, operation, name, beginRequest()));
            });
        });
        resourceRatesButton = containerWindow.addControl(AtlasButton.atlasBuilder(
                Component.empty(), ignored -> openResourceTypes(true))
            .bounds(leftPos + RESOURCE_RATES_X, topPos + SLGuiTextures.LinkConfigurator.NODE_CONFIG_Y + nodeConfigurationPanel.resourceRowY(), RESOURCE_RATES_WIDTH, RESOURCE_RATES_HEIGHT).build().rateIcon());
        resourceRatesButton.setTooltip(Tooltip.create(Component.translatable("gui.staticlogistics.resources.rates")));
        resourceRatesButton.visible = menu.hasTarget();
        if (resourceDialog != null) resourceDialog.layout(virtualWidth, virtualHeight);
        if (blueprintPage == null && restoreView != null && restoreView.blueprintView() != null)
            blueprintPage = new BlueprintPage(toolStack(), this);
        if (blueprintPage != null) {
            blueprintPage.layout(minecraft, virtualWidth, virtualHeight);
            if (restoreView != null && restoreView.blueprintView() != null)
                blueprintPage.restoreView(restoreView.blueprintView());
        }
        blueprintVisible = ToolModeSession.currentMode(toolStack()) == ToolMode.BLUEPRINT;
        if (blueprintVisible && blueprintPage == null) displayBlueprint();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        updateBatchSelection();
        ConfiguratorStyle.window(graphics, leftPos - OUTER_FRAME_INSET, topPos - OUTER_FRAME_INSET, OUTER_WIDTH + OUTER_FRAME_INSET * 2, SLGuiTextures.LinkConfigurator.HEIGHT + OUTER_FRAME_INSET * 2);
        ConfiguratorStyle.panel(graphics, leftPos + LEFT_PANEL_X, topPos + LEFT_PANEL_Y, LEFT_PANEL_WIDTH, SLGuiTextures.LinkConfigurator.NODE_CONFIG_Y - UPPER_PANEL_BOTTOM_INSET);
        ConfiguratorStyle.window(graphics, leftPos + RIGHT_PANEL_X, topPos + RIGHT_PANEL_Y, RIGHT_PANEL_WIDTH, DIRECTORY_PANEL_HEIGHT);
        ConfiguratorStyle.panel(graphics, leftPos + LEFT_PANEL_X, topPos + SLGuiTextures.LinkConfigurator.NODE_CONFIG_Y + NODE_PANEL_Y_OFFSET, LEFT_PANEL_WIDTH, SLGuiTextures.LinkConfigurator.NODE_CONFIG_HEIGHT + NODE_PANEL_HEIGHT_OFFSET);
        SLGuiTextures.WINDOW.frame(graphics, leftPos + RIGHT_PANEL_X, topPos + INVENTORY_PANEL_Y, RIGHT_PANEL_WIDTH, INVENTORY_PANEL_HEIGHT, INVENTORY_FRAME_BORDER);
        ConfiguratorStyle.inventory(graphics, leftPos + MenuLayout.LINK_INVENTORY_X,
            topPos + MenuLayout.LINK_INVENTORY_Y);
        renderToolbarLabels(graphics);
        groupContents.render(graphics, font, groupContentLeft(), groupContentTop(),
            groupContentWidth(), groupContentHeight(), mouseX, mouseY, interfaceScale);
        groupPanel.render(graphics, font, leftPos, topPos, mouseX, mouseY, partialTick, interfaceScale);
        nodeConfigurationPanel.render(graphics, mouseX, mouseY);
        if (menu.hasTarget() && !menu.canEditTarget()) {
            Component readOnly = Component.translatable("gui.staticlogistics.permissions.read_only");
            graphics.drawString(font, readOnly, leftPos + LEFT_PANEL_WIDTH - font.width(readOnly) - 6,
                topPos + SLGuiTextures.LinkConfigurator.NODE_CONFIG_Y + 5, ConfiguratorStyle.MUTED_TEXT, false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderWindow(graphics, mouseX, mouseY, partialTick);
        // 外层只保留外部 Screen.Init 注册的控件，始终使用屏幕坐标。
        for (var renderable : renderables) renderable.render(graphics, mouseX, mouseY, partialTick);
    }

    /// 只绘制本模组窗口；嵌套过滤器背景也使用此入口，不重复绘制外部控件。
    void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 页签展开和分辨率变化后也按完整外框约束，而非只限制标题栏。
        clampWindowOffset();
        if (blueprintVisible) hoveredSlot = null;
        int viewportX = -horizontalOffset() - windowOffsetX;
        int viewportY = -windowOffsetY;
        if (resourceDialog != null) resourceDialog.setViewportOrigin(viewportX, viewportY);
        groupPanel.constrainDialogs(viewportX, viewportY, virtualWidth, virtualHeight);
        int virtualMouseX = toVirtual(mouseX) - horizontalOffset() - windowOffsetX;
        int virtualMouseY = toVirtual(mouseY) - windowOffsetY;
        graphics.pose().pushPose();
        graphics.pose().scale((float) interfaceScale, (float) interfaceScale, 1.0F);
        graphics.pose().translate(horizontalOffset() + windowOffsetX, windowOffsetY, 0);
        if (blueprintVisible) blueprintPage.renderWithTooltip(graphics, virtualMouseX, virtualMouseY, partialTick);
        else renderInterface(graphics, virtualMouseX, virtualMouseY, partialTick);
        if (!groupPanel.hasDialog() && resourceDialog == null)
            tabs.render(graphics, font, leftPos - 3, topPos, OUTER_HEIGHT, virtualMouseX, virtualMouseY, blueprintVisible);
        graphics.pose().popPose();
    }

    /// 所有容器内容始终在同一套虚拟坐标中绘制，避免视觉缩放后槽位与鼠标命中区域错位
    private void renderInterface(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        containerWindow.renderWithTooltip(graphics, mouseX, mouseY, partialTick);
        hoveredSlot = containerWindow.getSlotUnderMouse();
        if (resourceDialog != null) {
            resourceDialog.render(graphics, font, virtualWidth, virtualHeight, mouseX, mouseY, partialTick);
            return;
        }
        if (groupPanel.hasDialog()) {
            groupPanel.renderDialog(graphics, mouseX, mouseY);
            return;
        }
        /// 玩家背包和快捷栏必须完整走原版容器 Tooltip 流程，才能保留物品名称、
        /// 附魔、数据组件以及其他模组追加的 Tooltip配置槽由节点区域在原版
        /// 物品 Tooltip 后追加升级统计，因此不能在这里重复绘制
        if (nodeConfigurationPanel.renderChannelDropdown(graphics, mouseX, mouseY)) return;
        Slot hoveredSlot = getSlotUnderMouse();
        if (hoveredSlot != null
            && !hoveredSlot.getItem().isEmpty()
            && !menu.isConfigurationSlot(hoveredSlot)) {
            containerWindow.renderItemTooltip(graphics, mouseX, mouseY);
            return;
        }

        /// Tooltip 必须最后绘制，否则会被节点区、分组区或真实槽位覆盖
        groupContents.renderTooltip(graphics, font, mouseX, mouseY,
            groupContentLeft(), groupContentTop(), groupContentWidth(), groupContentHeight());
        nodeConfigurationPanel.renderTooltips(graphics, mouseX, mouseY);
        renderTopTooltips(graphics, mouseX, mouseY);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        boolean selectedBlueprint = ToolModeSession.currentMode(toolStack()) == ToolMode.BLUEPRINT;
        if (selectedBlueprint && !blueprintVisible) displayBlueprint();
        else if (!selectedBlueprint) blueprintVisible = false;
        if (blueprintVisible && blueprintPage != null) blueprintPage.tick();
        editSession.flush();
        if (editSession.expired()) {
            minecraft.player.displayClientMessage(Component.translatable("gui.staticlogistics.request_timeout"), false);
            onClose();
            return;
        }
        updateBatchSelection();
        if (restoreView != null && editSession.canEdit()) {
            SavedView saved = restoreView;
            if (groupPanel.restoreView(saved.directory())) {
                restoreView = null;
                var group = saved.group() == null ? null : ClientLinkData.INSTANCE.findGroupRef(saved.group());
                if (group != null) syncGroup(group, false);
                groupContents.setGroup(saved.group());
                groupContents.setConnections(groupPanel.getSelectedConnections(), groupPanel.getSelectedConnection());
                groupContents.restoreView(saved.contents());
                SelectedWorldLinks.set(groupPanel.getSelectedConnections());
                if (saved.node() != null && ClientLinkData.INSTANCE.getTopology(saved.node()) != null
                    && groupPanel.getSelectedConnections().stream().anyMatch(key -> key.contains(saved.node().gPos()))) {
                    groupContents.selectNode(saved.node());
                    restoreDraft = saved.draft();
                    openNode(saved.node(), saved.input());
                } else clearNodeTarget();
            } else if (++restoreTicks >= 100) restoreView = null;
        }
        if (restoreView == null && editSession.canReconcileSelection() && menu.hasTarget() && !groupPanel.hasDirectorySelection() && groupPanel.getSelectedConnection() == null && menu.getRemoteGroupKey() != null) {
            LogisticsNode target = menu.getTargetNode();
            ClientLinkData.INSTANCE.getConnectionsForGroup(menu.getRemoteGroupKey()).stream()
                .filter(connection -> connection.key().contains(target.gPos()))
                .findFirst().ifPresent(connection -> focusChannel(connection.key(), target));
        }
        editSession.advanceFocus(
            focus -> ClientLinkData.INSTANCE.getTopology(focus.node()) != null
                && groupPanel.focusConnection(focus.connection()),
            focus -> {
                var connection = focus.connection();
                LogisticsNode node = focus.node();
                syncGroup(ClientLinkData.INSTANCE.findGroupRef(connection.groupKey()), false);
                groupContents.setGroup(connection.groupKey());
                groupContents.setConnections(Set.of(connection), connection);
                SelectedWorldLinks.set(groupPanel.getSelectedConnections());
                groupContents.selectNode(node);
                if (menu.hasTarget() && menu.getTargetNode().equals(node)) refreshConfigurationView();
                else openNode(node);
            });
        groupContents.setConnections(groupPanel.getSelectedConnections(), groupPanel.getSelectedConnection());
        if (editSession.canReconcileSelection() && groupPanel.hasDirectorySelection() && menu.hasTarget()
            && groupPanel.getSelectedConnections().stream().noneMatch(key -> key.contains(menu.getTargetNode().gPos())))
            clearNodeTarget();
        nodeConfigurationPanel.tick();
        resourceRatesButton.visible = menu.hasTarget() && !menu.getTargetNode().isConnectionFace();
        resourceRatesButton.setY(topPos + SLGuiTextures.LinkConfigurator.NODE_CONFIG_Y + nodeConfigurationPanel.resourceRowY());
        if (!menu.hasTarget()) resourceDialog = null;
    }

    private void renderToolbarLabels(GuiGraphics graphics) {
        graphics.drawString(font, playerInventoryTitle,
            leftPos + SLGuiTextures.LinkConfigurator.INVENTORY_X,
            topPos + SLGuiTextures.LinkConfigurator.INVENTORY_Y
                + INVENTORY_TITLE_Y_OFFSET,
            ConfiguratorStyle.TEXT, false);
    }

    private Component compactNode(LogisticsNode node) {
        return NodeDisplayText.compact(node);
    }

    private void renderTopTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        renderGroupTooltip(graphics, mouseX, mouseY);
        graphics.pose().popPose();
    }

    private void renderGroupTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        GroupRef hoveredGroup = groupPanel.getHoveredGroup();
        if (hoveredGroup != null) {
            groupPanel.renderGroupTooltip(graphics, font, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (resourceDialog == null && !groupPanel.hasDialog()
            && !nodeConfigurationPanel.isChannelDropdownOpen()
            && screenWidgets.mouseClicked(mouseX, mouseY, button)) {
            screenWidgetButton = button;
            containerWindow.setFocused(null);
            if (blueprintPage != null) blueprintPage.setFocused(null);
            nodeConfigurationPanel.unfocusEditors(-1, -1);
            return true;
        }
        screenWidgets.setFocused(null);
        mouseX = toVirtual(mouseX) - horizontalOffset() - windowOffsetX;
        mouseY = toVirtual(mouseY) - windowOffsetY;
        if (resourceDialog != null) return resourceDialog.mouseClicked(mouseX, mouseY, button);
        if (groupPanel.hasDialog()) return groupPanel.directoryMouseClicked(mouseX, mouseY, button);
        if (resourceDialog == null && !groupPanel.hasDialog()
            && !nodeConfigurationPanel.isChannelDropdownOpen()
            && windowDrag.begin(mouseX + windowOffsetX, mouseY + windowOffsetY, button,
            leftPos + windowOffsetX, topPos + windowOffsetY,
            leftPos + windowOffsetX, topPos + windowOffsetY - OUTER_FRAME_INSET,
            OUTER_WIDTH, OUTER_FRAME_INSET + 5)) return true;
        if (!groupPanel.hasDialog() && resourceDialog == null && tabs.mouseClicked(mouseX, mouseY, button)) {
            tabPointer = true;
            nodeConfigurationPanel.unfocusEditors(-1, -1);
            containerWindow.setFocused(null);
            return true;
        }
        if (blueprintVisible) return blueprintPage.mouseClicked(mouseX, mouseY, button);
        if (restoreView != null || editSession.awaiting() && button != 0) return true;
        if (nodeConfigurationPanel.channelDropdownClicked(mouseX, mouseY, button)) return true;
        if (groupPanel.directoryMouseClicked(mouseX, mouseY, button)) return true;
        if (groupContents.searchBoxMouseClicked(mouseX, mouseY, button)) {
            containerWindow.setFocused(groupContents.getSearchBox());
            return true;
        }
        if (groupPanel.searchBoxMouseClicked(mouseX, mouseY, button)) {
            containerWindow.setFocused(groupPanel.getSearchBox());
            return true;
        }
        nodeConfigurationPanel.unfocusEditors(mouseX, mouseY);
        if (nodeConfigurationPanel.focusedEditor() == null) containerWindow.setFocused(null);

        if (groupPanel.isSearchTriggerHit(mouseX, mouseY, leftPos, topPos)) {
            groupPanel.triggerSearch();
            return true;
        }
        if (groupPanel.handleScrollbarClick(mouseX, mouseY, leftPos, topPos)) return true;
        GroupPanel.ClickResult listResult = groupPanel.handleListClick(
            mouseX, mouseY, button, leftPos, topPos);
        if (listResult != null) {
            handleGroupResult(listResult);
            return true;
        }

        if (menu.hasTarget() && !menu.canEditTarget()
            && mouseX >= leftPos && mouseX < leftPos + LEFT_PANEL_WIDTH
            && mouseY >= topPos + SLGuiTextures.LinkConfigurator.NODE_CONFIG_Y
            && mouseY < topPos + SLGuiTextures.LinkConfigurator.NODE_CONFIG_Y + SLGuiTextures.LinkConfigurator.NODE_CONFIG_HEIGHT)
            return true;

        var numberEditor = nodeConfigurationPanel.editorAt(mouseX, mouseY);
        if (numberEditor != null && editSession.canEdit()) {
            containerWindow.setFocused(numberEditor);
            return numberEditor.mouseClicked(mouseX, mouseY, button);
        }
        if (!editSession.canEdit()) return true;
        LogisticsNode nodeBeforeClick = groupContents.getSelectedNode();
        if (groupContents.mouseClicked(mouseX, mouseY, button,
            groupContentLeft(), groupContentTop(), groupContentWidth(), groupContentHeight())) {
            LogisticsNode node = groupContents.getSelectedNode();
            if (node != null && (!menu.hasTarget() || !node.equals(menu.getTargetNode()) || !Objects.equals(nodeBeforeClick, node))) {
                openNode(node);
            } else if (node == null && nodeBeforeClick != null) {
                clearNodeTarget();
            }
            return true;
        }
        int resourceY = topPos + SLGuiTextures.LinkConfigurator.NODE_CONFIG_Y + nodeConfigurationPanel.resourceRowY();
        if (button == 0 && menu.hasTarget() && menu.showsOutputSettings() && !menu.getTargetNode().isConnectionFace()
            && mouseX >= leftPos + NodeConfigurationPanel.RESOURCE_BUTTON_X && mouseX < leftPos + NodeConfigurationPanel.RESOURCE_BUTTON_X + NodeConfigurationPanel.RESOURCE_BUTTON_WIDTH
            && mouseY >= resourceY && mouseY < resourceY + NodeConfigurationPanel.RESOURCE_BUTTON_HEIGHT) {
            openResourceTypes(false);
            return true;
        }
        if (nodeConfigurationPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        boolean handled = containerWindow.mouseClicked(mouseX, mouseY, button);
        nodeConfigurationPanel.unfocusEditors(mouseX, mouseY);
        return handled;
    }

    private void handleGroupResult(GroupPanel.ClickResult result) {
        if (editSession.deferNavigation(() -> handleGroupResult(result))) {
            SelectedWorldLinks.set(groupPanel.getSelectedConnections());
            return;
        }
        discardBatch();
        switch (result.getAction()) {
            case SELECT -> {
                if (menu.hasTarget()
                    && !result.getGroup().key().equals(menu.getRemoteGroupKey())) {
                    clearNodeTarget();
                }
                syncGroup(result.getGroup(), true);
                syncFocusedConnection(false);
                groupContents.setGroup(result.getGroup().key());
                groupContents.setConnections(groupPanel.getSelectedConnections(), groupPanel.getSelectedConnection());
                SelectedWorldLinks.set(groupPanel.getSelectedConnections());
                if (menu.hasTarget() && !groupContents.displays(menu.getTargetNode())) clearNodeTarget();
            }
            case CONSUME -> {
                if (result.getGroup() == null) {
                    var selected = groupPanel.getSelectedConnections();
                    if (!selected.isEmpty()) {
                        var first = selected.iterator().next();
                        var group = ClientLinkData.INSTANCE.findGroupRef(first.groupKey());
                        if (group != null) {
                            syncGroup(group, false);
                            groupContents.setGroup(group.key());
                        }
                    }
                    groupContents.setConnections(selected, groupPanel.getSelectedConnection());
                    SelectedWorldLinks.set(selected);
                    if (menu.hasTarget() && !groupContents.displays(menu.getTargetNode())) clearNodeTarget();
                }
            }
        }
    }


    /// 端点管理选择连接面时请求服务端验证并切换权威数据源
    /// 验证成功后由确认包更新当前 Menu，整个过程不会重建 Screen 或 Menu
    private void openNode(LogisticsNode node) {
        GroupRef group = selectedGroup();
        if (group == null) return;
        EndpointTopology topology = ClientLinkData.INSTANCE.getTopology(node);
        boolean inputSide = topology != null
            && topology.role().canReceive() && !topology.role().canSend();
        openNode(node, inputSide);
    }

    private void openNode(LogisticsNode node, boolean inputSide) {
        GroupRef group = ClientLinkData.INSTANCE.findGroupRef(groupContents.groupForNode(node));
        if (group == null) return;
        PacketDistributor.sendToServer(new C2SOpenLinkEndpointPayload(
            group.key(), node, inputSide, menu.containerId, beginRequest(new ConfiguratorEditSession.Target(node, inputSide))));
    }

    /// 端点清单不再选中节点时，同时解除客户端菜单与服务端权威目标的绑定
    private void clearNodeTarget() {
        discardBatch();
        if (!editSession.canEdit()) return;
        editSession.flush();
        groupContents.selectNode(null);
        if (!menu.hasTarget()) return;
        menu.clearTarget();
        PacketDistributor.sendToServer(new C2SClearLinkEndpointPayload(menu.containerId));
    }

    private void openFilter() {
        nodeConfigurationPanel.unfocusEditors(-1, -1);
        editSession.flush();
        FilterConfiguratorScreen.prepareNestedOpen(this);
        PacketDistributor.sendToServer(new C2SOpenNodeFilterPayload(
            menu.getPos(), menu.getFace(), menu.isInputSideVisible()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double scrollX, double scrollY) {
        if (resourceDialog != null) return true;
        if (groupPanel.hasDialog()) {
            groupPanel.scrollDialog(scrollY);
            return true;
        }
        if (screenWidgets.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        mouseX = toVirtual(mouseX) - horizontalOffset() - windowOffsetX;
        mouseY = toVirtual(mouseY) - windowOffsetY;
        if (tabs.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        if (blueprintVisible) return blueprintPage.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (nodeConfigurationPanel.channelDropdownScrolled(scrollY)) return true;
        if (groupPanel.mouseScrolled(mouseX, mouseY, scrollY, leftPos, topPos)) return true;
        if (groupContents.mouseScrolled(mouseX, mouseY, scrollY,
            groupContentLeft(), groupContentTop(), groupContentWidth(), groupContentHeight())) return true;
        return containerWindow.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (screenWidgetButton == button) {
            screenWidgets.mouseDragged(mouseX, mouseY, button, dragX, dragY);
            return true;
        }
        if (tabPointer) return true;
        if (windowDrag.active(button)) {
            double screenX = toVirtual(mouseX) - horizontalOffset();
            double screenY = toVirtual(mouseY);
            windowOffsetX = windowDrag.boundedX(screenX, windowMinimumX(), windowMaximumX()) - leftPos;
            windowOffsetY = windowDrag.boundedY(screenY, OUTER_FRAME_INSET, windowMaximumY()) - topPos;
            return true;
        }
        mouseX = toVirtual(mouseX) - horizontalOffset() - windowOffsetX;
        mouseY = toVirtual(mouseY) - windowOffsetY;
        if (blueprintVisible)
            return blueprintPage.mouseDragged(mouseX, mouseY, button, dragX / interfaceScale, dragY / interfaceScale);
        if (resourceDialog != null) {
            resourceDialog.setViewportOrigin(-horizontalOffset() - windowOffsetX, -windowOffsetY);
            return resourceDialog.mouseDragged(mouseX, mouseY, button);
        }
        if (groupPanel.hasDialog()) {
            groupPanel.dragDialog(mouseX, mouseY, button, -horizontalOffset() - windowOffsetX, -windowOffsetY, virtualWidth, virtualHeight);
            return true;
        }
        if (nodeConfigurationPanel.isChannelDropdownOpen()) return true;
        dragX /= interfaceScale;
        dragY /= interfaceScale;
        if (groupPanel.mouseDragged(mouseX, mouseY, button, topPos)) return true;
        if (groupContents.mouseDragged(mouseX, mouseY, dragX, dragY)) return true;
        return containerWindow.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (screenWidgetButton == button) {
            screenWidgetButton = -1;
            screenWidgets.mouseReleased(mouseX, mouseY, button);
            return true;
        }
        if (windowDrag.release()) return true;
        if (tabPointer) {
            tabPointer = false;
            return true;
        }
        if (resourceDialog != null) return resourceDialog.mouseReleased();
        if (groupPanel.hasDialog()) {
            groupPanel.releaseDialog();
            return true;
        }
        mouseX = toVirtual(mouseX) - horizontalOffset() - windowOffsetX;
        mouseY = toVirtual(mouseY) - windowOffsetY;
        if (blueprintVisible) return blueprintPage.mouseReleased(mouseX, mouseY, button);
        if (groupPanel.mouseReleased(mouseX, mouseY, button)) return true;
        LogisticsNode before = groupContents.getSelectedNode();
        if (groupContents.mouseReleased()) {
            LogisticsNode node = groupContents.getSelectedNode();
            if (node != null && !Objects.equals(before, node)) openNode(node);
            return true;
        }
        return containerWindow.mouseReleased(mouseX, mouseY, button);
    }

    /// 界面尺寸较大、逻辑窗口放不下完整配置器时，仅缩放本界面，不修改玩家的全局设置
    private void updateResponsiveViewport() {
        interfaceScale = ConfiguratorStyle.interfaceScale(width, height, tabs.maximumWidth() + 12);
        virtualWidth = Math.max(OUTER_WIDTH, (int) Math.floor(width / interfaceScale));
        virtualHeight = Math.max(OUTER_HEIGHT, (int) Math.floor(height / interfaceScale));
    }

    private int toVirtual(int coordinate) {
        return (int) Math.floor(coordinate / interfaceScale);
    }

    private double toVirtual(double coordinate) {
        return coordinate / interfaceScale;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (resourceDialog != null) return resourceDialog.keyPressed(keyCode, scanCode, modifiers);
        if (groupPanel.hasDialog()) return groupPanel.dialogKeyPressed(keyCode, scanCode, modifiers);
        if (screenWidgets.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (tabs.isExpanded() && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            tabs.collapse();
            return true;
        }
        if (blueprintVisible) return blueprintPage.keyPressed(keyCode, scanCode, modifiers);
        if (nodeConfigurationPanel.isChannelDropdownOpen()) return nodeConfigurationPanel.keyPressed(keyCode);
        if (groupPanel.dialogKeyPressed(keyCode, scanCode, modifiers)) return true;
        var editor = focusedEditor();
        if (editor != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                editor.setFocused(false);
                containerWindow.setFocused(null);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (editor == groupPanel.getSearchBox()) groupPanel.triggerSearch();
                else nodeConfigurationPanel.keyPressed(keyCode);
                editor.setFocused(false);
                containerWindow.setFocused(null);
                return true;
            }
            editor.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (nodeConfigurationPanel.keyPressed(keyCode)) return true;
        if (Minecraft.getInstance().options.keyInventory.matches(keyCode, scanCode)
            || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return containerWindow.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (resourceDialog != null || groupPanel.hasDialog()) return true;
        if (screenWidgets.keyReleased(keyCode, scanCode, modifiers)) return true;
        return blueprintVisible ? blueprintPage.keyReleased(keyCode, scanCode, modifiers)
            : containerWindow.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public int getGuiLeft() {
        return screenArea(new Rect2i(leftPos, topPos, imageWidth, imageHeight)).getX();
    }

    @Override
    public int getGuiTop() {
        return screenArea(new Rect2i(leftPos, topPos, imageWidth, imageHeight)).getY();
    }

    @Override
    public int getXSize() {
        return screenArea(new Rect2i(leftPos, topPos, imageWidth, imageHeight)).getWidth();
    }

    @Override
    public int getYSize() {
        return screenArea(new Rect2i(leftPos, topPos, imageWidth, imageHeight)).getHeight();
    }

    private EditBox focusedEditor() {
        var nodeEditor = nodeConfigurationPanel.focusedEditor();
        if (nodeEditor != null) return nodeEditor;
        for (var editor : List.of(groupContents.getSearchBox(), groupPanel.getSearchBox())) {
            if (editor.canConsumeInput()) return editor;
        }
        return null;
    }


    private GroupRef selectedGroup() {
        ItemStack stack = toolStack();
        var key = stack.get(SLDataComponents.SELECTED_GROUP_KEY.get());
        return key == null ? null : ClientLinkData.INSTANCE.findGroupRef(key);
    }

    private void openResourceTypes(boolean values) {
        if (!menu.hasTarget()) return;
        ConnectionKey monitoredConnection = values ? activeNodeConnection() : null;
        if (values && monitoredConnection == null) return;
        boolean readOnly = values || !menu.showsOutputSettings();
        LogisticsNode target = menu.getTargetNode();
        EndpointTopology topology = ClientLinkData.INSTANCE.getTopology(target);
        List<ResourceLocation> ids = !menu.showsOutputSettings()
            ? topology == null ? List.of() : topology.presentTypeIds()
            : editSession.preview(new ChannelConfigurationEdit.TypePatchEdit(List.of(), List.of())).apply(menu.getSelectedTypeIds());
        Component context = Component.translatable("gui.staticlogistics.node.resource_context", compactNode(target),
            Component.translatable(!menu.showsOutputSettings() ? "gui.staticlogistics.input" : "gui.staticlogistics.output"));
        if (!values && groupContents.hasMultipleConnections())
            context = Component.translatable("gui.staticlogistics.resources.batch_context",
                groupContents.configurationTargets(target).size());
        resourceDialog = new ResourceTypeDialog(context, ids, !values && groupContents.hasMultipleConnections(), readOnly, patch -> {
            if (!menu.hasTarget() || !target.equals(menu.getTargetNode())) return;
            if (patch.added().isEmpty() && patch.removed().isEmpty()) return;
            submitConfiguration(patch);
        }, () -> {
            resourceDialog = null;
            if (values)
                PacketDistributor.sendToServer(new C2SObserveTransferRatesPayload(menu.containerId, target, monitoredConnection, false));
        });
        if (values) {
            menu.acceptTransferRates(Map.of());
            menu.clearPeerRates();
            menu.acceptTransferDiagnostic("loading");
            PacketDistributor.sendToServer(new C2SObserveTransferRatesPayload(menu.containerId, target, monitoredConnection, true));
            resourceDialog.showTransferValues(menu::getStackMultiplier, menu::getSpeedMultiplier, !menu.showsOutputSettings());
            resourceDialog.showLiveRates(menu::getTransferRate);
            resourceDialog.showDiagnostic(menu::getTransferDiagnostic);
            resourceDialog.showConnectionRates(menu::getPeerRates, menu::arePeerRatesTruncated, menu::arePeerRatesReady, menu::getUnavailablePeers, peer -> {
                resourceDialog = null;
                PacketDistributor.sendToServer(new C2SObserveTransferRatesPayload(menu.containerId, target, monitoredConnection, false));
                focusChannel(new ConnectionKey(monitoredConnection.groupKey(), target, peer), peer);
            });
        }
        resourceDialog.layout(virtualWidth, virtualHeight);
        containerWindow.setFocused(null);
    }

    private Map<LogisticsNode, GroupKey> configurationTargets(LogisticsNode focus) {
        Map<LogisticsNode, GroupKey> targets = groupContents.configurationGroups(focus);
        targets.put(focus, menu.getRemoteGroupKey());
        return targets;
    }

    private void updateBatchSelection() {
        int count = menu.hasTarget() ? groupContents.configurationTargets(menu.getTargetNode()).size() : 0;
        menu.setClientBatchSelection(groupContents.hasMultipleConnections());
        if (nodeConfigurationPanel != null)
            nodeConfigurationPanel.setBatchSelection(groupContents.hasMultipleConnections(), count);
    }

    private void discardBatch() {
        editSession.clearBatchDraft();
        batchTargets = Map.of();
    }

    private void applyBatch() {
        nodeConfigurationPanel.unfocusEditors(-1, -1);
        if (!editSession.canEdit() || !editSession.hasBatchDraft() || !menu.hasTarget() || nodeConfigurationPanel.hasInputError())
            return;
        if (!batchTargets.equals(configurationTargets(menu.getTargetNode()))) {
            discardBatch();
            return;
        }
        PacketDistributor.sendToServer(new C2SConfigureFacePayload(menu.getPos(), menu.getFace(),
            menu.getTargetNode().channel(), editSession.batchEdits(), menu.containerId, beginRequest(), batchTargets));
    }

    private void savePreset() {
        nodeConfigurationPanel.unfocusEditors(-1, -1);
        if (!editSession.canEdit() || !menu.hasTarget() || nodeConfigurationPanel.hasInputError() || editSession.hasBatchDraft())
            return;
        Long priority = editSession.pendingValue(menu.getTargetNode(), ChannelConfigurationEdit.NumberField.PRIORITY);
        Long stock = editSession.pendingValue(menu.getTargetNode(), ChannelConfigurationEdit.NumberField.KEEP_STOCK);
        LogisticsNode target = menu.getTargetNode();
        long savedPriority = priority == null ? menu.getPriority() : priority;
        long savedStock = stock == null ? menu.getKeepStock() : stock;
        groupPanel.openPresetName(name -> savePresetNamed(name, target, savedPriority, savedStock));
    }

    private void savePresetNamed(String name, LogisticsNode target, long priority, long stock) {
        if (!menu.hasTarget() || !target.equals(menu.getTargetNode()) || !editSession.canEdit()) return;
        try {
            ConfigurationPreset.save(name, menu, priority, stock);
            minecraft.player.displayClientMessage(Component.translatable("gui.staticlogistics.preset.saved"), true);
        } catch (FileAlreadyExistsException e) {
            minecraft.player.displayClientMessage(Component.translatable("gui.staticlogistics.preset.duplicate"), true);
        } catch (IOException | RuntimeException e) {
            minecraft.player.displayClientMessage(Component.translatable("gui.staticlogistics.preset.error"), true);
        }
    }

    private void applyPreset() {
        nodeConfigurationPanel.unfocusEditors(-1, -1);
        if (!editSession.canEdit() || !menu.hasTarget() || nodeConfigurationPanel.hasInputError() || editSession.hasBatchDraft())
            return;
        LogisticsNode target = menu.getTargetNode();
        try {
            groupPanel.openPresetSelection(ConfigurationPreset.names(), name -> applyPresetNamed(name, target), this::deletePreset);
        } catch (IOException | RuntimeException e) {
            minecraft.player.displayClientMessage(Component.translatable("gui.staticlogistics.preset.error"), true);
        }
    }

    private void deletePreset(String name) {
        try {
            ConfigurationPreset.delete(name);
            minecraft.player.displayClientMessage(Component.translatable("gui.staticlogistics.preset.deleted"), true);
        } catch (IOException | RuntimeException e) {
            minecraft.player.displayClientMessage(Component.translatable("gui.staticlogistics.preset.error"), true);
        }
    }

    private void applyPresetNamed(String name, LogisticsNode target) {
        if (!menu.hasTarget() || !target.equals(menu.getTargetNode()) || !editSession.canEdit() || editSession.hasBatchDraft())
            return;
        try {
            var edits = ConfigurationPreset.load(name, menu);
            if (edits.stream().anyMatch(edit -> !menu.allowsEdit(edit)))
                throw new IllegalArgumentException("Unsupported preset");
            var targets = Map.copyOf(configurationTargets(target));
            if (targets.size() > 1) {
                selectPresetInspection(target, edits, targets);
                return;
            }
            var differences = edits.stream().map(this::presetDifference).toList();
            groupPanel.openPresetReview(differences, targets.size(), selected -> {
                if (!menu.hasTarget() || !target.equals(menu.getTargetNode()) || !editSession.canEdit()
                    || editSession.hasBatchDraft() || !targets.equals(configurationTargets(target))
                    || !differences.equals(edits.stream().map(this::presetDifference).toList())) {
                    minecraft.player.displayClientMessage(Component.translatable("gui.staticlogistics.preset.changed"), true);
                    return;
                }
                var chosen = IntStream.range(0, edits.size()).filter(selected::contains).mapToObj(edits::get).toList();
                if (!chosen.isEmpty() && chosen.stream().allMatch(menu::allowsEdit))
                    PacketDistributor.sendToServer(new C2SConfigureFacePayload(menu.getPos(), menu.getFace(),
                        target.channel(), chosen, menu.containerId, beginRequest(), targets));
            });
        } catch (IOException | RuntimeException e) {
            minecraft.player.displayClientMessage(Component.translatable("gui.staticlogistics.preset.error"), true);
        }
    }

    private void selectPresetInspection(LogisticsNode target, List<ChannelConfigurationEdit> edits,
                                        Map<LogisticsNode, GroupKey> targets) {
        var nodes = targets.keySet().stream().sorted(ConnectionKey.NODE_ORDER).toList();
        groupPanel.openPresetTargetSelection(nodes, inspected -> {
            if (!menu.hasTarget() || !target.equals(menu.getTargetNode())
                || !targets.equals(configurationTargets(target)) || !editSession.canEdit()) return;
            long requestId = ++presetPreviewSequence;
            pendingPresetPreview = new PendingPresetPreview(requestId, target, inspected, edits, targets);
            PacketDistributor.sendToServer(new C2SPresetPreviewPayload(
                menu.containerId, requestId, inspected, targets.get(inspected), edits));
        });
    }

    public void acceptPresetPreview(long requestId, LogisticsNode node,
                                    PresetPreviewService.Snapshot snapshot) {
        var pending = pendingPresetPreview;
        if (pending == null || pending.requestId() != requestId || !pending.inspected().equals(node)) return;
        pendingPresetPreview = null;
        if (snapshot == null || !menu.hasTarget() || !pending.focus().equals(menu.getTargetNode())
            || !pending.targets().equals(configurationTargets(pending.focus())) || !editSession.canEdit()
            || snapshot.fields().size() != pending.edits().size()) {
            minecraft.player.displayClientMessage(Component.translatable("gui.staticlogistics.preset.preview_unavailable"), true);
            return;
        }
        var labels = new ArrayList<String>();
        for (var field : snapshot.fields())
            labels.add(presetDifferenceForSnapshot(pending.edits().get(field.editIndex()), field.value(), field.available()));
        groupPanel.openPresetReview(labels, pending.targets().size(), selected -> {
            if (!menu.hasTarget() || !pending.focus().equals(menu.getTargetNode()) || !editSession.canEdit()
                || editSession.hasBatchDraft() || !pending.targets().equals(configurationTargets(pending.focus()))) {
                minecraft.player.displayClientMessage(Component.translatable("gui.staticlogistics.preset.changed"), true);
                return;
            }
            var chosen = IntStream.range(0, pending.edits().size()).filter(selected::contains)
                .mapToObj(pending.edits()::get).toList();
            if (!chosen.isEmpty() && chosen.stream().allMatch(menu::allowsEdit))
                PacketDistributor.sendToServer(new C2SConfigureFacePayload(menu.getPos(), menu.getFace(),
                    pending.focus().channel(), chosen, menu.containerId, beginRequest(), pending.targets(),
                    snapshot.node(), snapshot.version()));
        }, () -> selectPresetInspection(pending.focus(), pending.edits(), pending.targets()));
    }

    private String presetDifferenceForSnapshot(ChannelConfigurationEdit desired, ChannelConfigurationEdit current, boolean available) {
        String before;
        if (!available) before = Component.translatable("gui.staticlogistics.preset.filter_missing").getString();
        else if (current instanceof ChannelConfigurationEdit.EnabledEdit value) before = presetBoolean(value.enabled());
        else if (current instanceof ChannelConfigurationEdit.RedstoneEdit value)
            before = Component.translatable("gui.staticlogistics.compact.redstone_" + value.mode().name().toLowerCase(Locale.ROOT)).getString();
        else if (current instanceof ChannelConfigurationEdit.NumberEdit value) before = Long.toString(value.value());
        else if (current instanceof ChannelConfigurationEdit.StrategyEdit value)
            before = value.strategy().getDisplayName().getString();
        else if (current instanceof ChannelConfigurationEdit.ExtractionEdit value)
            before = value.mode().getDisplayName().getString();
        else if (current instanceof ChannelConfigurationEdit.SelectedTypesEdit value)
            before = presetTypes(value.typeIds());
        else if (current instanceof ChannelConfigurationEdit.FilterEdit value) before = presetFilter(value.filter());
        else throw new IllegalArgumentException("Unsupported preset snapshot");
        String focusDifference = presetDifference(desired);
        int separator = focusDifference.indexOf(": ");
        if (separator < 0) throw new IllegalArgumentException("Invalid preset difference");
        String after;
        if (desired instanceof ChannelConfigurationEdit.EnabledEdit value) after = presetBoolean(value.enabled());
        else if (desired instanceof ChannelConfigurationEdit.RedstoneEdit value)
            after = Component.translatable("gui.staticlogistics.compact.redstone_" + value.mode().name().toLowerCase(Locale.ROOT)).getString();
        else if (desired instanceof ChannelConfigurationEdit.NumberEdit value) after = Long.toString(value.value());
        else if (desired instanceof ChannelConfigurationEdit.StrategyEdit value)
            after = value.strategy().getDisplayName().getString();
        else if (desired instanceof ChannelConfigurationEdit.ExtractionEdit value)
            after = value.mode().getDisplayName().getString();
        else if (desired instanceof ChannelConfigurationEdit.SelectedTypesEdit value)
            after = presetTypes(value.typeIds());
        else if (desired instanceof ChannelConfigurationEdit.FilterEdit value) after = presetFilter(value.filter());
        else throw new IllegalArgumentException("Unsupported preset");
        return focusDifference.substring(0, separator + 2) + before + " → " + after;
    }

    private String presetDifference(ChannelConfigurationEdit edit) {
        String label, before, after;
        if (edit instanceof ChannelConfigurationEdit.EnabledEdit value) {
            label = Component.translatable("gui.staticlogistics.preset.channel_state").getString();
            before = presetBoolean(menu.isChannelEnabled());
            after = presetBoolean(value.enabled());
        } else if (edit instanceof ChannelConfigurationEdit.RedstoneEdit value) {
            label = Component.translatable("gui.staticlogistics.compact.redstone").getString();
            before = Component.translatable("gui.staticlogistics.compact.redstone_" + menu.getRedstoneMode().name().toLowerCase(Locale.ROOT)).getString();
            after = Component.translatable("gui.staticlogistics.compact.redstone_" + value.mode().name().toLowerCase(Locale.ROOT)).getString();
        } else if (edit instanceof ChannelConfigurationEdit.NumberEdit value) {
            boolean priority = value.field() == ChannelConfigurationEdit.NumberField.PRIORITY;
            label = Component.translatable(priority ? "gui.staticlogistics.label.target_priority" : "gui.staticlogistics.label.keep_stock").getString();
            before = Long.toString(priority ? menu.getPriority() : menu.getKeepStock());
            after = Long.toString(value.value());
        } else if (edit instanceof ChannelConfigurationEdit.StrategyEdit value) {
            label = Component.translatable("gui.staticlogistics.compact.distribution").getString();
            before = menu.getStrategy().getDisplayName().getString();
            after = value.strategy().getDisplayName().getString();
        } else if (edit instanceof ChannelConfigurationEdit.ExtractionEdit value) {
            label = Component.translatable("gui.staticlogistics.compact.extraction").getString();
            before = menu.getExtractionMode().getDisplayName().getString();
            after = value.mode().getDisplayName().getString();
        } else if (edit instanceof ChannelConfigurationEdit.SelectedTypesEdit value) {
            label = Component.translatable("gui.staticlogistics.reference.resources").getString();
            before = presetTypes(menu.getSelectedTypeIds());
            after = presetTypes(value.typeIds());
        } else if (edit instanceof ChannelConfigurationEdit.FilterEdit value) {
            label = Component.translatable("gui.staticlogistics.filter").getString();
            var stack = menu.getSlot(0).getItem();
            before = stack.getItem() instanceof LogisticsUpgrade upgrade && upgrade.isFilterUpgrade()
                ? presetFilter(stack.getOrDefault(SLDataComponents.FILTER_DATA.get(), FilterData.EMPTY))
                : Component.translatable("gui.staticlogistics.preset.filter_missing").getString();
            after = presetFilter(value.filter());
        } else throw new IllegalArgumentException("Unsupported preset field");
        return label + ": " + before + " → " + after;
    }

    private String presetBoolean(boolean enabled) {
        return Component.translatable(enabled ? "gui.staticlogistics.reference.enable" : "gui.staticlogistics.reference.disable").getString();
    }

    private String presetFilter(FilterData data) {
        var labels = new TreeMap<String, String>();
        data.items().forEach((slot, stack) -> labels.put(slot, stack.getHoverName().getString()));
        data.fluids().forEach((slot, fluid) -> labels.put(slot, BuiltInRegistries.FLUID.getKey(fluid).toString()));
        if (labels.isEmpty()) return "—";
        return labels.entrySet().stream().map(entry -> {
            String slot = entry.getKey();
            var rules = data.rules().getOrDefault(slot, List.of());
            String details = rules.stream().map(rule -> Component.translatable("gui.staticlogistics.filter.rule." + rule.kind().name().toLowerCase(Locale.ROOT)).getString()
                + " " + Component.translatable("gui.staticlogistics.filter.rule." + rule.mode().name().toLowerCase(Locale.ROOT)).getString()
                + (rule.key().isEmpty() ? "" : " " + rule.key())).collect(Collectors.joining(", "));
            return Component.translatable("gui.staticlogistics.preset.filter_entry", Integer.parseInt(slot) + 1, entry.getValue(),
                Component.translatable(data.blacklist().getOrDefault(slot, false) ? "gui.staticlogistics.preset.block" : "gui.staticlogistics.preset.allow"), details.isEmpty() ? "—" : details).getString();
        }).collect(Collectors.joining("; "));
    }

    private String presetTypes(List<ResourceLocation> ids) {
        if (ids.isEmpty()) return "—";
        return ids.stream().map(id -> {
            var type = TransferRegistries.get(id);
            return type == null ? id.toString() : Component.translatable(type.translationKey()).getString();
        }).collect(Collectors.joining(", "));
    }

    private void submitConfiguration(ChannelConfigurationEdit edit) {
        if (!menu.hasTarget()) return;
        if (groupContents.hasMultipleConnections()) {
            if (!editSession.hasBatchDraft()) batchTargets = Map.copyOf(configurationTargets(menu.getTargetNode()));
            editSession.stageBatch(edit);
            return;
        }
        if (edit instanceof ChannelConfigurationEdit.NumberEdit number) {
            editSession.queueNumber(menu.getTargetNode(), number);
            return;
        }
        PacketDistributor.sendToServer(new C2SConfigureFacePayload(menu.getPos(), menu.getFace(), menu.getTargetNode().channel(), List.of(edit), menu.containerId, beginRequest(), configurationTargets(menu.getTargetNode())));
    }


    @Override
    public boolean charTyped(char character, int modifiers) {
        if (resourceDialog != null) return true;
        if (groupPanel.hasDialog()) return groupPanel.dialogCharTyped(character, modifiers);
        if (screenWidgets.charTyped(character, modifiers)) return true;
        if (tabs.isExpanded()) return true;
        if (blueprintVisible) return blueprintPage.charTyped(character, modifiers);
        if (groupPanel.dialogCharTyped(character, modifiers)) return true;
        if (nodeConfigurationPanel.isChannelDropdownOpen()) return true;
        var editor = focusedEditor();
        if (editor != null) {
            editor.charTyped(character, modifiers);
            return true;
        }
        return containerWindow.charTyped(character, modifiers);
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
        if (!Objects.equals(stack.get(SLDataComponents.SELECTED_GROUP_KEY.get()), groupKey)) {
            SelectedWorldLinks.set(Set.of());
        }
        stack.set(SLDataComponents.SELECTED_GROUP.get(), groupName);
        if (group == null) stack.remove(SLDataComponents.SELECTED_GROUP_KEY.get());
        else stack.set(SLDataComponents.SELECTED_GROUP_KEY.get(), group.key());
        stack.remove(SLDataComponents.SELECTED_CONNECTION_KEY.get());
        PacketDistributor.sendToServer(
            new C2SUpdateToolGroupPayload(groupName, groupKey));
        if (playSound) SoundUtil.playClickSound();
    }

    private void syncFocusedConnection(boolean playSound) {
        ItemStack stack = toolStack();
        stack.remove(SLDataComponents.SELECTED_CONNECTION_KEY.get());
        PacketDistributor.sendToServer(new C2SUpdateToolConnectionPayload(null));
        if (playSound) SoundUtil.playClickSound();
    }


    private ItemStack toolStack() {
        return menu.getToolStack();
    }

    private int groupContentLeft() {
        return leftPos + SLGuiTextures.LinkConfigurator.GROUP_CONTENT_X;
    }

    private int groupContentTop() {
        return topPos + SLGuiTextures.LinkConfigurator.GROUP_CONTENT_Y;
    }

    private int groupContentWidth() {
        return SLGuiTextures.LinkConfigurator.GROUP_CONTENT_WIDTH - GROUP_CONTENT_RIGHT_INSET;
    }

    private int groupContentHeight() {
        return SLGuiTextures.LinkConfigurator.GROUP_CONTENT_HEIGHT - GROUP_CONTENT_BOTTOM_INSET;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void renderCustomContent(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    /// 新主界面的区域标题均自行定位；禁止继承旧底板的“物品栏”标签坐标
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

}
