package com.coobird.staticlogistics.content.menu;

import com.coobird.staticlogistics.transfer.LogisticsResource;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import com.coobird.staticlogistics.content.item.UpgradeItem;
import com.coobird.staticlogistics.transfer.UpgradeType;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import com.coobird.staticlogistics.content.registry.SLMenuTypes;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.node.NodeInteractionValidator;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import com.coobird.staticlogistics.logistics.node.FaceAddress;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class FilterConfiguratorMenu extends AbstractFilterMenu {
    private final BlockPos pos;
    private final Direction face;
    private final LogisticsResource<?> transferType;
    private final boolean isInput;
    private final ItemStack upgradeStack;

    public FilterConfiguratorMenu(int containerId, Inventory inv, BlockPos pos, Direction face,
                                  LogisticsResource<?> type, boolean isInput,
                                  ItemStack upgradeStack) {
        super(SLMenuTypes.FILTER_CONFIG.get(), containerId, upgradeStack);
        this.pos = pos;
        this.face = face;
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
        if (type == null) throw new IllegalArgumentException("Unknown transfer type");
        return new FilterConfiguratorMenu(containerId, inv, pos, face, type, isInput, upgradeStack);
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

    public LogisticsResource<?> getTransferType() {
        return transferType;
    }

    public boolean isValidFilterUpgrade(ItemStack stack) {
        return stack.getItem() instanceof UpgradeItem upgrade && upgrade.isFilterUpgrade();
    }

    /** 将客户端编辑结果同时提交到面配置权威栈和当前菜单同步状态。 */
    public void commitFilterData(FilterData filter, ItemStack authoritativeStack) {
        authoritativeStack.set(SLDataComponents.FILTER_DATA.get(), filter);
        if (upgradeStack != authoritativeStack) {
            upgradeStack.set(SLDataComponents.FILTER_DATA.get(), filter);
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
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
            || !(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return player.level().isClientSide;
        }
        FaceConfigComposite current = LinkManager.get(level).getFaceConfig(FaceAddress.of(pos, face));
        return NodeInteractionValidator.holdsConfigurator(serverPlayer)
            && NodeInteractionValidator.canUseExisting(serverPlayer, pos, face, current);
    }

    private void addPlayerInventorySlots(Inventory playerInventory) {
        int playerInvX = (MenuLayout.BACKGROUND_WIDTH - MenuLayout.INVENTORY_WIDTH) / 2 + 7;
        int playerInvY = MenuLayout.BACKGROUND_HEIGHT + 8;
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, playerInvX + col * 18, playerInvY + row * 18));
            }
        }
        int hotbarY = playerInvY + 60;
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, playerInvX + col * 18, hotbarY));
        }
    }
}
