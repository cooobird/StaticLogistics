package com.coobird.staticlogistics.gui.screen;

import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.client.data.SelectionContext;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
import com.coobird.staticlogistics.integration.ModCompat;
import com.coobird.staticlogistics.network.c2s.C2SGroupRenamePayload;
import com.coobird.staticlogistics.network.c2s.C2SUpdateToolSettingsPayload;
import com.coobird.staticlogistics.registry.SLDataComponents;
import com.coobird.staticlogistics.util.LogisticsConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.stream.Collectors;

public class LinkConfiguratorScreen extends Screen {
    private static final int SIDE_PANEL_X = SLGuiTextures.Background.WIDTH + 2;
    private static final int BAR_W = 52, BAR_X = 11, BAR_Y = 13;
    private static final int LIST_OFFSET_X = 10, LIST_OFFSET_Y = 32;
    private static final int SCROLLBAR_X = 88, SCROLLBAR_Y = 25;
    private static final int SELECTION_WIDTH = 75;
    private static final int MODE_COUNT = 6;

    private final ItemStack stack;
    private int leftPos, topPos, modeIdx;
    private EditBox searchBox;
    private EditBox renameBox;
    private float scrollOffset;
    private boolean isScrolling;
    private long lastClickTime;
    private String lastClickedGroup = "";
    private String editingGroupId = "";
    private String confirmedSearchTerm = "";
    private String hoveredGroupId = "";
    private TransferType hoveredType = null;

    private int lastSeenVersion = -1;
    private List<String> cachedGroupList = Collections.emptyList();

    public LinkConfiguratorScreen(ItemStack stack) {
        super(Component.translatable("gui.staticlogistics.linker_settings"));
        this.stack = stack;
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - SLGuiTextures.Background.WIDTH) / 2;
        this.topPos = (this.height - SLGuiTextures.Background.HEIGHT) / 2;
        int sx = leftPos + SIDE_PANEL_X;

        SelectionContext.syncFromItem(this.stack);
        this.modeIdx = SelectionContext.getSelectedMode();
        this.lastClickedGroup = SelectionContext.getSelectedGroupId();
        this.stack.set(SLDataComponents.SELECTED_GROUP.get(), this.lastClickedGroup);

        this.searchBox = new EditBox(this.font, sx + BAR_X, topPos + BAR_Y + 1, BAR_W - 2, 8, Component.empty());
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(20);
        this.searchBox.setTextColor(0xFFFFFF);
        this.searchBox.setHint(Component.translatable("gui.staticlogistics.search_hint").withStyle(ChatFormatting.DARK_GRAY));

        this.renameBox = new EditBox(this.font, 0, 0, SELECTION_WIDTH - 4, 10, Component.empty());
        this.renameBox.setBordered(false);
        this.renameBox.setVisible(false);
        this.renameBox.setTextColor(0xFFFFCC);

        this.lastSeenVersion = -1;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        List<String> groups = getFilteredGroups();
        this.hoveredGroupId = "";
        this.hoveredType = null;

        graphics.blit(SLGuiTextures.GUI_ATLAS, leftPos, topPos, SLGuiTextures.Background.U, SLGuiTextures.Background.V, SLGuiTextures.Background.WIDTH, SLGuiTextures.Background.HEIGHT, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        int sx = leftPos + SIDE_PANEL_X;
        graphics.blit(SLGuiTextures.GUI_ATLAS, sx, topPos, 0, 144, SLGuiTextures.Background.BY_GROUP_WIDTH, SLGuiTextures.Background.BY_GROUP_HEIGHT, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

        renderTitle(graphics);
        renderSideTabs(graphics);
        renderGroupList(graphics, groups, sx, mouseX, mouseY);

        this.searchBox.render(graphics, mouseX, mouseY, partialTick);
        if (this.renameBox.isVisible()) this.renameBox.render(graphics, mouseX, mouseY, partialTick);

        renderTransferTypeSection(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);

        if (this.hoveredType != null) {
            renderTypeTooltip(graphics, this.hoveredType, mouseX, mouseY);
        }
        if (!this.hoveredGroupId.isEmpty()) {
            renderGroupTooltip(graphics, mouseX, mouseY, this.hoveredGroupId);
        }
        renderModeTooltips(graphics, mouseX, mouseY);

        graphics.pose().popPose();
    }

    private void renderTransferTypeSection(GuiGraphics g, int mx, int my) {
        int mask = stack.getOrDefault(SLDataComponents.SELECTED_TYPES_MASK.get(), 0);
        List<TransferType> types = new ArrayList<>(TransferRegistries.getAllActive());
        int perRow = 8;
        int btnWidth = 19;
        int spacing = 4;
        int rowSpacing = 22;
        int startX = leftPos + 15;
        int startY = topPos + 18;

        for (int i = 0; i < types.size(); i++) {
            TransferType type = types.get(i);
            boolean isSelected = (mask & type.getFlag()) != 0;
            int row = i / perRow;
            int col = i % perRow;
            int baseX = startX + col * (btnWidth + spacing);
            int baseY = startY + row * rowSpacing;
            int bw = isSelected ? SLGuiTextures.Button.Big.SELECTED_WIDTH : SLGuiTextures.Button.Big.DISABLED_WIDTH;
            int bh = isSelected ? SLGuiTextures.Button.Big.SELECTED_HEIGHT : SLGuiTextures.Button.Big.DISABLED_HEIGHT;
            int u = isSelected ? SLGuiTextures.Button.Big.SELECTED_U : SLGuiTextures.Button.Big.DISABLED_U;
            int v = isSelected ? SLGuiTextures.Button.Big.SELECTED_V : SLGuiTextures.Button.Big.DISABLED_V;
            int drawX = isSelected ? baseX - 1 : baseX;
            int drawY = isSelected ? baseY - 1 : baseY;

            g.blit(SLGuiTextures.GUI_ATLAS, drawX, drawY, u, v, bw, bh, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

            ItemStack iconStack = type.getIcon();
            float scale = 0.8f;
            g.pose().pushPose();
            float iconX = (baseX + 3.5f) / scale;
            float iconY = (baseY + 1.5f) / scale;
            g.pose().scale(scale, scale, 1.0f);
            g.renderFakeItem(iconStack, (int) iconX, (int) iconY);
            g.pose().popPose();

            if (mx >= drawX && mx < drawX + bw && my >= drawY && my < drawY + bh) {
                this.hoveredType = type;
            }
        }
    }

    private void renderTypeTooltip(GuiGraphics g, TransferType type, int mx, int my) {
        List<Component> tooltip = new ArrayList<>();
        int safeColor = type.color() & 0xFFFFFF;
        tooltip.add(Component.translatable(type.translationKey())
            .withStyle(style -> style.withColor(net.minecraft.network.chat.TextColor.fromRgb(safeColor))));
        tooltip.add(Component.translatable(type.translationKey() + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("gui.staticlogistics.tooltip.toggle_type").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        g.renderComponentTooltip(this.font, tooltip, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        int sx = leftPos + SIDE_PANEL_X;

        if (this.searchBox.mouseClicked(mx, my, b)) {
            this.setFocused(this.searchBox);
            return true;
        }
        if (this.renameBox.isVisible() && this.renameBox.mouseClicked(mx, my, b)) {
            this.setFocused(this.renameBox);
            return true;
        }

        if (handleTransferTypeClick(mx, my, b)) return true;

        if (!editingGroupId.isEmpty() && !renameBox.isMouseOver(mx, my)) {
            confirmRename();
        }
        this.setFocused(null);

        if (mx >= leftPos - 25 && mx < leftPos) {
            for (int i = 0; i < MODE_COUNT; i++) {
                int ry = topPos + 7 + (i * 19);
                boolean isSel = (i == modeIdx);
                int bh = isSel ? SLGuiTextures.Button.Middle.SELECTED_HEIGHT : SLGuiTextures.Button.Middle.HEIGHT;
                int by = isSel ? ry - 1 : ry;
                if (my >= by && my < by + bh) {
                    this.modeIdx = i;
                    syncSettings(stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), ""), true);
                    return true;
                }
            }
        }

        if (mx >= sx + 73 && mx <= sx + 73 + SLGuiTextures.ZOOM_WIDTH && my >= topPos + 13 && my <= topPos + 13 + SLGuiTextures.ZOOM_HEIGHT) {
            triggerSearch();
            return true;
        }

        int scrollX = sx + SCROLLBAR_X, scrollY = topPos + SCROLLBAR_Y;
        if (mx >= scrollX && mx <= scrollX + SLGuiTextures.Scrollbar.ENABLED_WIDTH && my >= scrollY && my <= scrollY + SLGuiTextures.Scrollbar.TRACK_HEIGHT) {
            if (getMaxScroll() > 0) {
                this.isScrolling = true;
                updateScrollFromMouse(my);
            }
            return true;
        }

        List<String> groups = getFilteredGroups();
        int listY = topPos + LIST_OFFSET_Y;
        if (mx >= sx + 10 && mx <= sx + 10 + SELECTION_WIDTH && my >= listY && my < listY + SLGuiTextures.List.HEIGHT) {
            for (int i = 0; i < groups.size(); i++) {
                int itemY = listY + (i * SLGuiTextures.List.ITEM_H) - (int) scrollOffset;
                if (my >= itemY && my < itemY + SLGuiTextures.List.ITEM_H) {
                    String gn = groups.get(i);
                    if (hasShiftDown()) {
                        exportToChat(gn);
                        return true;
                    }
                    long now = Util.getMillis();
                    if (Objects.equals(lastClickedGroup, gn) && now - lastClickTime < LogisticsConstants.UI.DOUBLE_CLICK_THRESHOLD_MS)
                        startRename(gn);
                    else syncSettings(gn, true);
                    lastClickedGroup = gn;
                    lastClickTime = now;
                    return true;
                }
            }
        }

        boolean inMainPanel = mx >= leftPos && mx <= leftPos + SLGuiTextures.Background.WIDTH && my >= topPos && my <= topPos + SLGuiTextures.Background.HEIGHT;
        boolean inSidePanel = mx >= sx && mx <= sx + SLGuiTextures.Background.BY_GROUP_WIDTH && my >= topPos && my <= topPos + SLGuiTextures.Background.BY_GROUP_HEIGHT;
        if ((inMainPanel || inSidePanel)) {
            if (!stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "").isEmpty()) {
                syncSettings("", false);
                return true;
            }
        }

        return super.mouseClicked(mx, my, b);
    }

    private boolean handleTransferTypeClick(double mx, double my, int button) {
        List<TransferType> types = new ArrayList<>(TransferRegistries.getAllActive());
        if (types.isEmpty()) return false;

        int mask = stack.getOrDefault(SLDataComponents.SELECTED_TYPES_MASK.get(), 0);
        int perRow = 8;
        int btnWidth = 19;
        int spacing = 4;
        int rowSpacing = 22;
        int startX = leftPos + 15;
        int startY = topPos + 18;
        int rows = (types.size() + perRow - 1) / perRow;
        if (mx < startX || mx >= startX + perRow * (btnWidth + spacing)
            || my < startY || my >= startY + rows * rowSpacing) return false;

        // 找到点击的是哪个类型按钮
        int col = (int) ((mx - startX) / (btnWidth + spacing));
        int row = (int) ((my - startY) / rowSpacing);
        int idx = row * perRow + col;
        if (idx < 0 || idx >= types.size()) return false;

        // 多选 toggle：点谁切谁
        TransferType clicked = types.get(idx);
        int newMask = mask ^ clicked.getFlag();
        stack.set(SLDataComponents.SELECTED_TYPES_MASK.get(), newMask);
        syncSettings(stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), ""), true);
        return true;
    }

    private void renderModeTooltips(GuiGraphics g, int mx, int my) {
        if (mx >= leftPos - 25 && mx < leftPos) {
            for (int i = 0; i < MODE_COUNT; i++) {
                int ry = topPos + 7 + (i * 19);
                boolean isSel = (i == modeIdx);
                int bh = isSel ? SLGuiTextures.Button.Middle.SELECTED_HEIGHT : SLGuiTextures.Button.Middle.HEIGHT;
                int actualY = isSel ? ry - 1 : ry;
                if (my >= actualY && my < actualY + bh) {
                    List<Component> tooltip = new ArrayList<>();
                    String key = switch (i) {
                        case 0 -> "mode.staticlogistics.wrench";
                        case 1 -> "mode.staticlogistics.link_as_input";
                        case 2 -> "mode.staticlogistics.link_as_output";
                        case 3 -> "mode.staticlogistics.remove";
                        case 4 -> "mode.staticlogistics.face_config";
                        default -> "mode.staticlogistics.container_config";
                    };
                    tooltip.add(Component.translatable(key).withStyle(ChatFormatting.YELLOW));
                    tooltip.add(Component.translatable(key + ".desc").withStyle(ChatFormatting.GRAY));
                    g.renderComponentTooltip(this.font, tooltip, mx, my);
                }
            }
        }
    }

    private void renderGroupTooltip(GuiGraphics g, int mx, int my, String gid) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        List<BlockPos> positions = ClientLinkData.INSTANCE.getPositionsForGroup(gid).stream().distinct().toList();

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.staticlogistics.tooltip.group_id", gid).withStyle(ChatFormatting.GOLD));

        if (!positions.isEmpty()) {
            for (BlockPos p : positions) {
                double dist = Math.sqrt(p.distToCenterSqr(player.position()));
                lines.add(Component.literal(String.format(" §f[%d, %d, %d] §b(%.1fm)", p.getX(), p.getY(), p.getZ(), dist)));
            }
            lines.add(Component.empty());
        }

        if (editingGroupId.isEmpty()) {
            lines.add(Component.translatable("gui.staticlogistics.tooltip.select_hint").withStyle(ChatFormatting.BLUE));
            lines.add(Component.translatable("gui.staticlogistics.tooltip.rename_hint").withStyle(ChatFormatting.AQUA));
        }
        lines.add(Component.translatable("gui.staticlogistics.tooltip.shift_export").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));

        g.renderComponentTooltip(this.font, lines, mx, my);
    }

    private void exportToChat(String gid) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        List<BlockPos> positions = ClientLinkData.INSTANCE.getPositionsForGroup(gid).stream().distinct().toList();
        player.sendSystemMessage(Component.translatable("msg.staticlogistics.export.header", gid).withStyle(ChatFormatting.GOLD));
        if (positions.isEmpty()) {
            player.sendSystemMessage(Component.literal(" §7> ").append(Component.translatable("msg.staticlogistics.no_nodes_stored").withStyle(ChatFormatting.RED)));
        } else {
            for (BlockPos p : positions) {
                String posStr = p.getX() + " " + p.getY() + " " + p.getZ();
                MutableComponent posEntry = Component.literal(" §7> §a[" + posStr + "]")
                    .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp " + posStr))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("msg.staticlogistics.export.tp_hover").withStyle(ChatFormatting.ITALIC))));
                player.sendSystemMessage(posEntry);
            }
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        this.onClose();
    }

    private void renderGroupList(GuiGraphics g, List<String> groups, int sx, int mx, int my) {
        int maxScroll = getMaxScroll();
        renderScrollBar(g, sx + SCROLLBAR_X, topPos + SCROLLBAR_Y, mx, my, maxScroll);
        int listX = sx + LIST_OFFSET_X, listY = topPos + LIST_OFFSET_Y;
        g.enableScissor(listX - 2, listY, listX + SELECTION_WIDTH + 2, listY + SLGuiTextures.List.HEIGHT);
        String currentGroupId = stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "");
        for (int i = 0; i < groups.size(); i++) {
            String gn = groups.get(i);
            int itemY = listY + (i * SLGuiTextures.List.ITEM_H) - (int) scrollOffset;
            if (itemY + SLGuiTextures.List.ITEM_H < listY || itemY > listY + SLGuiTextures.List.HEIGHT) continue;
            boolean isSelected = Objects.equals(currentGroupId, gn);
            boolean isHovered = mx >= sx + 10 && mx <= sx + 10 + SELECTION_WIDTH && my >= itemY && my < itemY + SLGuiTextures.List.ITEM_H;
            if (isHovered) this.hoveredGroupId = gn;
            if (isSelected)
                g.fill(sx + 10, itemY, sx + 10 + SELECTION_WIDTH, itemY + SLGuiTextures.List.ITEM_H, 0x4498FB98);
            else if (isHovered)
                g.fill(sx + 10, itemY, sx + 10 + SELECTION_WIDTH, itemY + SLGuiTextures.List.ITEM_H, 0x22FFFFFF);

            if (Objects.equals(editingGroupId, gn)) {
                renameBox.setX(sx + 12);
                renameBox.setY(itemY + 1);
                renameBox.setVisible(true);
            } else {
                g.drawString(this.font, "#" + gn, sx + 12, itemY + 2, isSelected ? 0x98FB98 : 0xCCCCCC, false);
            }
        }
        g.disableScissor();
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
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void triggerSearch() {
        this.confirmedSearchTerm = this.searchBox.getValue().trim();
        this.scrollOffset = 0;
        this.lastSeenVersion = -1;
        playClickSound();
    }

    private void startRename(String id) {
        this.editingGroupId = id;
        this.renameBox.setValue(id);
        this.renameBox.setVisible(true);
        this.setFocused(renameBox);
    }

    private void confirmRename() {
        String newId = renameBox.getValue().trim();
        if (!newId.isEmpty() && !Objects.equals(editingGroupId, newId)) {
            PacketDistributor.sendToServer(new C2SGroupRenamePayload(editingGroupId, newId));
            if (stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "").equals(editingGroupId))
                syncSettings(newId, false);
        }
        cancelRename();
    }

    private void cancelRename() {
        this.editingGroupId = "";
        this.renameBox.setVisible(false);
        this.setFocused(null);
    }

    private int getMaxScroll() {
        return Math.max(0, getFilteredGroups().size() * SLGuiTextures.List.ITEM_H - SLGuiTextures.List.HEIGHT);
    }

    private List<String> getFilteredGroups() {
        int currentVersion = ClientLinkData.INSTANCE.getDataVersion();
        if (currentVersion == lastSeenVersion && !cachedGroupList.isEmpty()) return cachedGroupList;

        Player p = Minecraft.getInstance().player;
        if (p == null) return Collections.emptyList();

        String filter = this.confirmedSearchTerm.toLowerCase();
        UUID playerUUID = p.getUUID();

        // 收集所有需查询的 UUID（自己 + FTB 队友）
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

    private void renderTitle(GuiGraphics g) {
        int tw = 110, tx = leftPos + (SLGuiTextures.Background.WIDTH - tw) / 2, ty = topPos - 8;
        g.blit(SLGuiTextures.GUI_ATLAS, tx + tw - 2, ty, SLGuiTextures.Title.U + SLGuiTextures.Button.Small.DISABLED_WIDTH - 2, SLGuiTextures.Title.V, 2, SLGuiTextures.Button.Small.DISABLED_HEIGHT, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.blit(SLGuiTextures.GUI_ATLAS, tx, ty, SLGuiTextures.Title.U, SLGuiTextures.Title.V, 2, SLGuiTextures.Button.Small.DISABLED_HEIGHT, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.blit(SLGuiTextures.GUI_ATLAS, tx + 2, ty, tw - 4, SLGuiTextures.Button.Small.DISABLED_HEIGHT, SLGuiTextures.Title.U + 2, SLGuiTextures.Title.V, 1, SLGuiTextures.Button.Small.DISABLED_HEIGHT, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.drawString(this.font, this.title, tx + (tw - this.font.width(this.title)) / 2, ty + 4, 0x98FB98, false);
    }

    private void renderSideTabs(GuiGraphics g) {
        for (int i = 0; i < MODE_COUNT; i++) {
            int ry = topPos + 7 + (i * 19);
            boolean sel = (i == modeIdx);
            int bw = sel ? SLGuiTextures.Button.Middle.SELECTED_WIDTH : SLGuiTextures.Button.Middle.WIDTH;
            int bh = sel ? SLGuiTextures.Button.Middle.SELECTED_HEIGHT : SLGuiTextures.Button.Middle.HEIGHT;
            int bx = leftPos - bw, by = sel ? ry - 1 : ry;
            g.blit(SLGuiTextures.GUI_ATLAS, bx, by, sel ? SLGuiTextures.Button.Middle.SELECTED_U : SLGuiTextures.Button.Middle.DISABLED_U, sel ? SLGuiTextures.Button.Middle.SELECTED_V : SLGuiTextures.Button.Middle.DISABLED_V, bw, bh, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
            int iconU;
            int iconV;
            if (i == 0) {
                iconU = SLGuiTextures.Icon.WRANCH_U;
                iconV = SLGuiTextures.Icon.WRANCH_V;
            } else {
                iconU = sel ? SLGuiTextures.Icon.SELECTED_U : SLGuiTextures.Icon.NORMAL_U;
                iconV = switch (i) {
                    case 1 -> SLGuiTextures.Icon.INPUT_V;
                    case 2 -> SLGuiTextures.Icon.OUTPUT_V;
                    case 3 -> SLGuiTextures.Icon.DISCONNECT_V;
                    default -> SLGuiTextures.Icon.CONFIG_V;
                };
            }
            g.blit(SLGuiTextures.GUI_ATLAS, bx + (bw - 19) / 2, by + (bh - 15) / 2 - 1, iconU, iconV, 19, 15, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        }
    }

    private void renderScrollBar(GuiGraphics g, int x, int y, int mx, int my, int maxScroll) {
        boolean showActive = maxScroll > 0 && ((mx >= x && mx <= x + SLGuiTextures.Scrollbar.ENABLED_WIDTH && my >= y && my <= y + SLGuiTextures.Scrollbar.TRACK_HEIGHT) || this.isScrolling);
        int knobY = maxScroll > 0 ? (int) (scrollOffset / maxScroll * (SLGuiTextures.Scrollbar.TRACK_HEIGHT - SLGuiTextures.Scrollbar.ENABLED_HEIGHT)) : 0;
        g.blit(SLGuiTextures.GUI_ATLAS, x, y + knobY, showActive ? SLGuiTextures.Scrollbar.ENABLED_U : SLGuiTextures.Scrollbar.DISABLED_U, SLGuiTextures.Scrollbar.ENABLED_V, SLGuiTextures.Scrollbar.ENABLED_WIDTH, SLGuiTextures.Scrollbar.ENABLED_HEIGHT, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
    }

    private void updateScrollFromMouse(double mouseY) {
        int maxScroll = getMaxScroll();
        if (maxScroll > 0) {
            float relativePos = (float) ((mouseY - (topPos + SCROLLBAR_Y)) / SLGuiTextures.Scrollbar.TRACK_HEIGHT);
            this.scrollOffset = Mth.clamp(relativePos * maxScroll, 0, maxScroll);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int maxScroll = getMaxScroll();
        if (maxScroll > 0) {
            this.scrollOffset = Mth.clamp(this.scrollOffset - (float) dy * SLGuiTextures.List.ITEM_H, 0, maxScroll);
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

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBox.canConsumeInput() || this.renameBox.canConsumeInput()) {
            if (keyCode == 257 || keyCode == 335) {
                if (this.searchBox.isFocused()) triggerSearch();
                else if (this.renameBox.isVisible()) confirmRename();
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
}