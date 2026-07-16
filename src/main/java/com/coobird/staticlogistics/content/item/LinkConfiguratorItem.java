package com.coobird.staticlogistics.content.item;

import PortLib.extensions.net.minecraft.world.item.ItemStack.PortItemStackExtension;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.content.SLKeyNames;
import com.coobird.staticlogistics.logistics.NodeConfiguratorTool;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.transfer.LogisticsResource;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import com.coobird.staticlogistics.transfer.TransferTypeSelection;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ToolAction;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class LinkConfiguratorItem extends Item implements NodeConfiguratorTool {
    private static final Map<ToolMode, ModeHandler> HANDLERS = new EnumMap<>(ToolMode.class);
    private static final UUID BASE_ATTACK_DAMAGE_UUID = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
    private static final UUID BASE_ATTACK_SPEED_UUID = UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA3");

    static {
        HANDLERS.put(ToolMode.WRENCH, new WrenchModeHandler());
        HANDLERS.put(ToolMode.NODE_CONFIG, new NodeConfigModeHandler());
        HANDLERS.put(ToolMode.REMOVE, new RemoveModeHandler());
        HANDLERS.put(ToolMode.LINK_AS_INSERT, new LinkModeHandler());
        HANDLERS.put(ToolMode.LINK_AS_EXTRACT, new LinkModeHandler());
    }

    public LinkConfiguratorItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot == EquipmentSlot.MAINHAND) {
            ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
            builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", 6.0, AttributeModifier.Operation.ADDITION));
            builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", -2.4, AttributeModifier.Operation.ADDITION));
            return builder.build();
        }
        return super.getAttributeModifiers(slot, stack);
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
        try {
            Integer sModeIdx = PortItemStackExtension.getData(stack, SLDataComponents.STORED_MODE.get());
            List<LogisticsNode> storedNodes = PortItemStackExtension.getDataOrDefault(stack, SLDataComponents.STORED_NODES.get(), List.of());
            List<ResourceLocation> selectedTypeIds = PortItemStackExtension.getData(stack, SLDataComponents.SELECTED_TYPES.get());
            int legacyMask = PortItemStackExtension.getDataOrDefault(
                stack, SLDataComponents.SELECTED_TYPES_MASK.get(), 0);
            selectedTypeIds = TransferTypeSelection.mergeIdsWithMask(
                selectedTypeIds == null ? List.of() : selectedTypeIds,
                legacyMask, TransferRegistries.getAllActive());
            return new ToolSettings(
                ToolMode.fromId(PortItemStackExtension.getDataOrDefault(stack, SLDataComponents.TOOL_MODE.get(), 0)),
                selectedTypeIds,
                PortItemStackExtension.getDataOrDefault(stack, SLDataComponents.SELECTED_GROUP.get(), ""),
                PortItemStackExtension.getData(stack, SLDataComponents.SELECTED_GROUP_KEY.get()),
                storedNodes,
                sModeIdx != null ? ToolMode.fromId(sModeIdx) : null
            );
        } catch (Exception e) {
            // 注册表对象在早期加载阶段可能尚未就绪，例如创建创造模式搜索树时。
            return new ToolSettings(ToolMode.WRENCH, List.of(), "", null, List.of(), null);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
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
        super.appendHoverText(stack, level, tooltip, flag);
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

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction action) {
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
