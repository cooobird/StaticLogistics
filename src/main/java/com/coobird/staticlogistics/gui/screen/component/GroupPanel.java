package com.coobird.staticlogistics.gui.screen.component;

import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
import com.coobird.staticlogistics.integration.ModCompat;
import com.coobird.staticlogistics.registry.SLDataComponents;
import com.coobird.staticlogistics.util.LogisticsConstants;
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

import java.util.*;
import java.util.stream.Collectors;

/**
 * 右侧组面板：搜索框 + 分组列表 + 滚动条 + 行内重命名。
 */
public class GroupPanel {

    public static final int SIDE_PANEL_X = SLGuiTextures.Background.WIDTH + 2;
    public static final int BAR_W = 39, BAR_X = 11, BAR_Y = 13;
    public static final int LIST_OFFSET_X = 10, LIST_OFFSET_Y = 32;
    public static final int SCROLLBAR_X = 87, SCROLLBAR_Y = 25;
    public static final int SELECTION_WIDTH = 75;

    private final EditBox searchBox;
    private final EditBox renameBox;

    private float scrollOffset;
    private boolean isScrolling;
    private long lastClickTime;
    private String lastClickedGroup = "";
    private String editingGroupId = "";
    private String confirmedSearchTerm = "";
    private String hoveredGroupId = "";

    private int lastSeenVersion = -1;
    private List<String> cachedGroupList = Collections.emptyList();

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
        return hoveredGroupId;
    }

    public String getEditingGroupId() {
        return editingGroupId;
    }

    public boolean isScrolling() {
        return isScrolling;
    }

    public void setInitialState(ItemStack stack) {
        this.lastClickedGroup = stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "");
        this.lastSeenVersion = -1;
    }

    public void render(GuiGraphics g, Font font, ItemStack stack,
                       int leftPos, int topPos, int mx, int my, float partialTick) {
        this.hoveredGroupId = "";
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
        List<String> groups = getFilteredGroups(stack);
        int maxScroll = getMaxScroll();
        renderScrollBar(g, sx + SCROLLBAR_X, topPos + SCROLLBAR_Y, mx, my, maxScroll);

        int listX = sx + LIST_OFFSET_X;
        int listY = topPos + LIST_OFFSET_Y;
        g.enableScissor(listX - 2, listY, listX + SELECTION_WIDTH + 2,
            listY + SLGuiTextures.List.HEIGHT);

        String currentGroupId = stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "");

        for (int i = 0; i < groups.size(); i++) {
            String gn = groups.get(i);
            int itemY = listY + (i * SLGuiTextures.List.ITEM_H) - (int) scrollOffset;
            if (itemY + SLGuiTextures.List.ITEM_H < listY
                || itemY > listY + SLGuiTextures.List.HEIGHT) continue;

            boolean isSelected = Objects.equals(currentGroupId, gn);
            boolean isHovered = mx >= sx + 10 && mx <= sx + 10 + SELECTION_WIDTH
                && my >= itemY && my < itemY + SLGuiTextures.List.ITEM_H;
            if (isHovered) this.hoveredGroupId = gn;

            if (isSelected) {
                g.fill(sx + 10, itemY, sx + 10 + SELECTION_WIDTH,
                    itemY + SLGuiTextures.List.ITEM_H, 0x4498FB98);
            } else if (isHovered) {
                g.fill(sx + 10, itemY, sx + 10 + SELECTION_WIDTH,
                    itemY + SLGuiTextures.List.ITEM_H, 0x22FFFFFF);
            }

            if (Objects.equals(editingGroupId, gn)) {
                renameBox.setX(sx + 12);
                renameBox.setY(itemY + 1);
                renameBox.setVisible(true);
            } else {
                String display = "#" + gn;
                int color = isSelected ? 0x98FB98 : 0xCCCCCC;
                int textX = sx + 12;

                // 渲染所有者头像
                UUID ownerUUID = ClientLinkData.INSTANCE.getOwnerUUIDForGroup(gn);
                if (ownerUUID != null) {
                    String ownerName = ClientLinkData.INSTANCE.getOwnerNameForGroup(gn);
                    GameProfile profile = new GameProfile(ownerUUID, ownerName);
                    ItemStack headStack = new ItemStack(Items.PLAYER_HEAD);
                    headStack.set(DataComponents.PROFILE, new ResolvableProfile(profile));
                    int headSize = 10;
                    g.pose().pushPose();
                    g.pose().translate(textX, itemY + 1, 0);
                    g.pose().scale(headSize / 16f, headSize / 16f, 1f);
                    g.renderFakeItem(headStack, 0, 0);
                    g.pose().popPose();
                    textX += headSize + 3;
                    if (mx >= sx + 12 && mx < sx + 12 + headSize
                        && my >= itemY + 1 && my < itemY + 1 + headSize) {
                        this.hoveredGroupId = gn;
                    }
                }
                g.drawString(font, display, textX, itemY + 2, color, false);
            }
        }
        g.disableScissor();
    }

    private void renderScrollBar(GuiGraphics g, int x, int y, int mx, int my, int maxScroll) {
        boolean showActive = maxScroll > 0
            && ((mx >= x && mx <= x + SLGuiTextures.Scrollbar.ENABLED_WIDTH
            && my >= y && my <= y + SLGuiTextures.Scrollbar.TRACK_HEIGHT) || this.isScrolling);
        int knobY = maxScroll > 0
            ? (int) (scrollOffset / maxScroll * (SLGuiTextures.Scrollbar.TRACK_HEIGHT
            - SLGuiTextures.Scrollbar.ENABLED_HEIGHT))
            : 0;
        g.blit(SLGuiTextures.GUI_ATLAS, x, y + knobY,
            showActive ? SLGuiTextures.Scrollbar.ENABLED_U : SLGuiTextures.Scrollbar.DISABLED_U,
            SLGuiTextures.Scrollbar.ENABLED_V,
            SLGuiTextures.Scrollbar.ENABLED_WIDTH, SLGuiTextures.Scrollbar.ENABLED_HEIGHT,
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

    public void startRename(String groupId, int leftPos, int topPos) {
        int sx = leftPos + SIDE_PANEL_X;
        this.renameBox.setX(sx + 10);
        this.renameBox.setY(topPos + 25);
        this.editingGroupId = groupId;
        this.renameBox.setValue(groupId);
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
        this.editingGroupId = "";
        this.renameBox.setVisible(false);
    }

    /**
     * 处理分组列表区域的鼠标点击。
     *
     * @return 点击结果：null=未命中，ClickResult=命中
     */
    public ClickResult handleListClick(double mx, double my, int button, int leftPos, int topPos,
                                       ItemStack stack, boolean shiftDown) {
        int sx = leftPos + SIDE_PANEL_X;
        List<String> groups = getFilteredGroups(stack);
        int listY = topPos + LIST_OFFSET_Y;
        if (!(mx >= sx + 10 && mx <= sx + 10 + SELECTION_WIDTH
            && my >= listY && my < listY + SLGuiTextures.List.HEIGHT))
            return null;

        for (int i = 0; i < groups.size(); i++) {
            int itemY = listY + (i * SLGuiTextures.List.ITEM_H) - (int) scrollOffset;
            if (my >= itemY && my < itemY + SLGuiTextures.List.ITEM_H) {
                String gn = groups.get(i);
                if (button == 1) {
                    return ClickResult.delete(gn);
                }
                if (shiftDown) {
                    return ClickResult.export(gn);
                }
                long now = Util.getMillis();
                boolean isDoubleClick = Objects.equals(lastClickedGroup, gn)
                    && now - lastClickTime < LogisticsConstants.UI.DOUBLE_CLICK_THRESHOLD_MS;
                lastClickedGroup = gn;
                lastClickTime = now;
                return isDoubleClick ? ClickResult.rename(gn) : ClickResult.select(gn);
            }
        }
        return null;
    }

    public boolean handleScrollbarClick(double mx, double my, int leftPos, int topPos) {
        int sx = leftPos + SIDE_PANEL_X;
        int scrollX = sx + SCROLLBAR_X;
        int scrollY = topPos + SCROLLBAR_Y;
        if (mx >= scrollX && mx <= scrollX + SLGuiTextures.Scrollbar.ENABLED_WIDTH
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

    private List<String> getFilteredGroups(ItemStack stack) {
        int currentVersion = ClientLinkData.INSTANCE.getDataVersion();
        if (currentVersion == lastSeenVersion && !cachedGroupList.isEmpty())
            return cachedGroupList;

        Player p = Minecraft.getInstance().player;
        if (p == null) return Collections.emptyList();

        String filter = this.confirmedSearchTerm.toLowerCase();
        UUID playerUUID = p.getUUID();

        List<UUID> ownerUUIDs = new ArrayList<>();
        ownerUUIDs.add(playerUUID);
        if (ModCompat.isFtbTeamsLoaded()) {
            try {
                var api = dev.ftb.mods.ftbteams.api.FTBTeamsAPI.api();
                if (api != null) {
                    api.getManager().getTeamForPlayerID(playerUUID).ifPresent(team -> {
                        ownerUUIDs.addAll(team.getMembers());
                    });
                }
            } catch (Exception ignored) {
            }
        }

        this.cachedGroupList = ClientLinkData.INSTANCE.getGroupsByOwners(ownerUUIDs).stream()
            .filter(gid -> gid != null && !gid.isEmpty())
            .filter(s -> s.toLowerCase().contains(filter))
            .sorted((a, b) -> {
                boolean aNum = a.matches("\\d+"), bNum = b.matches("\\d+");
                if (aNum && bNum) {
                    try {
                        return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
                    } catch (NumberFormatException e) {
                        return a.compareToIgnoreCase(b);
                    }
                }
                return aNum ? -1 : (bNum ? 1 : a.compareToIgnoreCase(b));
            }).collect(Collectors.toList());

        this.lastSeenVersion = currentVersion;
        return cachedGroupList;
    }

    public void renderGroupTooltip(GuiGraphics g, Font font, int mx, int my, String gid, boolean shiftDown) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        List<BlockPos> positions = ClientLinkData.INSTANCE.getPositionsForGroup(gid)
            .stream().distinct().toList();

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.staticlogistics.tooltip.group_id", gid)
            .withStyle(ChatFormatting.GOLD));
        String ownerName = ClientLinkData.INSTANCE.getOwnerNameForGroup(gid);
        if (!ownerName.isEmpty()) {
            lines.add(Component.translatable("msg.staticlogistics.owner_display", ownerName)
                .withStyle(ChatFormatting.GRAY));
        }

        int maxShown = shiftDown ? Integer.MAX_VALUE : 5;
        if (!positions.isEmpty()) {
            int count = 0;
            for (BlockPos p : positions) {
                if (count >= maxShown) break;
                double dist = Math.sqrt(p.distToCenterSqr(player.position()));
                lines.add(Component.literal(
                    String.format(" §f[%d, %d, %d] §b(%.1fm)",
                        p.getX(), p.getY(), p.getZ(), dist)));
                count++;
            }
            if (positions.size() > maxShown) {
                lines.add(Component.translatable("gui.staticlogistics.tooltip.shift_more",
                    positions.size() - maxShown).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
            lines.add(Component.empty());
        }

        if (editingGroupId.isEmpty()) {
            lines.add(Component.translatable("gui.staticlogistics.tooltip.select_hint")
                .withStyle(ChatFormatting.BLUE));
            lines.add(Component.translatable("gui.staticlogistics.tooltip.right_click_delete")
                .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
            lines.add(Component.translatable("gui.staticlogistics.tooltip.rename_hint")
                .withStyle(ChatFormatting.AQUA));
        }
        lines.add(Component.translatable("gui.staticlogistics.tooltip.shift_export")
            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        g.renderComponentTooltip(font, lines, mx, my);
    }

    public void exportToChat(String gid) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        List<BlockPos> positions = ClientLinkData.INSTANCE.getPositionsForGroup(gid)
            .stream().distinct().toList();
        player.sendSystemMessage(Component.translatable("msg.staticlogistics.export.header", gid)
            .withStyle(ChatFormatting.GOLD));
        if (positions.isEmpty()) {
            player.sendSystemMessage(Component.literal(" §7> ").append(
                Component.translatable("msg.staticlogistics.no_nodes_stored")
                    .withStyle(ChatFormatting.RED)));
        } else {
            for (BlockPos p : positions) {
                String posStr = p.getX() + " " + p.getY() + " " + p.getZ();
                MutableComponent posEntry = Component.literal(" §7> §a[" + posStr + "]")
                    .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                            "/tp " + posStr))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.translatable("msg.staticlogistics.export.tp_hover")
                                .withStyle(ChatFormatting.ITALIC))));
                player.sendSystemMessage(posEntry);
            }
        }
        Minecraft.getInstance().getSoundManager()
            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void playClickSound() {
        com.coobird.staticlogistics.util.SoundUtil.playClickSound();
    }

    public static class ClickResult {
        public enum Action {SELECT, RENAME, EXPORT, DELETE}

        private final Action action;
        private final String groupId;

        private ClickResult(Action action, String groupId) {
            this.action = action;
            this.groupId = groupId;
        }

        public static ClickResult select(String groupId) {
            return new ClickResult(Action.SELECT, groupId);
        }

        public static ClickResult rename(String groupId) {
            return new ClickResult(Action.RENAME, groupId);
        }

        public static ClickResult export(String groupId) {
            return new ClickResult(Action.EXPORT, groupId);
        }

        public static ClickResult delete(String groupId) {
            return new ClickResult(Action.DELETE, groupId);
        }

        public Action getAction() {
            return action;
        }

        public String getGroupId() {
            return groupId;
        }
    }
}
