package com.coobird.staticlogistics.item;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.core.service.GroupService;
import com.coobird.staticlogistics.core.service.LinkValidator;
import com.coobird.staticlogistics.gui.menu.ContainerConfiguratorMenu;
import com.coobird.staticlogistics.gui.menu.FaceConfiguratorMenu;
import com.coobird.staticlogistics.gui.screen.LinkConfiguratorScreen;
import com.coobird.staticlogistics.item.util.ToolMode;
import com.coobird.staticlogistics.registry.SLDataComponents;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.ContainerConfig;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.storage.config.LinkConfig;
import com.coobird.staticlogistics.transfer.handler.TransferUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
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
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LinkConfiguratorItem extends Item {
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
                               @Nullable ToolMode storedMode, int configIndex) {
        public List<TransferType> getSelectedTypes() {
            return TransferRegistries.getAllActive().stream().filter(type -> (typeMask & type.getFlag()) != 0).collect(Collectors.toList());
        }
    }

    public ToolSettings getSettings(ItemStack stack) {
        Integer sModeIdx = stack.get(SLDataComponents.STORED_MODE.get());
        return new ToolSettings(
            ToolMode.fromId(stack.getOrDefault(SLDataComponents.TOOL_MODE.get(), 0)),
            stack.getOrDefault(SLDataComponents.SELECTED_TYPES_MASK.get(), TransferRegistries.ITEM.getFlag()),
            stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), ""),
            stack.getOrDefault(SLDataComponents.STORED_NODES.get(), List.of()),
            sModeIdx != null ? ToolMode.fromId(sModeIdx) : null,
            stack.getOrDefault(SLDataComponents.PRIORITY.get(), 0)
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
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive()) {
            if (level.isClientSide) openLinkerScreenClient(stack);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        } else {
            clearNodes(stack, player, level);
        }
        return InteractionResultHolder.pass(stack);
    }

    @OnlyIn(Dist.CLIENT)
    private void openLinkerScreenClient(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) mc.setScreen(new LinkConfiguratorScreen(stack));
    }

    private void openRemoteNodeGui(ServerPlayer player, LogisticsNode node, ToolSettings settings) {
        ServerLevel targetLevel = player.server.getLevel(node.gPos().dimension());
        if (targetLevel == null) return;
        BlockPos pos = node.gPos().pos();
        Direction face = node.face();
        BlockState state = targetLevel.getBlockState(pos);
        MutableComponent title = state.getBlock().getName().copy().append(Component.literal(String.format(" [%d, %d, %d]", pos.getX(), pos.getY(), pos.getZ())).withStyle(ChatFormatting.GRAY));
        List<TransferType> selected = settings.getSelectedTypes();
        TransferType firstType = selected.isEmpty() ? TransferRegistries.ITEM : selected.get(0);
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new FaceConfiguratorMenu(id, inv, pos, face, firstType), title), buf -> {
            buf.writeBlockPos(pos);
            buf.writeEnum(face);
            buf.writeResourceLocation(firstType.id());
        });
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player == null) return InteractionResult.PASS;
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) validateStoredNodes(stack, serverLevel);
        BlockPos pos = context.getClickedPos();
        Direction face = context.getClickedFace();
        ToolSettings settings = getSettings(stack);

        if (!player.isSecondaryUseActive()) return InteractionResult.PASS;

        if (settings.mode == ToolMode.FACE_CONFIG) {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
                LinkManager mgr = LinkManager.get(serverLevel);
                FaceConfigComposite config = mgr.getFaceConfig(LinkManager.posToKey(pos, face));
                if (config != null) {
                    if (GroupService.canAccess(config.faceConfig.getOwner(), player)) {
                        openRemoteNodeGui(serverPlayer, new LogisticsNode(GlobalPos.of(level.dimension(), pos), face), settings);
                    } else {
                        player.displayClientMessage(Component.translatable("msg.staticlogistics.no_permission"), true);
                    }
                } else player.displayClientMessage(Component.translatable("msg.staticlogistics.no_face_config"), true);
            }
            return InteractionResult.SUCCESS;
        }

        if (settings.mode == ToolMode.CONTAINER_CONFIG) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
                if (!TransferUtils.hasLogisticsCapability(level, pos, null)) {
                    player.displayClientMessage(Component.translatable("msg.staticlogistics.no_capability"), true);
                    return InteractionResult.SUCCESS;
                }
                BlockState state = level.getBlockState(pos);
                MutableComponent title = state.getBlock().getName().copy();
                serverPlayer.openMenu(new SimpleMenuProvider((id, inv, p) -> new ContainerConfiguratorMenu(id, inv, pos), title), buf -> buf.writeBlockPos(pos));
            }
            return InteractionResult.SUCCESS;
        }

        if (settings.mode == ToolMode.REMOVE) {
            if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
                LinkManager mgr = LinkManager.get(serverLevel);
                long key = LinkManager.posToKey(pos, face);
                FaceConfigComposite config = mgr.getFaceConfig(key);

                if (config != null) {
                    if (GroupService.canModify(config.faceConfig.getOwner(), player)) {
                        mgr.removeFaceConfig(key);

                        level.playSound(null, pos, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 0.5f, 0.8f);
                        player.displayClientMessage(Component.translatable("msg.staticlogistics.links_removed_smart"), true);
                    } else {
                        player.displayClientMessage(Component.translatable("msg.staticlogistics.no_permission_to_remove"), true);
                    }
                } else {
                    player.displayClientMessage(Component.translatable("msg.staticlogistics.no_links_on_face", face.getName()), true);
                }
            }
            return InteractionResult.SUCCESS;
        }

        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            if (TransferUtils.hasLogisticsCapability(level, pos, face)) {
                if (settings.storedMode() != null && settings.storedMode() != settings.mode()) {
                    String targetGroup = settings.group();
                    if (targetGroup.isEmpty()) {
                        LinkManager.get(serverLevel);
                        FaceConfigComposite existing = LinkManager.get(serverLevel).getFaceConfig(LinkManager.posToKey(pos, face));
                        if (existing != null && existing.faceConfig.hasGroup()) {
                            targetGroup = existing.faceConfig.getGroupId();
                        } else {
                            targetGroup = GroupService.getNextGroupId("1", GlobalLogisticsManager.get(serverLevel.getServer()).getActiveGroups());
                        }
                    }
                    executeBatchLink(stack, targetGroup, settings, pos, face, serverLevel, player);
                } else {
                    LinkManager.get(serverLevel);
                    FaceConfigComposite config = LinkManager.get(serverLevel).getFaceConfig(LinkManager.posToKey(pos, face));
                    if (config == null || GroupService.canAccess(config.faceConfig.getOwner(), player)) {
                        addNode(stack, GlobalPos.of(level.dimension(), pos), face, settings.mode(), player, level);
                    } else {
                        player.displayClientMessage(Component.translatable("msg.staticlogistics.no_permission"), true);
                    }
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    private void validateStoredNodes(ItemStack stack, ServerLevel level) {
        List<LogisticsNode> storedNodes = stack.get(SLDataComponents.STORED_NODES.get());
        if (storedNodes == null || storedNodes.isEmpty()) return;
        List<LogisticsNode> validNodes = storedNodes.stream().filter(node -> {
            ServerLevel nodeLevel = level.getServer().getLevel(node.gPos().dimension());
            return nodeLevel != null && TransferUtils.hasLogisticsCapability(nodeLevel, node.gPos().pos(), node.face());
        }).toList();
        if (validNodes.size() != storedNodes.size()) {
            stack.set(SLDataComponents.STORED_NODES.get(), validNodes);
            if (validNodes.isEmpty()) stack.remove(SLDataComponents.STORED_MODE.get());
        }
    }

    private void addNode(ItemStack stack, GlobalPos gpos, Direction face, ToolMode mode, Player player, Level level) {
        if (!mode.isLinkMode()) return;
        List<LogisticsNode> nodes = new ArrayList<>(stack.getOrDefault(SLDataComponents.STORED_NODES.get(), List.of()));
        LogisticsNode newNode = new LogisticsNode(gpos, face);
        if (nodes.contains(newNode)) {
            nodes.remove(newNode);
            player.displayClientMessage(Component.translatable("msg.staticlogistics.node_removed", nodes.size()).withStyle(ChatFormatting.RED), true);
            if (nodes.isEmpty()) stack.remove(SLDataComponents.STORED_MODE.get());
        } else {
            nodes.add(newNode);
            stack.set(SLDataComponents.STORED_MODE.get(), mode.getId());
            player.displayClientMessage(Component.translatable("msg.staticlogistics.node_added", nodes.size()).withStyle(ChatFormatting.GREEN), true);
        }
        stack.set(SLDataComponents.STORED_NODES.get(), nodes);
        level.playSound(null, gpos.pos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.8f, 1.5f);
    }

    private void clearNodes(ItemStack stack, Player player, Level level) {
        List<LogisticsNode> nodes = stack.get(SLDataComponents.STORED_NODES.get());
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        stack.remove(SLDataComponents.STORED_NODES.get());
        stack.remove(SLDataComponents.STORED_MODE.get());
        stack.remove(SLDataComponents.PRIORITY.get());
        player.displayClientMessage(Component.translatable("msg.staticlogistics.selection_cleared").withStyle(ChatFormatting.YELLOW), true);
        level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 0.5f);
    }

    private void executeBatchLink(ItemStack stack, String groupId, ToolSettings settings, BlockPos pos, Direction face, ServerLevel level, Player player) {
        List<LogisticsNode> targets = settings.storedNodes().stream().filter(n -> !n.isAt(level.dimension(), pos, face)).toList();
        if (targets.isEmpty()) {
            player.displayClientMessage(Component.translatable("msg.staticlogistics.self_link_error").withStyle(ChatFormatting.RED), true);
            return;
        }

        LinkManager targetMgr = LinkManager.get(level);
        ContainerConfig containerCfg = targetMgr.getOrCreateContainerConfig(pos);
        LogisticsNode currentNode = new LogisticsNode(GlobalPos.of(level.dimension(), pos), face);
        int linkedCount = 0;

        for (LogisticsNode srcNode : targets) {
            ServerLevel srcLevel = level.getServer().getLevel(srcNode.gPos().dimension());
            if (srcLevel == null) continue;

            LinkManager.get(srcLevel);
            FaceConfigComposite srcCfg = LinkManager.get(srcLevel).getFaceConfig(LinkManager.posToKey(srcNode.gPos().pos(), srcNode.face()));
            if (srcCfg != null && !GroupService.canAccess(srcCfg.faceConfig.getOwner(), player)) continue;

            LinkValidator.Result validation = LinkValidator.canLink(srcNode, currentNode, containerCfg);
            if (!validation.success() && validation.error() != null) {
                player.displayClientMessage(validation.error().copy().withStyle(ChatFormatting.RED), true);
                continue;
            }

            if (performSingleLink(level, currentNode, srcNode, groupId, settings, player)) linkedCount++;
        }

        if (linkedCount > 0) {
            if (stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "").isEmpty())
                stack.set(SLDataComponents.SELECTED_GROUP.get(), groupId);
            player.displayClientMessage(Component.translatable("msg.staticlogistics.batch_linked_to_group", linkedCount, groupId).withStyle(ChatFormatting.AQUA), true);
            level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
    }

    private boolean performSingleLink(ServerLevel level, LogisticsNode current, LogisticsNode stored, String groupId, ToolSettings settings, Player player) {
        LinkManager currentMgr = LinkManager.get(level);
        FaceConfigComposite currentCfg = currentMgr.getOrCreateFaceConfig(current.gPos().pos(), current.face());
        currentCfg.faceConfig.setGroupId(groupId);
        currentCfg.faceConfig.setOwner(player.getUUID(), player.getGameProfile().getName());
        currentCfg.setSelectedTypesMask(settings.typeMask());

        ServerLevel storedLevel = level.getServer().getLevel(stored.gPos().dimension());
        if (storedLevel == null) return false;

        LinkManager storedMgr = LinkManager.get(storedLevel);
        FaceConfigComposite storedCfg = storedMgr.getOrCreateFaceConfig(stored.gPos().pos(), stored.face());
        storedCfg.faceConfig.setGroupId(groupId);
        storedCfg.faceConfig.setOwner(player.getUUID(), player.getGameProfile().getName());
        storedCfg.setSelectedTypesMask(settings.typeMask());

        for (TransferType type : settings.getSelectedTypes()) {
            LinkConfig.SideData currentData = currentCfg.linkConfig.getSettings(type);
            LinkConfig.SideData storedData = storedCfg.linkConfig.getSettings(type);

            currentData.linkedInputs.add(stored);
            storedData.linkedInputs.add(current);

            if (settings.storedMode() == ToolMode.LINK_AS_INPUT) {
                currentData.outputEnabled = true;
                storedData.inputEnabled = true;
            } else if (settings.storedMode() == ToolMode.LINK_AS_OUTPUT) {
                storedData.outputEnabled = true;
                currentData.inputEnabled = true;
            }
        }

        GlobalLogisticsManager.get(level.getServer()).registerNode(groupId, stored, storedCfg.determineRole());
        GlobalLogisticsManager.get(level.getServer()).registerNode(groupId, current, currentCfg.determineRole());

        currentCfg.markDirty();
        storedCfg.markDirty();

        currentMgr.syncConfigToClients(current.gPos().pos());
        storedMgr.syncConfigToClients(stored.gPos().pos());

        currentMgr.activateNode(current.toKey(), current.gPos().pos(), current.face(), currentCfg);
        storedMgr.activateNode(stored.toKey(), stored.gPos().pos(), stored.face(), storedCfg);

        return true;
    }
}