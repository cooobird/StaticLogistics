package com.coobird.staticlogistics.client.gui.screen;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.client.data.SelectionContext;
import com.coobird.staticlogistics.client.key.SLKeyMappings;
import com.coobird.staticlogistics.client.render.SLGuiTextures;
import com.coobird.staticlogistics.network.c2s.C2SUpdateToolSettingsPayload;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.util.NodeDisplayText;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 蓝图组选择界面 — 仅显示分组列表，不包含配置器模式/类型切换。
 */
public class BlueprintGroupScreen extends Screen {

    private static final int PANEL_W = SLGuiTextures.Background.BY_GROUP_WIDTH;
    private static final int PANEL_H = SLGuiTextures.Background.BY_GROUP_HEIGHT;
    private static final int BAR_W = 52, BAR_X = 11, BAR_Y = 13;
    private static final int LIST_OFFSET_X = 10, LIST_OFFSET_Y = 32;
    private static final int SCROLLBAR_X = 87, SCROLLBAR_Y = 25;
    private static final int SELECTION_WIDTH = 75;

    private final ItemStack stack;
    private int leftPos, topPos;
    private EditBox searchBox;
    private float scrollOffset;
    private boolean isScrolling;
    private String confirmedSearchTerm = "";
    private int lastSeenVersion = -1;
    private List<GroupRef> cachedGroupList = Collections.emptyList();
    private GroupRef hoveredGroup;

    public BlueprintGroupScreen(ItemStack stack) {
        super(Component.translatable("item.staticlogistics.blueprint"));
        this.stack = stack;
    }

    @Override
    protected void init() {
        int titleH = 18;
        this.leftPos = (this.width - PANEL_W) / 2;
        this.topPos = (this.height - PANEL_H - titleH) / 2 + titleH;

        this.searchBox = new EditBox(this.font,
            leftPos + BAR_X, topPos + BAR_Y + 1, BAR_W - 2, 8, Component.empty());
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(20);
        this.searchBox.setTextColor(0xFFFFFF);
        this.searchBox.setHint(Component.translatable("gui.staticlogistics.search_hint")
            .withStyle(ChatFormatting.DARK_GRAY));
        this.addRenderableWidget(this.searchBox);
        this.lastSeenVersion = -1;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        this.hoveredGroup = null;

        g.blit(SLGuiTextures.GUI_ATLAS, leftPos, topPos, 0, 144,
            PANEL_W, PANEL_H, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

        renderGroupList(g, mx, my);
        this.searchBox.render(g, mx, my, pt);

        if (this.hoveredGroup != null) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 500);
            renderGroupTooltip(g, mx, my, this.hoveredGroup,
                SLKeyMappings.isGuiKeyDown(SLKeyMappings.GROUP_DETAILS_AND_EXPORT));
            g.pose().popPose();
        }
    }

    private void renderGroupList(GuiGraphics g, int mx, int my) {
        List<GroupRef> groups = getFilteredGroups();
        int maxScroll = getMaxScroll();
        int listX = leftPos + LIST_OFFSET_X;
        int listY = topPos + LIST_OFFSET_Y;

        // 滚动条
        boolean showActive = maxScroll > 0
            && ((mx >= leftPos + SCROLLBAR_X
            && mx <= leftPos + SCROLLBAR_X + SLGuiTextures.Scrollbar.WIDTH
            && my >= topPos + SCROLLBAR_Y
            && my <= topPos + SCROLLBAR_Y + SLGuiTextures.Scrollbar.TRACK_HEIGHT)
            || this.isScrolling);
        int knobY = maxScroll > 0
            ? (int) (scrollOffset / maxScroll
            * (SLGuiTextures.Scrollbar.TRACK_HEIGHT - SLGuiTextures.Scrollbar.HEIGHT))
            : 0;
        g.blit(SLGuiTextures.GUI_ATLAS, leftPos + SCROLLBAR_X,
            topPos + SCROLLBAR_Y + knobY,
            showActive ? SLGuiTextures.Scrollbar.ENABLED_U
                : SLGuiTextures.Scrollbar.DISABLED_U,
            SLGuiTextures.Scrollbar.DISABLED_V,
            SLGuiTextures.Scrollbar.WIDTH,
            SLGuiTextures.Scrollbar.HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

        g.enableScissor(listX - 2, listY,
            listX + SELECTION_WIDTH + 2, listY + SLGuiTextures.List.HEIGHT);

        String current = stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "");
        GroupKey currentKey = stack.get(SLDataComponents.SELECTED_GROUP_KEY.get());

        for (int i = 0; i < groups.size(); i++) {
            GroupRef group = groups.get(i);
            String gn = group.displayName();
            int itemY = listY + (i * SLGuiTextures.List.ITEM_H) - (int) scrollOffset;
            if (itemY + SLGuiTextures.List.ITEM_H < listY
                || itemY > listY + SLGuiTextures.List.HEIGHT) continue;

            boolean sel = currentKey != null ? currentKey.equals(group.key()) : Objects.equals(current, gn);
            boolean hover = mx >= leftPos + 10
                && mx <= leftPos + 10 + SELECTION_WIDTH
                && my >= itemY && my < itemY + SLGuiTextures.List.ITEM_H;
            if (hover) this.hoveredGroup = group;

            if (sel) g.fill(leftPos + 10, itemY,
                leftPos + 10 + SELECTION_WIDTH,
                itemY + SLGuiTextures.List.ITEM_H, 0x4498FB98);
            else if (hover) g.fill(leftPos + 10, itemY,
                leftPos + 10 + SELECTION_WIDTH,
                itemY + SLGuiTextures.List.ITEM_H, 0x22FFFFFF);

            int textX = leftPos + 12;
            UUID ownerUUID = group.key().ownerId();
            if (ownerUUID != null) {
                String ownerName = ClientLinkData.INSTANCE.getOwnerName(ownerUUID);
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
            }

            g.drawString(this.font, "#" + gn,
                textX, itemY + 2, sel ? 0x98FB98 : 0xCCCCCC, false);
        }
        g.disableScissor();
    }

    private void renderGroupTooltip(GuiGraphics g, int mx, int my, GroupRef group, boolean shiftDown) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        String gid = group.displayName();
        List<LogisticsNode> nodes = ClientLinkData.INSTANCE.getNodesForGroup(group.key());

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.staticlogistics.tooltip.group_id", gid)
            .withStyle(ChatFormatting.GOLD));
        String owner = ClientLinkData.INSTANCE.getOwnerName(group.key().ownerId());
        if (!owner.isEmpty())
            lines.add(Component.translatable("msg.staticlogistics.owner_display", owner)
                .withStyle(ChatFormatting.GRAY));

        if (!nodes.isEmpty()) {
            int maxShown = shiftDown ? Integer.MAX_VALUE : 5;
            int count = 0;
            for (LogisticsNode node : nodes) {
                if (count >= maxShown) break;
                lines.add(formatNode(node, player));
                count++;
            }
            if (nodes.size() > maxShown) {
                lines.add(Component.translatable("gui.staticlogistics.tooltip.shift_more",
                    SLKeyMappings.GROUP_DETAILS_AND_EXPORT.getTranslatedKeyMessage(),
                    nodes.size() - maxShown).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
            lines.add(Component.empty());
        }

        lines.add(Component.translatable("gui.staticlogistics.tooltip.select_hint")
            .withStyle(ChatFormatting.BLUE));
        g.renderComponentTooltip(this.font, lines, mx, my);
    }

    private static Component formatNode(LogisticsNode node, Player player) {
        BlockPos pos = node.gPos().pos();
        var location = NodeDisplayText.details(node).withStyle(ChatFormatting.WHITE);
        if (!node.isInSameDimension(player.level().dimension())) return location;
        double distance = Math.sqrt(pos.distToCenterSqr(player.position()));
        return location.append(Component.literal("  "))
            .append(NodeDisplayText.distanceFromPlayer(distance).withStyle(ChatFormatting.AQUA));
    }

    private int getMaxScroll() {
        return Math.max(0,
            getFilteredGroups().size() * SLGuiTextures.List.ITEM_H
                - SLGuiTextures.List.HEIGHT);
    }

    private List<GroupRef> getFilteredGroups() {
        int cv = ClientLinkData.INSTANCE.getDataVersion();
        if (cv == lastSeenVersion) return cachedGroupList;

        Player p = Minecraft.getInstance().player;
        if (p == null) return Collections.emptyList();

        String filter = this.confirmedSearchTerm.toLowerCase();
        this.cachedGroupList = ClientLinkData.INSTANCE
            .getAccessibleGroupRefs().stream()
            .filter(group -> !group.displayName().isEmpty())
            .filter(group -> group.displayName().toLowerCase().contains(filter))
            .sorted((a, b) -> {
                String aName = a.displayName(), bName = b.displayName();
                boolean aN = aName.matches("\\d+"), bN = bName.matches("\\d+");
                if (aN && bN) {
                    try {
                        int byNumber = Integer.compare(Integer.parseInt(aName), Integer.parseInt(bName));
                        return byNumber != 0 ? byNumber : a.key().ownerId().compareTo(b.key().ownerId());
                    } catch (NumberFormatException e) {
                        return aName.compareToIgnoreCase(bName);
                    }
                }
                int byName = aN ? -1 : (bN ? 1 : aName.compareToIgnoreCase(bName));
                return byName != 0 ? byName : a.key().ownerId().compareTo(b.key().ownerId());
            }).collect(Collectors.toList());

        this.lastSeenVersion = cv;
        return cachedGroupList;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (this.searchBox.mouseClicked(mx, my, b)) return true;

        if (mx >= leftPos + 73 && mx <= leftPos + 73 + SLGuiTextures.ZOOM_WIDTH
            && my >= topPos + 13 && my <= topPos + 13 + SLGuiTextures.ZOOM_HEIGHT) {
            this.confirmedSearchTerm = this.searchBox.getValue().trim();
            this.scrollOffset = 0;
            this.lastSeenVersion = -1;
            playClickSound();
            return true;
        }

        int sx = leftPos + SCROLLBAR_X, sy = topPos + SCROLLBAR_Y;
        if (mx >= sx && mx <= sx + SLGuiTextures.Scrollbar.WIDTH
            && my >= sy && my <= sy + SLGuiTextures.Scrollbar.TRACK_HEIGHT) {
            if (getMaxScroll() > 0) {
                this.isScrolling = true;
                updateScrollFromMouse(my);
            }
            return true;
        }

        List<GroupRef> groups = getFilteredGroups();
        int listY = topPos + LIST_OFFSET_Y;
        if (mx >= leftPos + 10 && mx <= leftPos + 10 + SELECTION_WIDTH
            && my >= listY && my < listY + SLGuiTextures.List.HEIGHT) {
            for (int i = 0; i < groups.size(); i++) {
                int iy = listY + (i * SLGuiTextures.List.ITEM_H) - (int) scrollOffset;
                if (my >= iy && my < iy + SLGuiTextures.List.ITEM_H) {
                    syncSettings(groups.get(i));
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, b);
    }

    private void syncSettings(GroupRef group) {
        String groupId = group.displayName();
        stack.set(SLDataComponents.SELECTED_GROUP.get(), groupId);
        stack.set(SLDataComponents.SELECTED_GROUP_KEY.get(), group.key());
        SelectionContext.setSelection(groupId, group.key(), 0);
        PacketDistributor.sendToServer(
            new C2SUpdateToolSettingsPayload(groupId, group.key(), 0, List.of(),
                stack.getOrDefault(SLDataComponents.SELECTED_TYPES_MASK.get(), 0)));
        playClickSound();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int ms = getMaxScroll();
        if (ms > 0) {
            this.scrollOffset = Mth.clamp(
                this.scrollOffset - (float) dy * SLGuiTextures.List.ITEM_H, 0, ms);
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (this.isScrolling) {
            updateScrollFromMouse(my);
            return true;
        }
        return super.mouseDragged(mx, my, b, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int b) {
        this.isScrolling = false;
        return super.mouseReleased(mx, my, b);
    }

    private void updateScrollFromMouse(double mouseY) {
        int ms = getMaxScroll();
        if (ms > 0) {
            float rp = (float) ((mouseY - (topPos + SCROLLBAR_Y))
                / SLGuiTextures.Scrollbar.TRACK_HEIGHT);
            this.scrollOffset = Mth.clamp(rp * ms, 0, ms);
        }
    }

    private void playClickSound() {
        com.coobird.staticlogistics.client.gui.component.SoundUtil.playClickSound();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int k, int s, int m) {
        if (this.searchBox.canConsumeInput()) {
            if (k == 257 || k == 335) {
                this.confirmedSearchTerm = this.searchBox.getValue().trim();
                this.scrollOffset = 0;
                this.lastSeenVersion = -1;
                return true;
            }
            return super.keyPressed(k, s, m);
        }
        if (Minecraft.getInstance().options.keyInventory.matches(k, s) || k == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(k, s, m);
    }
}
