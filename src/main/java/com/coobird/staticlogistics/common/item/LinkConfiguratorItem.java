package com.coobird.staticlogistics.common.item;

import com.coobird.staticlogistics.client.ClientAccess;
import com.coobird.staticlogistics.common.init.SLDataComponents;
import com.coobird.staticlogistics.common.util.TransferUtils;
import com.coobird.staticlogistics.core.StaticLink;
import com.coobird.staticlogistics.storage.GroupService;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.transfer.TransferType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class LinkConfiguratorItem extends Item {
    public LinkConfiguratorItem() {
        super(new Properties().stacksTo(1));
    }

    private record ToolSettings(ToolMode mode, TransferType type, String group, @Nullable BlockPos firstPos,
                                @Nullable Direction firstFace, @Nullable ResourceKey<Level> firstDim) {
    }

    private ToolSettings getSettings(ItemStack stack) {
        return new ToolSettings(
            ToolMode.values()[stack.getOrDefault(SLDataComponents.TOOL_MODE.get(), 0)],
            stack.getOrDefault(SLDataComponents.SELECTED_TYPE.get(), TransferType.ITEM),
            stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "1"),
            stack.get(SLDataComponents.FIRST_POS.get()),
            stack.get(SLDataComponents.FIRST_FACE.get()),
            stack.get(SLDataComponents.FIRST_DIM.get())
        );
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            if (stack.has(SLDataComponents.FIRST_POS.get())) {
                resetSource(stack, player, level);
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }
        } else {
            if (level.isClientSide) ClientAccess.openLinkerScreen(stack);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null || !player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;

        ItemStack stack = context.getItemInHand();
        ToolSettings settings = getSettings(stack);
        BlockPos pos = context.getClickedPos();
        Direction face = context.getClickedFace();
        LinkManager manager = LinkManager.get(serverLevel);

        if (settings.mode == ToolMode.REMOVE) {
            List<StaticLink> toRemove = new ArrayList<>();
            manager.getLinksList().stream()
                .filter(l -> l.destPos().equals(pos) && l.destDimension().equals(level.dimension()))
                .forEach(toRemove::add);

            for (Direction dir : Direction.values()) {
                toRemove.addAll(manager.getLinksByKey(manager.posToKey(pos, dir)));
            }

            if (!toRemove.isEmpty()) {
                int removedCount = manager.removeLinksBulk(toRemove, serverLevel, player);
                if (removedCount > 0) {
                    player.displayClientMessage(Component.translatable("msg.staticlogistics.links_cleared", removedCount).withStyle(ChatFormatting.YELLOW), true);
                    level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 1.0f, 1.2f);
                    stack.remove(SLDataComponents.FIRST_POS.get());
                } else {
                    player.displayClientMessage(Component.translatable("msg.staticlogistics.no_permission").withStyle(ChatFormatting.RED), true);
                }
            } else {
                player.displayClientMessage(Component.translatable("msg.staticlogistics.no_link_found").withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResult.SUCCESS;
        }

        if (settings.mode == ToolMode.CONNECT) {
            manager.getLinksByKey(manager.posToKey(pos, face)).forEach(link -> {
                if (GroupService.canAccess(link, player) && !link.owner().equals(player.getUUID())) {
                    manager.updateLinkOwner(link, player.getUUID(), player.getGameProfile().getName(), serverLevel);
                    player.displayClientMessage(Component.translatable("msg.staticlogistics.owner_updated").withStyle(ChatFormatting.GRAY), true);
                }
            });

            if (!TransferUtils.hasLogisticsCapability(level, pos, face)) return InteractionResult.FAIL;

            if (settings.firstPos == null) {
                incrementGroupId(stack);
                setSource(stack, pos, face, player, serverLevel);
            } else {
                if (pos.equals(settings.firstPos) && face == settings.firstFace && level.dimension().equals(settings.firstDim)) {
                    resetSource(stack, player, level);
                } else {
                    LinkManager.ActionResult result = null;
                    if (settings.firstFace != null && settings.firstDim != null) {
                        result = manager.tryAddLink(
                            player, settings.firstPos, settings.firstFace, settings.firstDim,
                            pos, face, level.dimension(),
                            settings.type, settings.group,
                            stack.getOrDefault(SLDataComponents.PRIORITY.get(), 0),
                            serverLevel
                        );
                    }

                    if (result != null && result.success()) {
                        player.displayClientMessage(Component.translatable("msg.staticlogistics.link_created", pos.toShortString()).withStyle(ChatFormatting.AQUA), true);
                        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.4f, 1.8f);
                    } else if (result != null && result.message() != null) {
                        player.displayClientMessage(result.message().copy().withStyle(ChatFormatting.RED), true);
                    }
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private void incrementGroupId(ItemStack stack) {
        String currentGroup = stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "0");
        try {
            int id = Integer.parseInt(currentGroup);
            stack.set(SLDataComponents.SELECTED_GROUP.get(), String.valueOf(id + 1));
        } catch (NumberFormatException ignored) {}
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ToolSettings settings = getSettings(stack);

        // 【修改点】统一传参模式：使用 Component.translatable(Key, Argument)
        // 这样在语言文件里定义了 "当前模式: %s" 时，翻译系统会自动把模式名填入 %s
        tooltip.add(Component.translatable("tooltip.staticlogistics.mode", settings.mode.getDisplayName()));

        String typeKey = "type.staticlogistics." + settings.type.getSerializedName();
        Component typeName = Component.translatable(typeKey).withStyle(style -> style.withColor(settings.type.getColor()));
        tooltip.add(Component.translatable("tooltip.staticlogistics.type", typeName));

        tooltip.add(Component.translatable("tooltip.staticlogistics.group", Component.literal(settings.group).withStyle(ChatFormatting.GRAY)));

        if (settings.firstPos != null) {
            tooltip.add(Component.empty());
            String dimName = settings.firstDim != null ? settings.firstDim.location().getPath() : "unknown";
            String locationInfo = settings.firstPos.toShortString() + " (" + dimName + ")";
            tooltip.add(Component.translatable("tooltip.staticlogistics.linked_from", locationInfo).withStyle(ChatFormatting.BLUE));
            tooltip.add(Component.translatable("tooltip.staticlogistics.reset_hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.staticlogistics.no_source").withStyle(ChatFormatting.DARK_RED));
        }

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.staticlogistics.use_hint").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.staticlogistics.shift_use_hint").withStyle(ChatFormatting.AQUA));
    }

    private void setSource(ItemStack stack, BlockPos pos, Direction face, Player player, ServerLevel level) {
        stack.set(SLDataComponents.FIRST_POS.get(), pos);
        stack.set(SLDataComponents.FIRST_FACE.get(), face);
        stack.set(SLDataComponents.FIRST_DIM.get(), level.dimension());
        String group = stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "1");
        String dimName = level.dimension().location().getPath();
        player.displayClientMessage(Component.translatable("msg.staticlogistics.source_set",
            pos.toShortString(), dimName, group).withStyle(ChatFormatting.GREEN), true);

        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.8f, 1.2f);
    }

    private void resetSource(ItemStack stack, Player player, Level level) {
        stack.remove(SLDataComponents.FIRST_POS.get());
        stack.remove(SLDataComponents.FIRST_FACE.get());
        stack.remove(SLDataComponents.FIRST_DIM.get());
        player.displayClientMessage(Component.translatable("msg.staticlogistics.source_reset").withStyle(ChatFormatting.YELLOW), true);
        level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.5f, 0.5f);
    }

    public enum ToolMode {
        CONNECT("connect", ChatFormatting.AQUA),
        REMOVE("remove", ChatFormatting.RED),
        CONFIGURE("configure", ChatFormatting.GOLD);
        private final String name;
        private final ChatFormatting color;

        ToolMode(String n, ChatFormatting c) {
            this.name = n;
            this.color = c;
        }

        public Component getDisplayName() {
            return Component.translatable("mode.staticlogistics." + name).withStyle(color);
        }
    }
}