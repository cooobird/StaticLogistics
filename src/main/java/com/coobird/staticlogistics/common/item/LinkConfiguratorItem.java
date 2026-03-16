package com.coobird.staticlogistics.common.item;

import com.coobird.staticlogistics.client.ClientAccess;
import com.coobird.staticlogistics.common.init.SLDataComponents;
import com.coobird.staticlogistics.common.util.TransferUtils;
import com.coobird.staticlogistics.core.NodeEntry;
import com.coobird.staticlogistics.storage.GroupService;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.transfer.TransferType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class LinkConfiguratorItem extends Item {
    public LinkConfiguratorItem() {
        super(new Properties().stacksTo(1));
    }

    public record ToolSettings(ToolMode mode, TransferType type, String group, List<NodeEntry> storedNodes,
                               @Nullable ToolMode storedMode) {
    }

    public ToolSettings getSettings(ItemStack stack) {
        Integer sModeIdx = stack.get(SLDataComponents.STORED_MODE.get());
        return new ToolSettings(
            ToolMode.values()[stack.getOrDefault(SLDataComponents.TOOL_MODE.get(), 0)],
            stack.getOrDefault(SLDataComponents.SELECTED_TYPE.get(), TransferType.ITEM),
            stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "1"),
            stack.getOrDefault(SLDataComponents.STORED_NODES.get(), List.of()),
            sModeIdx != null ? ToolMode.values()[sModeIdx] : null
        );
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!level.isClientSide && isSelected && level.getGameTime() % 40 == 0) {
            List<NodeEntry> nodes = stack.get(SLDataComponents.STORED_NODES.get());
            if (nodes == null || nodes.isEmpty()) return;

            List<NodeEntry> validNodes = new ArrayList<>(nodes);
            boolean changed = validNodes.removeIf(node -> {
                if (!node.pos().dimension().equals(level.dimension())) return false;
                return level.getBlockState(node.pos().pos()).isAir();
            });

            if (changed) {
                stack.set(SLDataComponents.STORED_NODES.get(), validNodes);
                if (validNodes.isEmpty()) stack.remove(SLDataComponents.STORED_MODE.get());
            }
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            if (!level.isClientSide) clearNodes(stack, player, level);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (level.isClientSide) ClientAccess.openLinkerScreen(stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player == null || !player.isSecondaryUseActive()) return InteractionResult.PASS;

        BlockPos pos = context.getClickedPos();
        Direction face = context.getClickedFace();
        ToolSettings settings = getSettings(stack);

        if (!TransferUtils.hasLogisticsCapability(level, pos, face)) return InteractionResult.FAIL;

        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;
        LinkManager manager = LinkManager.get(serverLevel);
        if (manager == null) return InteractionResult.PASS;

        if (settings.mode == ToolMode.REMOVE) {
            LinkManager.ActionResult res = manager.removeAllLinksContextual(serverLevel, pos, face, player);

            if (res.success()) {
                if (res.count() > 0) {
                    level.playSound(null, pos, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 0.5f, 0.8f);
                } else {
                    player.displayClientMessage(Component.translatable("msg.staticlogistics.no_links_found").withStyle(ChatFormatting.GRAY), true);
                }
            } else {
                if (res.message() != null)
                    player.displayClientMessage(res.message().withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.SUCCESS;
        }

        if (settings.mode == ToolMode.CONFIGURE) return InteractionResult.SUCCESS;

        if (settings.storedMode != null && settings.storedMode != settings.mode) {
            executeBatchLink(stack, settings, pos, face, serverLevel, manager, player);
        } else {
            addNode(stack, GlobalPos.of(level.dimension(), pos), face, settings.mode, player, level);
        }
        return InteractionResult.SUCCESS;
    }

    private void executeBatchLink(ItemStack stack, ToolSettings settings, BlockPos pos, Direction face, ServerLevel level, LinkManager manager, Player player) {
        int priority = stack.getOrDefault(SLDataComponents.PRIORITY.get(), 0);
        LinkManager.ActionResult res = manager.executeBatchLink(level, player, settings.storedNodes, pos, face, settings.type, settings.group, priority, settings.mode);

        if (res.success()) {
            player.displayClientMessage(Component.translatable("msg.staticlogistics.batch_linked_to_group", res.count(), settings.group).withStyle(ChatFormatting.AQUA), true);
        }
    }

    private void addNode(ItemStack stack, GlobalPos gpos, Direction face, ToolMode mode, Player player, Level level) {
        List<NodeEntry> nodes = new ArrayList<>(stack.getOrDefault(SLDataComponents.STORED_NODES.get(), List.of()));
        NodeEntry entry = new NodeEntry(gpos, face);
        if (nodes.contains(entry)) {
            nodes.remove(entry);
            player.displayClientMessage(Component.translatable("msg.staticlogistics.node_removed", nodes.size()).withStyle(ChatFormatting.RED), true);
        } else {
            nodes.add(entry);
            stack.set(SLDataComponents.STORED_MODE.get(), mode.ordinal());
            player.displayClientMessage(Component.translatable("msg.staticlogistics.node_added", nodes.size()).withStyle(ChatFormatting.GREEN), true);
        }
        stack.set(SLDataComponents.STORED_NODES.get(), nodes);
        level.playSound(null, gpos.pos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.8f, 1.5f);
    }

    private void clearNodes(ItemStack stack, Player player, Level level) {
        ToolSettings settings = getSettings(stack);

        if (!settings.storedNodes().isEmpty()) {
            Set<String> existingGroups = GroupService.getGroupsForPlayer(level, player);
            String nextGroup = GroupService.getNextGroupId(settings.group(), existingGroups);
            stack.set(SLDataComponents.SELECTED_GROUP.get(), nextGroup);
        }

        stack.remove(SLDataComponents.STORED_NODES.get());
        stack.remove(SLDataComponents.STORED_MODE.get());

        if (player.isSecondaryUseActive()) {
            player.displayClientMessage(Component.translatable("msg.staticlogistics.selection_cleared").withStyle(ChatFormatting.YELLOW), true);
            level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 0.5f);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ToolSettings s = getSettings(stack);
        tooltip.add(Component.translatable("tooltip.staticlogistics.mode", s.mode.getDisplayName()));
        tooltip.add(Component.translatable("tooltip.staticlogistics.group", s.group).withStyle(ChatFormatting.WHITE));

        if (!s.storedNodes.isEmpty()) {
            tooltip.add(Component.empty());
            if (s.storedMode != null) {
                tooltip.add(Component.translatable("tooltip.staticlogistics.stored_nodes", s.storedNodes.size(), s.storedMode.getDisplayName()).withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD));
            }
            int displayCount = Math.min(s.storedNodes.size(), 5);
            for (int i = 0; i < displayCount; i++) {
                BlockPos p = s.storedNodes.get(i).pos().pos();
                tooltip.add(Component.literal(" • ").withStyle(ChatFormatting.GRAY).append(Component.literal(String.format("[%d, %d, %d]", p.getX(), p.getY(), p.getZ())).withStyle(ChatFormatting.AQUA)));
            }
            if (s.storedNodes.size() > 5) {
                tooltip.add(Component.literal(" ...").withStyle(ChatFormatting.GRAY));
            }
        }
    }

    public enum ToolMode {
        LINK_AS_INPUT("link_as_input", ChatFormatting.AQUA),
        LINK_AS_OUTPUT("link_as_output", ChatFormatting.GOLD),
        CONFIGURE("configure", ChatFormatting.LIGHT_PURPLE),
        REMOVE("remove", ChatFormatting.RED);

        private final String name;
        private final ChatFormatting color;

        ToolMode(String n, ChatFormatting c) {
            this.name = n;
            this.color = c;
        }

        public Component getDisplayName() {
            return Component.translatable("mode.staticlogistics." + name).withStyle(color);
        }

        public ToolMode next() {
            ToolMode[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }

        public ToolMode previous() {
            ToolMode[] values = values();
            return values[(this.ordinal() - 1 + values.length) % values.length];
        }
    }
}