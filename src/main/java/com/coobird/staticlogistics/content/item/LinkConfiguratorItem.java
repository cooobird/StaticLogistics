package com.coobird.staticlogistics.content.item;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.logistics.NodeConfiguratorTool;
import com.coobird.staticlogistics.transfer.LogisticsResource;
import com.coobird.staticlogistics.content.SLKeyNames;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import com.coobird.staticlogistics.transfer.TransferTypeSelection;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.api.group.GroupKey;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ItemAbility;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LinkConfiguratorItem extends Item implements NodeConfiguratorTool {
    private static final Map<ToolMode, ModeHandler> HANDLERS = new EnumMap<>(ToolMode.class);

    static {
        HANDLERS.put(ToolMode.WRENCH, new WrenchModeHandler());
        HANDLERS.put(ToolMode.NODE_CONFIG, new NodeConfigModeHandler());
        HANDLERS.put(ToolMode.REMOVE, new RemoveModeHandler());
        HANDLERS.put(ToolMode.LINK_AS_INSERT, new LinkModeHandler());
        HANDLERS.put(ToolMode.LINK_AS_EXTRACT, new LinkModeHandler());
    }

    public LinkConfiguratorItem() {
        super(new Properties().stacksTo(1).attributes(
            ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 6.0, AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, -2.4, AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND)
                .build()
        ));
    }

    public record ToolSettings(ToolMode mode, List<ResourceLocation> selectedTypeIds, String group,
                               @Nullable GroupKey groupKey,
                               List<LogisticsNode> storedNodes,
                               @Nullable ToolMode storedMode) {
        public List<LogisticsResource<?>> getSelectedTypes() {
            return TransferTypeSelection.selectedTypes(selectedTypeIds, TransferRegistries.getAllActive());
        }

    }

    public ToolSettings getSettings(ItemStack stack) {
        Integer sModeIdx = stack.get(SLDataComponents.STORED_MODE.get());
        List<ResourceLocation> selectedTypeIds = stack.get(SLDataComponents.SELECTED_TYPES.get());
        int legacyMask = stack.getOrDefault(SLDataComponents.SELECTED_TYPES_MASK.get(), 0);
        selectedTypeIds = TransferTypeSelection.mergeIdsWithMask(
            selectedTypeIds == null ? List.of() : selectedTypeIds,
            legacyMask, TransferRegistries.getAllActive());
        return new ToolSettings(
            ToolMode.fromId(stack.getOrDefault(SLDataComponents.TOOL_MODE.get(), 0)),
            selectedTypeIds,
            stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), ""),
            stack.get(SLDataComponents.SELECTED_GROUP_KEY.get()),
            stack.getOrDefault(SLDataComponents.STORED_NODES.get(), List.of()),
            sModeIdx != null ? ToolMode.fromId(sModeIdx) : null
        );
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ToolSettings settings = getSettings(stack);
        tooltip.add(Component.translatable("tooltip.staticlogistics.scroll_hint",
            Component.keybind(SLKeyNames.TOOL_MODE_SCROLL)).withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("tooltip.staticlogistics.mode", settings.mode().getDisplayName()));
        String types = settings.getSelectedTypes().stream().map(t -> Component.translatable(t.translationKey()).getString()).collect(Collectors.joining(", "));
        tooltip.add(Component.translatable("tooltip.staticlogistics.type", types.isEmpty() ? Component.translatable("tooltip.staticlogistics.none") : Component.literal(types)));
        tooltip.add(Component.translatable("tooltip.staticlogistics.group", settings.group().isEmpty() ? Component.translatable("tooltip.staticlogistics.none") : Component.literal(settings.group())));
        if (!settings.storedNodes().isEmpty() && settings.storedMode() != null) {
            tooltip.add(Component.translatable("tooltip.staticlogistics.stored_mode", settings.storedMode().getDisplayName()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.staticlogistics.saved_list"));
            for (LogisticsNode n : settings.storedNodes()) {
                String nodeStr = n.gPos().pos().toShortString() + " " + n.face();
                tooltip.add(Component.literal("  " + nodeStr).withStyle(ChatFormatting.WHITE));
            }
        }
        tooltip.add(Component.translatable("tooltip.staticlogistics.auto_clean_info").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.staticlogistics.clear_stored_hint",
            Component.keybind(SLKeyNames.CLEAR_STORED_NODES)).withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive()) {
            if (level.isClientSide) LinkConfiguratorClientHooks.openScreen(stack);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        } else {
            LinkOperationHelper.clearNodes(stack, player, level);
        }
        return InteractionResultHolder.pass(stack);
    }

    /**
     * 扳手模式门控：只在 WRENCH 模式放行 wrench_ 类 ItemAbility。
     */
    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility action) {
        if (getSettings(stack).mode() != ToolMode.WRENCH && action.name().startsWith("wrench_")) {
            return false;
        }
        return super.canPerformAction(stack, action);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player == null) return InteractionResult.PASS;
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            LinkOperationHelper.validateStoredNodes(stack, serverLevel);
        }
        ToolSettings settings = getSettings(stack);

        if (!player.isSecondaryUseActive()) {
            if (level.isClientSide) LinkConfiguratorClientHooks.openScreen(stack);
            return InteractionResult.SUCCESS;
        }

        ModeHandler handler = HANDLERS.get(settings.mode());
        if (handler == null) return InteractionResult.PASS;

        InteractionResult result = handler.handle(this, context, stack, settings);
        if (result == InteractionResult.PASS && settings.mode() != ToolMode.WRENCH) {
            return InteractionResult.SUCCESS;
        }
        return result;
    }
}
