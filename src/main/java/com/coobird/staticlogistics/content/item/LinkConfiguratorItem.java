package com.coobird.staticlogistics.content.item;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.content.SLKeyNames;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.LinkConfiguratorTool;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.group.GroupSelectionInvalidator;
import com.coobird.staticlogistics.logistics.node.EndpointAddress;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import com.coobird.staticlogistics.transfer.LogisticsResource;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import com.coobird.staticlogistics.transfer.TransferTypeSelection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
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

import java.util.*;

public class LinkConfiguratorItem extends Item implements LinkConfiguratorTool {
    private final Map<ItemStack, UUID> toolSessions = Collections.synchronizedMap(new WeakHashMap<>());

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide) {
            UUID identity = toolSessions.computeIfAbsent(stack, ignored -> UUID.randomUUID());
            if (!identity.equals(stack.get(SLDataComponents.TOOL_SESSION.get()))) {
                stack.set(SLDataComponents.TOOL_SESSION.get(), identity);
            }
        }
    }

    public boolean matchesSession(ItemStack stack, UUID identity) {
        return identity.equals(toolSessions.get(stack));
    }

    private static final Map<ToolMode, ModeHandler> HANDLERS = new EnumMap<>(ToolMode.class);

    static {
        HANDLERS.put(ToolMode.WRENCH, new WrenchModeHandler());
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
                               @Nullable ToolMode storedMode, int channel) {
        public List<LogisticsResource<?>> getSelectedTypes() {
            return TransferTypeSelection.selectedTypes(selectedTypeIds, TransferRegistries.getAllActive());
        }

    }

    public ToolSettings getSettings(ItemStack stack) {
        Integer sModeIdx = stack.get(SLDataComponents.STORED_MODE.get());
        List<ResourceLocation> selectedTypeIds = stack.get(SLDataComponents.SELECTED_TYPES.get());
        selectedTypeIds = selectedTypeIds == null ? List.of() : selectedTypeIds;
        return new ToolSettings(
            ToolMode.fromId(stack.getOrDefault(SLDataComponents.TOOL_MODE.get(), 0)),
            selectedTypeIds,
            stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), ""),
            stack.get(SLDataComponents.SELECTED_GROUP_KEY.get()),
            storedNodes(stack),
            sModeIdx != null ? ToolMode.fromId(sModeIdx) : null, selectedChannel(stack)
        );
    }

    public static int selectedChannel(ItemStack stack) {
        return Math.clamp(stack.getOrDefault(SLDataComponents.SELECTED_CHANNEL.get(), 0), 0, LogisticsNode.MAX_CHANNELS - 1);
    }

    public static int channelForFace(ServerLevel level, BlockPos pos, Direction face, int preferred) {
        var manager = LinkManager.get(level);
        if (manager.getEndpointConfig(new EndpointAddress(pos.asLong(), face, preferred)) != null) return preferred;
        var container = manager.getContainerConfig(pos);
        if (container == null) return 0;
        return container.getLinkedFaceKeys().stream().filter(address -> address.face() == face && address.channel() >= 0)
            .mapToInt(EndpointAddress::channel).min().orElse(0);
    }

    private static List<LogisticsNode> storedNodes(ItemStack stack) {
        List<LogisticsNode> nodes = stack.getOrDefault(SLDataComponents.STORED_NODES.get(), List.of());
        return nodes.isEmpty() ? LinkConfiguratorClientHooks.getStoredNodes(stack) : nodes;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ToolSettings settings = getSettings(stack);
        if (BlueprintModeHandler.isActive(stack)) {
            BlueprintModeHandler.appendHoverText(stack, context, tooltip, flag);
            return;
        }
        tooltip.add(Component.translatable("tooltip.staticlogistics.configurator_hint").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("tooltip.staticlogistics.mode_scroll",
            Component.keybind("key.sneak")).withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("tooltip.staticlogistics.direct_use_hint").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.staticlogistics.bulk_select_hint",
            Component.keybind(SLKeyNames.BULK_NODE_SELECTION)).withStyle(ChatFormatting.AQUA));
        if (!settings.group().isEmpty()) {
            tooltip.add(Component.translatable("tooltip.staticlogistics.group", settings.group()).withStyle(ChatFormatting.GRAY));
        }
        if (!settings.storedNodes().isEmpty()) {
            tooltip.add(Component.translatable("tooltip.staticlogistics.stored_faces",
                settings.storedNodes().size()).withStyle(ChatFormatting.WHITE));
            tooltip.add(Component.translatable("tooltip.staticlogistics.clear_stored_hint",
                Component.keybind(SLKeyNames.CLEAR_STORED_NODES)).withStyle(ChatFormatting.GRAY));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (BlueprintModeHandler.isPlacing(stack)) return BlueprintModeHandler.use(level, player, hand);

        if (!player.isSecondaryUseActive()) {
            if (player instanceof ServerPlayer serverPlayer) {
                openConfigurator(serverPlayer);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        } else {
            LinkOperationHelper.clearNodes(stack, player, level);
        }
        return InteractionResultHolder.pass(stack);
    }

    /// 扳手模式门控：只在 WRENCH 模式放行 wrench_ 类 ItemAbility。
    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility action) {
        if ((BlueprintModeHandler.isActive(stack) || getSettings(stack).mode() != ToolMode.WRENCH) && action.name().startsWith("wrench_")) {
            return false;
        }
        return super.canPerformAction(stack, action);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && BlueprintModeHandler.isActive(stack)
            && !BlueprintModeHandler.isPlacing(stack) && !player.isSecondaryUseActive()) {
            if (player instanceof ServerPlayer serverPlayer) openConfigurator(serverPlayer);
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        if (player == null || player.isSecondaryUseActive()
            || getSettings(stack).mode() != ToolMode.WRENCH) return InteractionResult.PASS;
        /// 普通右键先执行旋转，不让箱子或机器的交互提前打开界面；蹲下拆卸仍交给原有兼容入口。
        InteractionResult result = useOn(context);
        if (result != InteractionResult.PASS) return result;
        if (!context.getLevel().isClientSide) {
            player.displayClientMessage(Component.translatable("msg.staticlogistics.wrench.cannot_rotate"), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player == null) return InteractionResult.PASS;
        if (BlueprintModeHandler.isActive(stack)) {
            if (BlueprintModeHandler.isPlacing(stack) || player.isSecondaryUseActive())
                return BlueprintModeHandler.useOn(context);
            if (player instanceof ServerPlayer serverPlayer) openConfigurator(serverPlayer);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            LinkOperationHelper.validateStoredNodes(stack, serverLevel);
            GroupSelectionInvalidator.clearInvalidSelection(serverLevel.getServer(), stack);
        }
        ToolSettings settings = getSettings(stack);

        if (level.isClientSide && LinkConfiguratorClientHooks.isBulkSelectionActive()
            && settings.mode().isLinkMode()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer
            && BulkSelectionInteractionGuard.matches(
            serverPlayer, context.getClickedPos(), context.getClickedFace())) {
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

    private static void openConfigurator(ServerPlayer player) {
        int toolSlot = LinkConfiguratorMenu.findToolSlot(player.getInventory());
        if (toolSlot < 0) return;
        GroupSelectionInvalidator.clearInvalidSelection(
            player.server, player.getInventory().getItem(toolSlot));
        Component title = Component.translatable("gui.staticlogistics.linker_settings");
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, ignored) ->
                    new LinkConfiguratorMenu(id, inventory, toolSlot),
                title),
            buffer -> LinkConfiguratorMenu.writeEmptyOpenData(buffer, toolSlot));
    }
}
