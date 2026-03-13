package com.coobird.staticlogistics.common.item;

import com.coobird.staticlogistics.SLConfig;
import com.coobird.staticlogistics.client.gui.LinkConfiguratorScreen;
import com.coobird.staticlogistics.common.init.SLDataComponents;
import com.coobird.staticlogistics.common.util.TransferUtils;
import com.coobird.staticlogistics.core.FaceConfig;
import com.coobird.staticlogistics.core.StaticLink;
import com.coobird.staticlogistics.storage.GroupService;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.transfer.TransferType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LinkConfiguratorItem extends Item {
    public LinkConfiguratorItem() {
        super(new Properties().stacksTo(1));
    }

    private record CachedToolSettings(
        ToolMode mode,
        TransferType type,
        String group,
        @Nullable BlockPos firstPos,
        @Nullable Direction firstFace,
        @Nullable ResourceKey<Level> firstDim
    ) {
    }

    private CachedToolSettings getSettings(ItemStack stack) {
        return new CachedToolSettings(
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
                resetSource(stack, player, level, player.blockPosition());
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }
        } else if (level.isClientSide) {
            Minecraft.getInstance().setScreen(new LinkConfiguratorScreen(stack));
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        ItemStack stack = context.getItemInHand();
        CachedToolSettings settings = getSettings(stack);
        BlockPos clickedPos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();

        if (player.isSecondaryUseActive()) {
            if (settings.mode == ToolMode.REMOVE) {
                if (level instanceof ServerLevel serverLevel) {
                    if (GroupService.smartRemove(serverLevel, clickedPos, clickedFace, player.getUUID())) {
                        player.displayClientMessage(Component.translatable("msg.staticlogistics.links_cleared", clickedPos.toShortString()), true);
                    } else {
                        player.displayClientMessage(Component.translatable("msg.staticlogistics.no_permission").withStyle(ChatFormatting.RED), true);
                    }
                }
                level.playSound(player, clickedPos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.5f, 1.2f);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }

            if (settings.mode == ToolMode.CONFIGURE) {
                if (level instanceof ServerLevel) {
                    player.displayClientMessage(Component.translatable("msg.staticlogistics.todo.face_gui", clickedFace.getName()).withStyle(ChatFormatting.GOLD), true);
                }
                level.playSound(player, clickedPos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.8f, 1.2f);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }

            if (settings.mode == ToolMode.CONNECT) {
                if (!TransferUtils.hasLogisticsCapability(level, clickedPos, clickedFace))
                    return InteractionResult.FAIL;

                if (clickedPos.equals(settings.firstPos) && clickedFace == settings.firstFace) {
                    resetSource(stack, player, level, clickedPos);
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }

                if (settings.firstPos == null || settings.firstFace == null) {
                    setSource(stack, clickedPos, clickedFace, player, level);
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }

                if (level instanceof ServerLevel serverLevel) {
                    LinkManager manager = LinkManager.get(serverLevel);
                    FaceConfig sourceConfig = manager.getFaceConfig(settings.firstPos, settings.firstFace);
                    boolean isCrossDim = !serverLevel.dimension().equals(settings.firstDim);

                    if (isCrossDim && !sourceConfig.isDimensionEffective()) {
                        player.displayClientMessage(Component.translatable("msg.staticlogistics.no_dimension_upgrade").withStyle(ChatFormatting.RED), true);
                        return InteractionResult.FAIL;
                    }

                    if (!sourceConfig.isDimensionEffective()) {
                        int multiplier = sourceConfig.getMaxRangeMultiplier();
                        if (multiplier < 1000000) {
                            int baseRadius = SLConfig.getDefaultRadius();
                            long maxDist = (long) baseRadius * multiplier;
                            double distSq = settings.firstPos.distSqr(clickedPos);

                            if (distSq > (double) maxDist * maxDist) {
                                player.displayClientMessage(Component.translatable("msg.staticlogistics.too_far", maxDist).withStyle(ChatFormatting.RED), true);
                                return InteractionResult.FAIL;
                            }
                        }
                    }

                    int priority = stack.getOrDefault(SLDataComponents.PRIORITY.get(), 0);
                    StaticLink newLink = new StaticLink(
                        settings.firstPos, settings.firstFace, clickedPos, clickedFace, serverLevel.dimension(),
                        (1 << settings.type.ordinal()), priority, player.getUUID(), settings.group,
                        sourceConfig.getMaxRangeMultiplier(), sourceConfig.isDimensionEffective()
                    );

                    manager.addLink(newLink, serverLevel);
                    player.displayClientMessage(Component.translatable("msg.staticlogistics.link_created", clickedPos.toShortString()).withStyle(ChatFormatting.AQUA), true);
                }
                level.playSound(player, clickedPos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.4f, 1.8f);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return InteractionResult.PASS;
    }

    private void setSource(ItemStack stack, BlockPos pos, Direction face, Player player, Level level) {
        stack.set(SLDataComponents.FIRST_POS.get(), pos);
        stack.set(SLDataComponents.FIRST_FACE.get(), face);
        stack.set(SLDataComponents.FIRST_DIM.get(), level.dimension());

        if (level instanceof ServerLevel serverLevel) {
            String groupId = String.valueOf(GroupService.getNextAvailableGroupId(serverLevel, player.getUUID()));
            stack.set(SLDataComponents.SELECTED_GROUP.get(), groupId);
            player.displayClientMessage(Component.translatable("msg.staticlogistics.source_set", pos.toShortString(), face.getName(), groupId).withStyle(ChatFormatting.GREEN), true);
        }
        level.playSound(player, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.8f, 1.2f);
    }

    private void resetSource(ItemStack stack, Player player, Level level, BlockPos pos) {
        stack.remove(SLDataComponents.FIRST_POS.get());
        stack.remove(SLDataComponents.FIRST_FACE.get());
        stack.remove(SLDataComponents.FIRST_DIM.get());
        if (level instanceof ServerLevel) {
            player.displayClientMessage(Component.translatable("msg.staticlogistics.source_reset").withStyle(ChatFormatting.YELLOW), true);
        }
        level.playSound(player, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.5f, 0.5f);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CachedToolSettings settings = getSettings(stack);
        tooltip.add(Component.translatable("tooltip.staticlogistics.linker.mode", settings.mode.getDisplayName()).withStyle(ChatFormatting.GRAY));

        if (settings.firstPos != null && settings.firstFace != null) {
            tooltip.add(Component.translatable("tooltip.staticlogistics.linker.source", settings.firstPos.toShortString(), settings.firstFace.getName()).withStyle(ChatFormatting.GREEN));
        }

        tooltip.add(Component.translatable("tooltip.staticlogistics.linker.type", Component.translatable("type.staticlogistics." + settings.type.getSerializedName())).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("gui.staticlogistics.label.group").withStyle(ChatFormatting.GOLD));

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.staticlogistics.linker.hint.gui").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.staticlogistics.linker.hint.action").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    public enum ToolMode {
        CONNECT("connect", ChatFormatting.AQUA),
        REMOVE("remove", ChatFormatting.RED),
        CONFIGURE("configure", ChatFormatting.GOLD);

        private final String name;
        private final ChatFormatting color;

        ToolMode(String name, ChatFormatting color) {
            this.name = name;
            this.color = color;
        }

        public Component getDisplayName() {
            return Component.translatable("mode.staticlogistics." + name).withStyle(color);
        }
    }
}