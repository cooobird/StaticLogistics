package com.coobird.staticlogistics.content.menu;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.ExtractionMode;
import com.coobird.staticlogistics.content.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.content.item.UpgradeItem;
import com.coobird.staticlogistics.content.registry.SLMenuTypes;
import com.coobird.staticlogistics.logistics.node.*;
import com.coobird.staticlogistics.transfer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class LinkConfiguratorMenu extends AbstractContainerMenu {
    private static final int FILTER_SLOTS = 2;
    private static final int UPGRADE_SLOTS = ContainerConfig.UPGRADE_SLOT_COUNT;
    private static final int TOTAL_CONFIG_SLOTS = FILTER_SLOTS + UPGRADE_SLOTS;
    private static final int INV_SLOT_START = TOTAL_CONFIG_SLOTS;
    private static final int INV_SLOT_END = INV_SLOT_START + 27;
    private static final int HOTBAR_SLOT_START = INV_SLOT_END;
    private static final int HOTBAR_SLOT_END = HOTBAR_SLOT_START + 9;

    public static final int INPUT_FILTER_X = MenuLayout.NODE_FILTER_X;
    public static final int INPUT_FILTER_Y = MenuLayout.NODE_SLOT_Y;
    public static final int OUTPUT_FILTER_X = MenuLayout.NODE_FILTER_X;
    public static final int OUTPUT_FILTER_Y = MenuLayout.NODE_SLOT_Y;
    public static final int SPEED_UPGRADE_X = MenuLayout.NODE_SPEED_UPGRADE_X;
    public static final int SPEED_UPGRADE_Y = MenuLayout.NODE_SLOT_Y;
    public static final int RANGE_UPGRADE_X = MenuLayout.NODE_RANGE_UPGRADE_X;
    public static final int RANGE_UPGRADE_Y = MenuLayout.NODE_SLOT_Y;
    public static final int STACK_UPGRADE_X = MenuLayout.NODE_STACK_UPGRADE_X;
    public static final int STACK_UPGRADE_Y = MenuLayout.NODE_SLOT_Y;

    private BlockPos pos;
    private Direction face;
    private ResourceKey<Level> targetDimension;
    private GroupKey groupKey;
    private boolean hasTarget;
    private final int toolSlot;
    private final Player player;
    private final SwitchingItemHandler filterHandler = new SwitchingItemHandler(FILTER_SLOTS);
    private final SwitchingItemHandler containerHandler = new SwitchingItemHandler(UPGRADE_SLOTS);
    private FaceConfigComposite faceConfig;
    private ContainerConfig containerConfig;
    private List<ResourceLocation> selectedTypeIds = List.of();

    private final DataSlot globalInputSlot = DataSlot.standalone();
    private final DataSlot globalOutputSlot = DataSlot.standalone();
    private final DataSlot strategySlot = DataSlot.standalone();
    private final DataSlot extractionModeSlot = DataSlot.standalone();
    private final DataSlot prioritySlot = DataSlot.standalone();
    private final DataSlot keepStockSlot = DataSlot.standalone();
    public final DataSlot selectedTypesMaskSlot = DataSlot.standalone();

    private final DataSlot speedMultSlot = DataSlot.standalone();
    private final DataSlot rangeMultSlot = DataSlot.standalone();
    private final DataSlot stackMultSlot = DataSlot.standalone();
    private final DataSlot dimensionSlot = DataSlot.standalone();
    private final DataSlot visibleSideSlot = DataSlot.standalone();

    private final ItemStack[] lastUpgradeStacks = new ItemStack[UPGRADE_SLOTS];

    public LinkConfiguratorMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, readOpenData(buf));
    }

    public LinkConfiguratorMenu(int containerId, Inventory playerInventory, LogisticsNode node, GroupKey groupKey, boolean inputSide) {
        this(containerId, playerInventory, node.gPos().pos(), node.face(),
            node.gPos().dimension(), groupKey, inputSide, List.of(),
            findToolSlot(playerInventory));
    }

    public LinkConfiguratorMenu(int containerId, Inventory playerInventory, int toolSlot) {
        this(containerId, playerInventory, null, null, null, null,
            true, List.of(), toolSlot);
    }

    private LinkConfiguratorMenu(int containerId, Inventory playerInventory, OpenData data) {
        this(containerId, playerInventory, data.pos(), data.face(), data.dimension(),
            data.groupKey(), data.inputSide(), data.initialTypeIds(), data.toolSlot());
    }

    private LinkConfiguratorMenu(
        int containerId,
        Inventory playerInventory,
        BlockPos pos,
        Direction face,
        ResourceKey<Level> targetDimension,
        GroupKey groupKey,
        boolean inputSide,
        List<ResourceLocation> initialTypeIds,
        int toolSlot
    ) {
        super(SLMenuTypes.LINK_CONFIGURATOR_MENU.get(), containerId);
        this.pos = pos;
        this.face = face;
        this.targetDimension = targetDimension;
        this.groupKey = groupKey;
        this.hasTarget = pos != null && face != null
            && targetDimension != null && groupKey != null;
        this.toolSlot = toolSlot;
        this.player = playerInventory.player;
        this.selectedTypeIds = initialTypeIds;

        this.addDataSlot(globalInputSlot);
        this.addDataSlot(globalOutputSlot);
        this.addDataSlot(strategySlot);
        this.addDataSlot(extractionModeSlot);
        this.addDataSlot(prioritySlot);
        this.addDataSlot(keepStockSlot);
        this.addDataSlot(selectedTypesMaskSlot);

        addDataSlot(speedMultSlot);
        addDataSlot(rangeMultSlot);
        addDataSlot(stackMultSlot);
        addDataSlot(dimensionSlot);
        visibleSideSlot.set(inputSide ? 1 : 0);
        addDataSlot(visibleSideSlot);

        Arrays.fill(lastUpgradeStacks, ItemStack.EMPTY);

        ServerLevel serverLevel = !hasTarget || player.getServer() == null
            ? null : player.getServer().getLevel(targetDimension);
        if (serverLevel != null && pos != null && face != null) {
            LinkManager mgr = LinkManager.get(serverLevel);
            if (mgr != null) {
                this.faceConfig = mgr.getFaceConfig(FaceAddress.of(pos, face));
                this.containerConfig = mgr.getOrCreateContainerConfig(pos);
                filterHandler.bind(faceConfig == null
                    ? new ItemStackHandler(FILTER_SLOTS)
                    : faceConfig.filterConfig.getUpgrades());
                containerHandler.bind(containerConfig.getUpgrades());
                syncFaceSlots();
                syncContainerSlots();
                cacheUpgradeStacks();
            }
        }

        this.addSlot(new FilterSlot(filterHandler, 0, INPUT_FILTER_X, INPUT_FILTER_Y, true,
            UpgradeType.BASIC_FILTER, UpgradeType.TAG_FILTER, UpgradeType.NBT_FILTER));
        this.addSlot(new FilterSlot(filterHandler, 1, OUTPUT_FILTER_X, OUTPUT_FILTER_Y, false,
            UpgradeType.BASIC_FILTER, UpgradeType.TAG_FILTER, UpgradeType.NBT_FILTER));
        this.addSlot(new UpgradeSlot(containerHandler, ContainerConfig.SPEED_SLOT,
            SPEED_UPGRADE_X, SPEED_UPGRADE_Y, UpgradeType.SPEED));
        this.addSlot(new UpgradeSlot(containerHandler, ContainerConfig.RANGE_OR_DIMENSION_SLOT,
            RANGE_UPGRADE_X, RANGE_UPGRADE_Y, UpgradeType.RANGE, UpgradeType.DIMENSION));
        this.addSlot(new UpgradeSlot(containerHandler, ContainerConfig.STACK_SLOT,
            STACK_UPGRADE_X, STACK_UPGRADE_Y, UpgradeType.STACK));

        addPlayerInventory(playerInventory);
    }

    private static List<ResourceLocation> readInitialTypeIds(FriendlyByteBuf buf) {
        if (buf.readableBytes() <= 0) return List.of();
        int size = buf.readVarInt();
        List<ResourceLocation> ids = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ids.add(buf.readResourceLocation());
        }
        return TransferTypeSelection.sanitize(ids);
    }

    private static OpenData readOpenData(FriendlyByteBuf buf) {
        int toolSlot = buf.readVarInt();
        if (!buf.readBoolean()) {
            return new OpenData(null, null, null, null, true, List.of(), toolSlot);
        }
        BlockPos pos = buf.readBlockPos();
        Direction face = buf.readEnum(Direction.class);
        List<ResourceLocation> typeIds = readInitialTypeIds(buf);
        ResourceKey<Level> dimension = ResourceKey.create(
            Registries.DIMENSION, buf.readResourceLocation());
        GroupKey groupKey = new GroupKey(buf.readUUID(), buf.readUUID());
        boolean inputSide = buf.readBoolean();
        return new OpenData(pos, face, dimension, groupKey, inputSide, typeIds, toolSlot);
    }

    public static void writeEmptyOpenData(FriendlyByteBuf buf, int toolSlot) {
        buf.writeVarInt(toolSlot);
        buf.writeBoolean(false);
    }

    public static void writeTargetOpenData(
        FriendlyByteBuf buf,
        LogisticsNode node,
        GroupKey groupKey,
        boolean inputSide,
        FaceConfigComposite config,
        int toolSlot
    ) {
        buf.writeVarInt(toolSlot);
        buf.writeBoolean(true);
        buf.writeBlockPos(node.gPos().pos());
        buf.writeEnum(node.face());
        writeInitialTypeIds(buf, config);
        writeRemoteContext(buf, node, groupKey, inputSide);
    }

    private static void writeInitialTypeIds(FriendlyByteBuf buf, FaceConfigComposite config) {
        List<ResourceLocation> ids = config == null || !config.isGlobalOutputEnabled()
            ? List.of() : config.getSelectedTypeIds();
        buf.writeVarInt(ids.size());
        for (ResourceLocation id : ids) {
            buf.writeResourceLocation(id);
        }
    }

    private static void writeRemoteContext(
        FriendlyByteBuf buf, LogisticsNode node, GroupKey groupKey, boolean inputSide) {
        buf.writeResourceLocation(node.gPos().dimension().location());
        buf.writeUUID(groupKey.ownerId());
        buf.writeUUID(groupKey.internalId());
        buf.writeBoolean(inputSide);
    }

    public boolean isGlobalInputEnabled() {
        return globalInputSlot.get() == 1;
    }

    public boolean isGlobalOutputEnabled() {
        return globalOutputSlot.get() == 1;
    }

    public DistributionStrategy getStrategy() {
        int idx = strategySlot.get();
        var vals = DistributionStrategyRegistry.getValues();
        return idx >= 0 && idx < vals.size() ? vals.get(idx) : DistributionStrategyRegistry.SEQUENTIAL;
    }

    public ExtractionMode getExtractionMode() {
        int idx = extractionModeSlot.get();
        var vals = ExtractionMode.values();
        return idx >= 0 && idx < vals.length ? vals[idx] : ExtractionMode.SEQUENTIAL;
    }

    public int getPriority() {
        return prioritySlot.get();
    }

    public int getKeepStock() {
        return keepStockSlot.get();
    }

    public List<ResourceLocation> getSelectedTypeIds() {
        return selectedTypeIds;
    }

    public int getSpeedMultiplier() {
        return speedMultSlot.get();
    }

    public int getRangeMultiplier() {
        return rangeMultSlot.get();
    }

    public int getStackMultiplier() {
        return stackMultSlot.get();
    }

    public boolean isDimensionEffective() {
        return dimensionSlot.get() == 1;
    }


    public void setGlobalInputEnabled(boolean enabled) {
        if (faceConfig != null && faceConfig.isGlobalInputEnabled() != enabled) {
            faceConfig.setGlobalInputEnabled(enabled);
            syncFaceSlots();
        }
    }

    public void setGlobalOutputEnabled(boolean enabled) {
        if (faceConfig != null && faceConfig.isGlobalOutputEnabled() != enabled) {
            faceConfig.setGlobalOutputEnabled(enabled);
            syncFaceSlots();
        }
    }

    public void setStrategy(DistributionStrategy s) {
        if (faceConfig != null && faceConfig.linkConfig.getStrategy() != s) {
            faceConfig.setDistributionStrategy(s);
            syncFaceSlots();
        }
    }

    public void setExtractionMode(ExtractionMode m) {
        if (faceConfig != null && faceConfig.linkConfig.getExtractionMode() != m) {
            faceConfig.setExtractionMode(m);
            syncFaceSlots();
        }
    }

    public void setPriority(int v) {
        if (faceConfig != null && faceConfig.linkConfig.getPriority() != v) {
            faceConfig.setPriority(v);
            syncFaceSlots();
        }
    }

    public void setKeepStock(int v) {
        if (faceConfig != null && faceConfig.linkConfig.getKeepStock() != v) {
            faceConfig.setKeepStock(v);
            syncFaceSlots();
        }
    }

    public void setSelectedTypeIds(List<ResourceLocation> ids) {
        if (faceConfig != null && !faceConfig.isGlobalOutputEnabled()) return;
        List<ResourceLocation> sanitized = TransferTypeSelection.sanitize(ids);
        selectedTypeIds = sanitized;
        selectedTypesMaskSlot.set(TransferTypeSelection.toMask(sanitized, TransferRegistries.getAllActive()));
        if (faceConfig != null) {
            faceConfig.setSelectedTypeIds(sanitized);
            broadcastChanges();
        }
    }

    // 数据同步。

    public void syncFaceSlots() {
        if (faceConfig == null) return;
        ServerLevel level = player.getServer() == null
            ? null : player.getServer().getLevel(targetDimension);
        if (level != null && pos != null && face != null) {
            NodeQuerySnapshot snapshot = NodeQueryService.query(level, pos, face).orElse(null);
            if (snapshot != null) {
                globalInputSlot.set(snapshot.inputEnabled() ? 1 : 0);
                globalOutputSlot.set(snapshot.outputEnabled() ? 1 : 0);
                strategySlot.set(DistributionStrategyRegistry.getValues().indexOf(
                    DistributionStrategyRegistry.byName(snapshot.strategyId().toString())));
                extractionModeSlot.set(snapshot.extractionMode().ordinal());
                prioritySlot.set(snapshot.priority());
                keepStockSlot.set(snapshot.keepStock());
                selectedTypeIds = snapshot.selectedTypeIds();
                selectedTypesMaskSlot.set(TransferTypeSelection.toMask(
                    selectedTypeIds, TransferRegistries.getAllActive()));
                return;
            }
        }
        syncFaceSlotsFromConfig();
    }

    private void syncFaceSlotsFromConfig() {
        globalInputSlot.set(faceConfig.isGlobalInputEnabled() ? 1 : 0);
        globalOutputSlot.set(faceConfig.isGlobalOutputEnabled() ? 1 : 0);
        strategySlot.set(DistributionStrategyRegistry.getValues().indexOf(faceConfig.linkConfig.getStrategy()));
        extractionModeSlot.set(faceConfig.linkConfig.getExtractionMode().ordinal());
        prioritySlot.set(faceConfig.linkConfig.getPriority());
        keepStockSlot.set(faceConfig.linkConfig.getKeepStock());
        selectedTypeIds = faceConfig.getSelectedTypeIds();
        selectedTypesMaskSlot.set(TransferTypeSelection.toMask(
            selectedTypeIds, TransferRegistries.getAllActive()));
    }

    public void syncContainerSlots() {
        if (containerConfig == null) return;
        speedMultSlot.set((int) Math.min(LogisticsCalculator.getSpeedMultiplier(containerConfig), Integer.MAX_VALUE));
        rangeMultSlot.set((int) Math.min(LogisticsCalculator.getRangeMultiplier(containerConfig), Integer.MAX_VALUE));
        stackMultSlot.set((int) Math.min(LogisticsCalculator.getStackMultiplier(containerConfig), Integer.MAX_VALUE));
        dimensionSlot.set(LogisticsCalculator.isDimensionEffective(containerConfig) ? 1 : 0);
    }

    private void cacheUpgradeStacks() {
        if (containerConfig == null) return;
        for (int i = 0; i < UPGRADE_SLOTS; i++)
            lastUpgradeStacks[i] = containerConfig.getUpgrades().getStackInSlot(i).copy();
    }

    private boolean hasUpgradeStacksChanged() {
        if (containerConfig == null) return false;
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            if (!ItemStack.matches(containerConfig.getUpgrades().getStackInSlot(i), lastUpgradeStacks[i]))
                return true;
        }
        return false;
    }

    @Override
    public void broadcastChanges() {
        if (hasUpgradeStacksChanged()) {
            syncContainerSlots();
            cacheUpgradeStacks();
        }
        super.broadcastChanges();
    }

    // 槽位管理。

    private void addPlayerInventory(Inventory inv) {
        int x = MenuLayout.LINK_INVENTORY_X + MenuLayout.INVENTORY_SLOT_X;
        int ty = MenuLayout.LINK_INVENTORY_Y + MenuLayout.INVENTORY_SLOT_Y;
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                this.addSlot(new Slot(inv, col + row * 9 + 9, x + col * 18, ty + row * 18));
        int hy = MenuLayout.LINK_INVENTORY_Y + MenuLayout.HOTBAR_SLOT_Y;
        for (int col = 0; col < 9; col++)
            this.addSlot(new Slot(inv, col, x + col * 18, hy));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem().copy();
        ItemStack result = stack.copy();

        if (index < TOTAL_CONFIG_SLOTS) {
            if (!moveItemStackTo(stack, INV_SLOT_START, HOTBAR_SLOT_END, true))
                return ItemStack.EMPTY;
        } else {
            if (!(stack.getItem() instanceof UpgradeItem)) return ItemStack.EMPTY;
            boolean moved = false;
            for (int i = 0; i < TOTAL_CONFIG_SLOTS; i++) {
                Slot configSlot = this.slots.get(i);
                if (configSlot.mayPlace(stack) && moveItemStackTo(stack, i, i + 1, false)) {
                    moved = true;
                    break;
                }
            }
            if (!moved) return ItemStack.EMPTY;
        }

        slot.set(stack.isEmpty() ? ItemStack.EMPTY : stack);
        if (stack.getCount() == result.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!(getToolStack().getItem() instanceof LinkConfiguratorItem)) return false;
        if (!hasTarget) return true;
        if (!(player instanceof ServerPlayer serverPlayer)
        ) {
            return player.level().isClientSide;
        }
        return new NodeMutationService()
            .resolveRemote(serverPlayer, getTargetNode(), groupKey) != null;
    }

    public BlockPos getPos() {
        return pos;
    }

    public boolean hasTarget() {
        return hasTarget;
    }

    public ItemStack getToolStack() {
        if (toolSlot < 0 || toolSlot >= player.getInventory().getContainerSize()) {
            return ItemStack.EMPTY;
        }
        return player.getInventory().getItem(toolSlot);
    }

    public Direction getFace() {
        return face;
    }

    public ResourceKey<Level> getTargetDimension() {
        return targetDimension;
    }

    public LogisticsNode getTargetNode() {
        if (!hasTarget) {
            throw new IllegalStateException("No node is selected");
        }
        return new LogisticsNode(GlobalPos.of(targetDimension, pos), face);
    }

    public GroupKey getRemoteGroupKey() {
        return groupKey;
    }

    public boolean isInputSideVisible() {
        return visibleSideSlot.get() == 1;
    }

    public boolean isOutputSideVisible() {
        return !isInputSideVisible();
    }

    /**
     * 判断槽位是否属于过滤器或容器升级配置区。
     */
    public boolean isConfigurationSlot(Slot slot) {
        int index = slots.indexOf(slot);
        return index >= 0 && index < TOTAL_CONFIG_SLOTS;
    }

    /**
     * 客户端立即切换当前节点的显示身份，服务端随后发送权威槽位和数据槽内容。
     * 该操作不会创建新的 Menu，因此不会重置鼠标位置或预览画布状态。
     */
    public void selectTarget(
        LogisticsNode node,
        GroupKey groupKey,
        boolean inputSide,
        List<ResourceLocation> selectedTypeIds
    ) {
        bindTargetIdentity(node, groupKey, inputSide);
        faceConfig = null;
        containerConfig = null;
        filterHandler.clear();
        containerHandler.clear();
        clearSyncedConfiguration();
        this.selectedTypeIds = TransferTypeSelection.sanitize(selectedTypeIds);
        selectedTypesMaskSlot.set(TransferTypeSelection.toMask(
            this.selectedTypeIds, TransferRegistries.getAllActive()));
    }

    /**
     * 服务端在权限验证完成后，把现有 Menu 重新绑定到目标节点。
     */
    public void selectTarget(
        NodeMutationService.ValidatedNode node,
        GroupKey groupKey,
        boolean inputSide
    ) {
        LogisticsNode target = new LogisticsNode(
            GlobalPos.of(node.level().dimension(), node.pos()),
            node.face());
        bindTargetIdentity(target, groupKey, inputSide);
        faceConfig = node.config();
        containerConfig = node.manager().getOrCreateContainerConfig(node.pos());
        filterHandler.bind(faceConfig.filterConfig.getUpgrades());
        containerHandler.bind(containerConfig.getUpgrades());
        syncFaceSlots();
        syncContainerSlots();
        cacheUpgradeStacks();
    }

    /**
     * 解除当前节点绑定，但保留连接配置器本身及玩家物品栏槽位。
     */
    public void clearTarget() {
        pos = null;
        face = null;
        targetDimension = null;
        groupKey = null;
        hasTarget = false;
        faceConfig = null;
        containerConfig = null;
        visibleSideSlot.set(1);
        filterHandler.clear();
        containerHandler.clear();
        clearSyncedConfiguration();
    }

    private void bindTargetIdentity(
        LogisticsNode node,
        GroupKey groupKey,
        boolean inputSide
    ) {
        Objects.requireNonNull(node, "Node must not be null");
        this.groupKey = Objects.requireNonNull(groupKey,
            "Group key must not be null");
        pos = node.gPos().pos();
        face = node.face();
        targetDimension = node.gPos().dimension();
        hasTarget = true;
        visibleSideSlot.set(inputSide ? 1 : 0);
    }

    private void clearSyncedConfiguration() {
        globalInputSlot.set(0);
        globalOutputSlot.set(0);
        strategySlot.set(0);
        extractionModeSlot.set(0);
        prioritySlot.set(0);
        keepStockSlot.set(0);
        selectedTypesMaskSlot.set(0);
        speedMultSlot.set(0);
        rangeMultSlot.set(0);
        stackMultSlot.set(0);
        dimensionSlot.set(0);
        selectedTypeIds = List.of();
        Arrays.fill(lastUpgradeStacks, ItemStack.EMPTY);
    }

    /**
     * 在当前菜单内切换所查看的连接面方向，不重新创建容器界面。
     */
    public void selectVisibleSide(boolean inputSide) {
        visibleSideSlot.set(inputSide ? 1 : 0);
    }

    /**
     * 服务端菜单只接受当前窗口所展示一侧的修改，防止隐藏控件被构造数据包绕过。
     */
    public boolean allowsEdit(FaceConfigurationEdit edit) {
        if (!hasTarget) return false;
        if (isInputSideVisible()) {
            return edit instanceof FaceConfigurationEdit.BooleanEdit inputToggle
                && inputToggle.field() == FaceConfigurationEdit.BooleanField.GLOBAL_INPUT
                || edit instanceof FaceConfigurationEdit.NumberEdit;
        }
        return edit instanceof FaceConfigurationEdit.BooleanEdit outputToggle
            && outputToggle.field() == FaceConfigurationEdit.BooleanField.GLOBAL_OUTPUT
            || edit instanceof FaceConfigurationEdit.StrategyEdit
            || edit instanceof FaceConfigurationEdit.ExtractionEdit
            || edit instanceof FaceConfigurationEdit.SelectedTypesEdit;
    }

    public NodeMutationService.ValidatedNode
    resolveValidatedNode(ServerPlayer serverPlayer) {
        return new NodeMutationService()
            .resolveRemote(serverPlayer, getTargetNode(), groupKey);
    }

    public FaceConfigComposite getFaceConfig() {
        return faceConfig;
    }

    public ContainerConfig getContainerConfig() {
        return containerConfig;
    }

    private class FilterSlot extends SlotItemHandler {
        private final UpgradeType[] allowedTypes;
        private final boolean isInput;

        FilterSlot(IItemHandler handler, int index, int x, int y, boolean isInput, UpgradeType... allowed) {
            super(handler, index, x, y);
            this.allowedTypes = allowed;
            this.isInput = isInput;
        }

        @Override
        public boolean isActive() {
            return hasTarget && isInputSideVisible() == isInput;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (!isActive()) return false;
            if (!(stack.getItem() instanceof UpgradeItem upg)) return false;
            for (UpgradeType t : allowedTypes) if (upg.getType() == t) return true;
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return isActive();
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }
    }

    private class UpgradeSlot extends SlotItemHandler {
        private final UpgradeType[] allowedTypes;

        UpgradeSlot(IItemHandler handler, int index, int x, int y, UpgradeType... allowed) {
            super(handler, index, x, y);
            this.allowedTypes = allowed;
        }

        @Override
        public boolean isActive() {
            return hasTarget && isOutputSideVisible();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (!isActive()) return false;
            if (!(stack.getItem() instanceof UpgradeItem upg)) return false;
            for (UpgradeType t : allowedTypes) if (upg.getType() == t) return true;
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return isActive();
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }
    }

    /**
     * 槽位对象保持不变，仅切换实际数据源，避免选择节点时重建容器菜单。
     */
    private static final class SwitchingItemHandler
        implements IItemHandlerModifiable {
        private final int slotCount;
        private IItemHandlerModifiable delegate;

        private SwitchingItemHandler(int slotCount) {
            this.slotCount = slotCount;
            clear();
        }

        private void bind(IItemHandlerModifiable delegate) {
            if (delegate.getSlots() != slotCount) {
                throw new IllegalArgumentException(
                    "Item handler slot count does not match");
            }
            this.delegate = delegate;
        }

        private void clear() {
            delegate = new ItemStackHandler(slotCount);
        }

        @Override
        public int getSlots() {
            return slotCount;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return delegate.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return delegate.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return delegate.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return delegate.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return delegate.isItemValid(slot, stack);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            delegate.setStackInSlot(slot, stack);
        }
    }

    private record OpenData(
        BlockPos pos,
        Direction face,
        ResourceKey<Level> dimension,
        GroupKey groupKey,
        boolean inputSide,
        List<ResourceLocation> initialTypeIds,
        int toolSlot
    ) {
    }

    public static int findToolSlot(Inventory inventory) {
        int selected = inventory.selected;
        if (inventory.getItem(selected).getItem() instanceof LinkConfiguratorItem) {
            return selected;
        }
        int offhand = 40;
        if (offhand < inventory.getContainerSize()
            && inventory.getItem(offhand).getItem() instanceof LinkConfiguratorItem) {
            return offhand;
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).getItem() instanceof LinkConfiguratorItem) {
                return slot;
            }
        }
        return -1;
    }
}
