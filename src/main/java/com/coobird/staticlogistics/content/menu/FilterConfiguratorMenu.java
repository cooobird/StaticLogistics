package com.coobird.staticlogistics.content.menu;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.content.item.UpgradeItem;
import com.coobird.staticlogistics.content.registry.SLMenuTypes;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import com.coobird.staticlogistics.logistics.node.NodeMutationService;
import com.coobird.staticlogistics.transfer.LogisticsResource;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import com.coobird.staticlogistics.transfer.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class FilterConfiguratorMenu extends AbstractFilterMenu {
    private final BlockPos pos;
    private final Direction face;
    private final ResourceKey<Level> targetDimension;
    private final GroupKey groupKey;
    private final LogisticsResource<?> transferType;
    private final boolean isInput;
    private final ItemStack upgradeStack;

    public FilterConfiguratorMenu(int containerId, Inventory inv, LogisticsNode node,
                                  GroupKey groupKey, LogisticsResource<?> type,
                                  boolean isInput, ItemStack upgradeStack) {
        this(containerId, inv, node.gPos().pos(), node.face(), node.gPos().dimension(),
            groupKey, type, isInput, upgradeStack);
    }

    private FilterConfiguratorMenu(int containerId, Inventory inv, BlockPos pos, Direction face,
                                   ResourceKey<Level> targetDimension,
                                   GroupKey groupKey,
                                   LogisticsResource<?> type, boolean isInput,
                                   ItemStack upgradeStack) {
        super(SLMenuTypes.FILTER_CONFIG.get(), containerId, upgradeStack);
        this.pos = pos;
        this.face = face;
        this.targetDimension = targetDimension;
        this.groupKey = groupKey;
        this.transferType = type;
        this.isInput = isInput;
        this.upgradeStack = upgradeStack;
        addPlayerInventorySlots(inv);
    }

    public static FilterConfiguratorMenu fromBuffer(int containerId, Inventory inv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Direction face = buf.readEnum(Direction.class);
        LogisticsResource<?> type = TransferRegistries.get(buf.readResourceLocation());
        boolean isInput = buf.readBoolean();
        ItemStack upgradeStack = ItemStack.STREAM_CODEC.decode((RegistryFriendlyByteBuf) buf);
        upgradeStack.set(SLDataComponents.FILTER_DATA.get(),
            FilterData.STREAM_CODEC.decode((RegistryFriendlyByteBuf) buf));
        if (type == null) throw new IllegalArgumentException("Unknown transfer type");
        ResourceKey<Level> dimension = ResourceKey.create(
            Registries.DIMENSION, buf.readResourceLocation());
        GroupKey groupKey = GroupKey.STREAM_CODEC.decode((RegistryFriendlyByteBuf) buf);
        return new FilterConfiguratorMenu(containerId, inv, pos, face, dimension, groupKey,
            type, isInput, upgradeStack);
    }

    @Override
    protected ItemStack getFilterStack() {
        return upgradeStack;
    }

    public boolean isInput() {
        return isInput;
    }

    public BlockPos getPos() {
        return pos;
    }

    public Direction getFace() {
        return face;
    }

    public LogisticsNode getTargetNode() {
        return new LogisticsNode(GlobalPos.of(targetDimension, pos), face);
    }

    public GroupKey getRemoteGroupKey() {
        return groupKey;
    }

    @Nullable
    public NodeMutationService.ValidatedNode
    resolveValidatedNode(ServerPlayer player) {
        return new NodeMutationService()
            .resolveRemote(player, getTargetNode(), groupKey);
    }

    public LogisticsResource<?> getTransferType() {
        return transferType;
    }

    public boolean isValidFilterUpgrade(ItemStack stack) {
        return stack.getItem() instanceof UpgradeItem upgrade && upgrade.isFilterUpgrade();
    }

    /**
     * 过滤器编辑会话始终绑定打开时的那件权威物品；槽位被替换后旧窗口立即失效。
     */
    public boolean matchesInstalledFilter(FaceConfigComposite config) {
        if (config == null) return false;
        ItemStack installed = config.filterConfig.getUpgrades().getStackInSlot(isInput ? 0 : 1);
        return installed == upgradeStack && isValidFilterUpgrade(installed);
    }

    /**
     * 将客户端编辑结果同时提交到面配置权威栈和当前菜单同步状态。
     */
    public void commitFilterData(FilterData filter, ItemStack authoritativeStack) {
        FilterData normalized = filter.normalizedFor(getActiveUpgradeType());
        authoritativeStack.set(SLDataComponents.FILTER_DATA.get(), normalized);
        if (upgradeStack != authoritativeStack) {
            upgradeStack.set(SLDataComponents.FILTER_DATA.get(), normalized);
        }
        syncFromStack(upgradeStack);
        broadcastChanges();
    }

    public UpgradeType getActiveUpgradeType() {
        if (upgradeStack.getItem() instanceof UpgradeItem upgrade) return upgrade.getType();
        return null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)
        ) {
            return player.level().isClientSide;
        }
        var node = resolveValidatedNode(serverPlayer);
        return node != null && matchesInstalledFilter(node.config());
    }

    private void addPlayerInventorySlots(Inventory playerInventory) {
        int playerInvX = (MenuLayout.BACKGROUND_WIDTH - MenuLayout.INVENTORY_WIDTH) / 2
            + MenuLayout.INVENTORY_SLOT_X;
        int playerInvY = MenuLayout.BACKGROUND_HEIGHT + MenuLayout.INVENTORY_SLOT_Y;
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, playerInvX + col * 18, playerInvY + row * 18));
            }
        }
        int hotbarY = MenuLayout.BACKGROUND_HEIGHT + MenuLayout.HOTBAR_SLOT_Y;
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, playerInvX + col * 18, hotbarY));
        }
    }
}
