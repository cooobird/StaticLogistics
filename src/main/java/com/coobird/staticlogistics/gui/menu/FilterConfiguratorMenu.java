package com.coobird.staticlogistics.gui.menu;

import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.api.type.UpgradeType;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
import com.coobird.staticlogistics.item.UpgradeItem;
import com.coobird.staticlogistics.registry.SLMenuTypes;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.storage.config.LinkConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class FilterConfiguratorMenu extends AbstractFilterMenu {
    private final FaceConfigComposite config;
    private final BlockPos pos;
    private final Direction face;
    private final TransferType transferType;
    private final boolean isInput;
    private final ItemStack upgradeStack;

    public FilterConfiguratorMenu(int containerId, Inventory inv, BlockPos pos, Direction face,
                                  TransferType type, FaceConfigComposite config, boolean isInput,
                                  ItemStack upgradeStack) {
        super(SLMenuTypes.FILTER_CONFIG.get(), containerId, upgradeStack);
        this.pos = pos;
        this.face = face;
        this.transferType = type;
        this.config = config;
        this.isInput = isInput;
        this.upgradeStack = upgradeStack;
        addPlayerInventorySlots(inv);
    }

    public static FilterConfiguratorMenu fromBuffer(int containerId, Inventory inv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Direction face = buf.readEnum(Direction.class);
        TransferType type = TransferRegistries.get(buf.readResourceLocation());
        CompoundTag tag = buf.readNbt();
        boolean isInput = buf.readBoolean();
        ItemStack upgradeStack = ItemStack.STREAM_CODEC.decode((RegistryFriendlyByteBuf) buf);
        FaceConfigComposite composite = new FaceConfigComposite();
        if (tag != null) {
            if (buf instanceof RegistryFriendlyByteBuf registryBuf) {
                composite.deserializeNBT(registryBuf.registryAccess(), tag);
            } else {
                composite.deserializeNBT(null, tag);
            }
        }
        if (type == null) throw new IllegalArgumentException("Unknown transfer type");
        return new FilterConfiguratorMenu(containerId, inv, pos, face, type, composite, isInput, upgradeStack);
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

    public TransferType getTransferType() {
        return transferType;
    }

    public FaceConfigComposite getFaceConfig() {
        return config;
    }

    public LinkConfig.SideData getSideData() {
        return config.linkConfig.getSettings(transferType);
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
        return player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) <= 64;
    }

    private void addPlayerInventorySlots(Inventory playerInventory) {
        int playerInvX = (SLGuiTextures.Background.WIDTH - SLGuiTextures.Inventory.WIDTH) / 2 + 8;
        int playerInvY = SLGuiTextures.Background.HEIGHT + 8;
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