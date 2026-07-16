package com.coobird.staticlogistics.client.gui.component;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.content.SLKeyNames;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.client.render.SLGuiTextures;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.util.LogisticsConstants;
import com.coobird.staticlogistics.logistics.util.NodeDisplayText;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 右侧组面板：搜索框 + 分组列表 + 滚动条 + 行内重命名。
 */
public class GroupPanel {

    public static final int SIDE_PANEL_X = SLGuiTextures.Background.WIDTH;
    public static final int BAR_W = 39, BAR_X = 11, BAR_Y = 13;
    public static final int LIST_OFFSET_X = 10, LIST_OFFSET_Y = 32;
    public static final int SCROLLBAR_X = 87, SCROLLBAR_Y = 25;
    public static final int SELECTION_WIDTH = 75;

    private final EditBox searchBox;
    private final EditBox renameBox;

    private float scrollOffset;
    private boolean isScrolling;
    private long lastClickTime;
    private GroupKey lastClickedGroup;
    private GroupRef editingGroup;
    private String confirmedSearchTerm = "";
    private GroupRef hoveredGroup;

    private int lastSeenVersion = -1;
    private List<GroupRef> cachedGroupList = Collections.emptyList();
    private final Map<UUID, ItemStack> headCache = new HashMap<>();

    public GroupPanel(Font font, int leftPos, int topPos) {
        int sx = leftPos + SIDE_PANEL_X;
        this.searchBox = new EditBox(font, sx + BAR_X, topPos + BAR_Y, BAR_W - 2, 8, Component.empty());
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(20);
        this.searchBox.setTextColor(0xFFFFFF);
        this.searchBox.setHint(Component.translatable("gui.staticlogistics.search_hint").withStyle(ChatFormatting.DARK_GRAY));

        this.renameBox = new EditBox(font, 0, 0, SELECTION_WIDTH - 4, 10, Component.empty());
        this.renameBox.setBordered(false);
        this.renameBox.setVisible(false);
        this.renameBox.setTextColor(0xFFFFCC);
    }

    public EditBox getSearchBox() {
        return searchBox;
    }

    public EditBox getRenameBox() {
        return renameBox;
    }

    public String getHoveredGroupId() {
        return hoveredGroup == null ? "" : hoveredGroup.displayName();
    }

    @Nullable
    public GroupRef getHoveredGroup() {
        return hoveredGroup;
    }

    public String getEditingGroupId() {
        return editingGroup == null ? "" : editingGroup.displayName();
    }

    @Nullable
    public GroupRef getEditingGroup() {
        return editingGroup;
    }

    public boolean isScrolling() {
        return isScrolling;
    }

    public void setInitialState(ItemStack stack) {
        this.lastClickedGroup = stack.get(SLDataComponents.SELECTED_GROUP_KEY.get());
        this.lastSeenVersion = -1;
    }

    public void render(GuiGraphics g, Font font, ItemStack stack,
                       int leftPos, int topPos, int mx, int my, float partialTick) {
        this.hoveredGroup = null;
        int sx = leftPos + SIDE_PANEL_X;

        g.blit(SLGuiTextures.GUI_ATLAS, sx, topPos, 0, 144,
            SLGuiTextures.Background.BY_GROUP_WIDTH,
            SLGuiTextures.Background.BY_GROUP_HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

        this.searchBox.setX(sx + BAR_X + 1);
        this.searchBox.setY(topPos + BAR_Y + 1);
        this.searchBox.render(g, mx, my, partialTick);

        renderGroupList(g, font, stack, sx, topPos, mx, my);

        if (this.renameBox.isVisible()) {
            this.renameBox.render(g, mx, my, partialTick);
        }
    }

    private void renderGroupList(GuiGraphics g, Font font, ItemStack stack,
                                 int sx, int topPos, int mx, int my) {
        List<GroupRef> groups = getFilteredGroups(stack);
        int maxScroll = getMaxScroll();
        renderScrollBar(g, sx + SCROLLBAR_X, topPos + SCROLLBAR_Y, mx, my, maxScroll);

        int listX = sx + LIST_OFFSET_X;
        int listY = topPos + LIST_OFFSET_Y;
        g.enableScissor(listX - 2, listY, listX + SELECTION_WIDTH + 2,
            listY + SLGuiTextures.List.HEIGHT);

        String currentGroupId = stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "");
        GroupKey currentGroupKey = stack.get(SLDataComponents.SELECTED_GROUP_KEY.get());

        for (int i = 0; i < groups.size(); i++) {
            GroupRef group = groups.get(i);
            String gn = group.displayName();
            int itemY = listY + (i * SLGuiTextures.List.ITEM_H) - (int) scrollOffset;
            if (itemY + SLGuiTextures.List.ITEM_H < listY
                || itemY > listY + SLGuiTextures.List.HEIGHT) continue;

            boolean isSelected = currentGroupKey != null
                ? currentGroupKey.equals(group.key()) : Objects.equals(currentGroupId, gn);
            boolean isHovered = mx >= sx + 8 && mx <= sx + 8 + SELECTION_WIDTH
                && my >= itemY && my < itemY + SLGuiTextures.List.ITEM_H;
            if (isHovered) this.hoveredGroup = group;

            if (isSelected) {
                g.fill(sx + 8, itemY, sx + 8 + SELECTION_WIDTH,
                    itemY + SLGuiTextures.List.ITEM_H, 0x4498FB98);
            } else if (isHovered) {
                g.fill(sx + 8, itemY, sx + 8 + SELECTION_WIDTH,
                    itemY + SLGuiTextures.List.ITEM_H, 0x22FFFFFF);
            }

            if (editingGroup != null && editingGroup.key().equals(group.key())) {
                renameBox.setX(sx + 8);
                renameBox.setY(itemY + 1);
                renameBox.setVisible(true);
            } else {
                String display = "#" + gn;
                int color = isSelected ? 0x98FB98 : 0xCCCCCC;
                int textX = sx + 8;

                // 渲染所有者头像
                UUID ownerUUID = group.key().ownerId();
                if (ownerUUID != null) {
                    ItemStack headStack = headCache.computeIfAbsent(ownerUUID, uid -> {
                        String ownerName = ClientLinkData.INSTANCE.getOwnerName(uid);
                        GameProfile profile = new GameProfile(uid, ownerName);
                        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
                        head.set(DataComponents.PROFILE, new ResolvableProfile(profile));
                        return head;
                    });
                    int headSize = 10;
                    g.pose().pushPose();
                    g.pose().translate(textX, itemY + 1, 0);
                    g.pose().scale(headSize / 16f, headSize / 16f, 1f);
                    g.renderFakeItem(headStack, 0, 0);
                    g.pose().popPose();
                    textX += headSize + 3;
                    if (mx >= sx + 8 && mx < sx + 8 + headSize
                        && my >= itemY + 1 && my < itemY + 1 + headSize) {
                        this.hoveredGroup = group;
                    }
                }
                g.drawString(font, display, textX, itemY + 2, color, false);
            }
        }
        g.disableScissor();
    }

    private void renderScrollBar(GuiGraphics g, int x, int y, int mx, int my, int maxScroll) {
        boolean showActive = maxScroll > 0
            && ((mx >= x && mx <= x + SLGuiTextures.Scrollbar.WIDTH
            && my >= y && my <= y + SLGuiTextures.Scrollbar.TRACK_HEIGHT) || this.isScrolling);
        int knobY = maxScroll > 0
            ? (int) (scrollOffset / maxScroll * (SLGuiTextures.Scrollbar.TRACK_HEIGHT
            - SLGuiTextures.Scrollbar.HEIGHT))
            : 0;
        g.blit(SLGuiTextures.GUI_ATLAS, x, y + knobY,
            showActive ? SLGuiTextures.Scrollbar.ENABLED_U : SLGuiTextures.Scrollbar.DISABLED_U,
            SLGuiTextures.Scrollbar.ENABLED_V,
            SLGuiTextures.Scrollbar.WIDTH, SLGuiTextures.Scrollbar.HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
    }

    public boolean isSearchTriggerHit(double mx, double my, int leftPos, int topPos) {
        int sx = leftPos + SIDE_PANEL_X;
        return mx >= sx + 52 && mx <= sx + 52 + SLGuiTextures.ZOOM_WIDTH
            && my >= topPos + 13 && my <= topPos + 13 + SLGuiTextures.ZOOM_HEIGHT;
    }

    public void triggerSearch() {
        this.confirmedSearchTerm = this.searchBox.getValue().trim();
        this.scrollOffset = 0;
        this.lastSeenVersion = -1;
        playClickSound();
    }

    public boolean searchBoxMouseClicked(double mx, double my, int button) {
        return this.searchBox.mouseClicked(mx, my, button);
    }

    public boolean isSearchBoxFocused() {
        return this.searchBox.isFocused();
    }

    public boolean renameBoxMouseClicked(double mx, double my, int button) {
        if (this.renameBox.isVisible()) {
            return this.renameBox.mouseClicked(mx, my, button);
        }
        return false;
    }

    public boolean isRenameBoxVisible() {
        return this.renameBox.isVisible();
    }

    public void startRename(GroupRef group, int leftPos, int topPos) {
        int sx = leftPos + SIDE_PANEL_X;
        this.renameBox.setX(sx + 8);
        this.renameBox.setY(topPos + 25);
        this.editingGroup = group;
        this.renameBox.setValue(group.displayName());
        this.renameBox.setVisible(true);
        this.renameBox.setFocused(true);
    }

    /**
     * 将 renameBox 定位到指定绝对坐标
     */
    public String confirmRename() {
        String newId = renameBox.getValue().trim();
        cancelRename();
        return newId.isEmpty() ? null : newId;
    }

    public void cancelRename() {
        this.editingGroup = null;
        this.renameBox.setVisible(false);
    }

    /**
     * 处理分组列表区域的鼠标点击。
     *
     * @return 点击结果：null=未命中，ClickResult=命中
     */
    @Nullable
    public ClickResult handleListClick(double mx, double my, int button, int leftPos, int topPos,
                                       ItemStack stack, boolean shiftDown) {
        int sx = leftPos + SIDE_PANEL_X;
        List<GroupRef> groups = getFilteredGroups(stack);
        int listY = topPos + LIST_OFFSET_Y;
        if (!(mx >= sx + 8 && mx <= sx + 8 + SELECTION_WIDTH
            && my >= listY && my < listY + SLGuiTextures.List.HEIGHT))
            return null;

        for (int i = 0; i < groups.size(); i++) {
            int itemY = listY + (i * SLGuiTextures.List.ITEM_H) - (int) scrollOffset;
            if (my >= itemY && my < itemY + SLGuiTextures.List.ITEM_H) {
                GroupRef group = groups.get(i);
                if (button == 1) {
                    return ClickResult.delete(group);
                }
                if (shiftDown) {
                    return ClickResult.export(group);
                }
                long now = Util.getMillis();
                boolean isDoubleClick = Objects.equals(lastClickedGroup, group.key())
                    && now - lastClickTime < LogisticsConstants.UI.DOUBLE_CLICK_THRESHOLD_MS;
                lastClickedGroup = group.key();
                lastClickTime = now;
                return isDoubleClick ? ClickResult.rename(group) : ClickResult.select(group);
            }
        }
        return null;
    }

    public boolean handleScrollbarClick(double mx, double my, int leftPos, int topPos) {
        int sx = leftPos + SIDE_PANEL_X;
        int scrollX = sx + SCROLLBAR_X;
        int scrollY = topPos + SCROLLBAR_Y;
        if (mx >= scrollX && mx <= scrollX + SLGuiTextures.Scrollbar.WIDTH
            && my >= scrollY && my <= scrollY + SLGuiTextures.Scrollbar.TRACK_HEIGHT) {
            if (getMaxScroll() > 0) {
                this.isScrolling = true;
                updateScrollFromMouse(my, topPos);
            }
            return true;
        }
        return false;
    }

    public void updateScrollFromMouse(double mouseY, int topPos) {
        int maxScroll = getMaxScroll();
        if (maxScroll > 0) {
            float relativePos = (float) ((mouseY - (topPos + SCROLLBAR_Y))
                / SLGuiTextures.Scrollbar.TRACK_HEIGHT);
            this.scrollOffset = Mth.clamp(relativePos * maxScroll, 0, maxScroll);
        }
    }

    public boolean mouseScrolled(double mx, double my, double dy, int leftPos, int topPos) {
        int maxScroll = getMaxScroll();
        if (maxScroll > 0) {
            this.scrollOffset = Mth.clamp(
                this.scrollOffset - (float) dy * SLGuiTextures.List.ITEM_H, 0, maxScroll);
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double my, int topPos) {
        if (this.isScrolling) {
            updateScrollFromMouse(my, topPos);
            return true;
        }
        return false;
    }

    public void mouseReleased() {
        this.isScrolling = false;
    }

    private int getMaxScroll() {
        return Math.max(0,
            cachedGroupList.size() * SLGuiTextures.List.ITEM_H - SLGuiTextures.List.HEIGHT);
    }

    private List<GroupRef> getFilteredGroups(ItemStack stack) {
        int version = ClientLinkData.INSTANCE.getDataVersion();
        if (version == lastSeenVersion) return cachedGroupList;

        Player p = Minecraft.getInstance().player;
        if (p == null) return Collections.emptyList();

        String filter = this.confirmedSearchTerm.toLowerCase();
        this.cachedGroupList = ClientLinkData.INSTANCE.getAccessibleGroupRefs().stream()
            .filter(group -> !group.displayName().isEmpty())
            .filter(group -> group.displayName().toLowerCase().contains(filter))
            .sorted((a, b) -> {
                String aName = a.displayName(), bName = b.displayName();
                boolean aNum = aName.matches("\\d+"), bNum = bName.matches("\\d+");
                if (aNum && bNum) {
                    try {
                        int byNumber = Integer.compare(Integer.parseInt(aName), Integer.parseInt(bName));
                        return byNumber != 0 ? byNumber : a.key().ownerId().compareTo(b.key().ownerId());
                    } catch (NumberFormatException e) {
                        return aName.compareToIgnoreCase(bName);
                    }
                }
                int byName = aNum ? -1 : (bNum ? 1 : aName.compareToIgnoreCase(bName));
                return byName != 0 ? byName : a.key().ownerId().compareTo(b.key().ownerId());
            }).collect(Collectors.toList());

        this.lastSeenVersion = version;
        return cachedGroupList;
    }

    public void renderGroupTooltip(GuiGraphics g, Font font, int mx, int my, GroupRef group, boolean shiftDown) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        String gid = group.displayName();
        List<LogisticsNode> nodes = ClientLinkData.INSTANCE.getNodesForGroup(group.key());

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.staticlogistics.tooltip.group_id", gid)
            .withStyle(ChatFormatting.GOLD));
        String ownerName = ClientLinkData.INSTANCE.getOwnerName(group.key().ownerId());
        if (!ownerName.isEmpty()) {
            lines.add(Component.translatable("msg.staticlogistics.owner_display", ownerName)
                .withStyle(ChatFormatting.GRAY));
        }

        int maxShown = shiftDown ? Integer.MAX_VALUE : 5;
        if (!nodes.isEmpty()) {
            int count = 0;
            for (LogisticsNode node : nodes) {
                if (count >= maxShown) break;
                lines.add(formatNode(node, player));
                count++;
            }
            if (nodes.size() > maxShown) {
                lines.add(Component.translatable("gui.staticlogistics.tooltip.shift_more",
                    Component.keybind(SLKeyNames.GROUP_DETAILS_AND_EXPORT),
                    nodes.size() - maxShown).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
            lines.add(Component.empty());
        }

        if (editingGroup == null) {
            lines.add(Component.translatable("gui.staticlogistics.tooltip.select_hint")
                .withStyle(ChatFormatting.BLUE));
            lines.add(Component.translatable("gui.staticlogistics.tooltip.right_click_delete")
                .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
            lines.add(Component.translatable("gui.staticlogistics.tooltip.rename_hint")
                .withStyle(ChatFormatting.AQUA));
        }
        lines.add(Component.translatable("gui.staticlogistics.tooltip.shift_export",
                Component.keybind(SLKeyNames.GROUP_DETAILS_AND_EXPORT))
            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        g.renderComponentTooltip(font, lines, mx, my);
    }

    public void exportToChat(GroupRef group) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        String gid = group.displayName();
        List<LogisticsNode> nodes = ClientLinkData.INSTANCE.getNodesForGroup(group.key());
        player.sendSystemMessage(Component.translatable("msg.staticlogistics.export.header", gid)
            .withStyle(ChatFormatting.GOLD));
        if (nodes.isEmpty()) {
            player.sendSystemMessage(Component.literal(" §7> ").append(
                Component.translatable("msg.staticlogistics.no_nodes_stored")
                    .withStyle(ChatFormatting.RED)));
        } else {
            for (LogisticsNode node : nodes) {
                BlockPos p = node.gPos().pos();
                String posStr = p.getX() + " " + p.getY() + " " + p.getZ();
                String dimensionId = node.gPos().dimension().location().toString();
                String command = node.isInSameDimension(player.level().dimension())
                    ? "/tp " + posStr
                    : "/execute in " + dimensionId + " run tp @s " + posStr;
                MutableComponent posEntry = Component.literal(" > ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(NodeDisplayText.details(node).withStyle(ChatFormatting.GREEN))
                    .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                            command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.translatable("msg.staticlogistics.export.tp_hover")
                                .withStyle(ChatFormatting.ITALIC))));
                player.sendSystemMessage(posEntry);
            }
        }
        Minecraft.getInstance().getSoundManager()
            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private static Component formatNode(LogisticsNode node, Player player) {
        BlockPos pos = node.gPos().pos();
        MutableComponent location = NodeDisplayText.details(node).withStyle(ChatFormatting.WHITE);
        if (!node.isInSameDimension(player.level().dimension())) return location;
        double distance = Math.sqrt(pos.distToCenterSqr(player.position()));
        return location.append(Component.literal("  "))
            .append(NodeDisplayText.distanceFromPlayer(distance).withStyle(ChatFormatting.AQUA));
    }

    private void playClickSound() {
        com.coobird.staticlogistics.client.gui.component.SoundUtil.playClickSound();
    }

    public static class ClickResult {
        public enum Action {SELECT, RENAME, EXPORT, DELETE}

        private final Action action;
        private final GroupRef group;

        private ClickResult(Action action, GroupRef group) {
            this.action = action;
            this.group = group;
        }

        public static ClickResult select(GroupRef group) {
            return new ClickResult(Action.SELECT, group);
        }

        public static ClickResult rename(GroupRef group) {
            return new ClickResult(Action.RENAME, group);
        }

        public static ClickResult export(GroupRef group) {
            return new ClickResult(Action.EXPORT, group);
        }

        public static ClickResult delete(GroupRef group) {
            return new ClickResult(Action.DELETE, group);
        }

        public Action getAction() {
            return action;
        }

        public String getGroupId() {
            return group.displayName();
        }

        public GroupRef getGroup() {
            return group;
        }
    }
}
