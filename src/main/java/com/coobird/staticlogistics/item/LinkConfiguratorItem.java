package com.coobird.staticlogistics.item;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.gui.screen.LinkConfiguratorScreen;
import com.coobird.staticlogistics.item.handler.*;
import com.coobird.staticlogistics.item.util.LinkOperationHelper;
import com.coobird.staticlogistics.item.util.ToolMode;
import com.coobird.staticlogistics.registry.SLDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LinkConfiguratorItem extends Item {
    private static final Map<ToolMode, ModeHandler> HANDLERS = new EnumMap<>(ToolMode.class);

    static {
        HANDLERS.put(ToolMode.WRENCH, new WrenchModeHandler());
        HANDLERS.put(ToolMode.FACE_CONFIG, new FaceConfigModeHandler());
        HANDLERS.put(ToolMode.CONTAINER_CONFIG, new ContainerConfigModeHandler());
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

    public record ToolSettings(ToolMode mode, int typeMask, String group, List<LogisticsNode> storedNodes,
                               @Nullable ToolMode storedMode) {
        public List<TransferType> getSelectedTypes() {
            return TransferRegistries.getAllActive().stream().filter(type -> (typeMask & type.getFlag()) != 0).collect(Collectors.toList());
        }
    }

    public ToolSettings getSettings(ItemStack stack) {
        Integer sModeIdx = stack.get(SLDataComponents.STORED_MODE.get());
        return new ToolSettings(
            ToolMode.fromId(stack.getOrDefault(SLDataComponents.TOOL_MODE.get(), 0)),
            stack.getOrDefault(SLDataComponents.SELECTED_TYPES_MASK.get(), 0),
            stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), ""),
            stack.getOrDefault(SLDataComponents.STORED_NODES.get(), List.of()),
            sModeIdx != null ? ToolMode.fromId(sModeIdx) : null
        );
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ToolSettings settings = getSettings(stack);
        tooltip.add(Component.translatable("tooltip.staticlogistics.mode", settings.mode().getDisplayName()));
        String types = settings.getSelectedTypes().stream().map(t -> Component.translatable(t.translationKey()).getString()).collect(Collectors.joining(", "));
        tooltip.add(Component.translatable("tooltip.staticlogistics.type", types.isEmpty() ? Component.translatable("tooltip.staticlogistics.none") : Component.literal(types)));
        tooltip.add(Component.translatable("tooltip.staticlogistics.group", settings.group().isEmpty() ? Component.translatable("tooltip.staticlogistics.none") : Component.literal(settings.group())));
        if (!settings.storedNodes().isEmpty() && settings.storedMode() != null) {
            String nodesInfo = settings.storedNodes().stream().map(n -> n.gPos().pos().toShortString() + " " + n.face()).collect(Collectors.joining(", "));
            tooltip.add(Component.translatable("tooltip.staticlogistics.stored_nodes", nodesInfo, settings.storedMode().getDisplayName()));
        }
        tooltip.add(Component.translatable("tooltip.staticlogistics.clear_stored_hint").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive()) {
            if (level.isClientSide) openLinkerScreenClient(stack);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        } else {
            LinkOperationHelper.clearNodes(stack, player, level);
        }
        return InteractionResultHolder.pass(stack);
    }

    @OnlyIn(Dist.CLIENT)
    private void openLinkerScreenClient(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) mc.setScreen(new LinkConfiguratorScreen(stack));
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

        if (!player.isSecondaryUseActive()) return InteractionResult.PASS;

        ModeHandler handler = HANDLERS.get(settings.mode());
        if (handler != null) {
            return handler.handle(this, context, stack, settings);
        }
        return InteractionResult.PASS;
    }
}