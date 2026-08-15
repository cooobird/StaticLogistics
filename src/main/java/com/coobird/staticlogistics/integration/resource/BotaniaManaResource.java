package com.coobird.staticlogistics.integration.resource;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.transfer.TransactionCapabilities;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.LogisticsResource;
import com.coobird.staticlogistics.transfer.TransferContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import vazkii.botania.api.block_entity.GeneratingFlowerBlockEntity;
import vazkii.botania.api.mana.ManaCollector;
import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.common.block.BotaniaBlocks;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Botania 魔力资源适配器（Forge 1.20.1）。
 * 方块实体直接实现 {@link ManaReceiver}，无需 Forge Capability。
 * 产能花只提供魔力；魔力池与魔力收集器既可提供也可接收魔力；其他接收器仅作为接收端。
 */
public class BotaniaManaResource implements LogisticsResource<BotaniaManaResource.ManaHandle> {
    private static final ResourceLocation TYPE_ID = StaticLogistics.asResource("botania_mana");

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public int color() {
        return 0xFF55FFFF;
    }

    @Override
    public String translationKey() {
        return "transfer_type.staticlogistics.botania_mana";
    }

    @Override
    public Supplier<ItemStack> iconSupplier() {
        return () -> new ItemStack(BotaniaBlocks.manaPool);
    }

    @Override
    public IntSupplier baseStackSizeSupplier() {
        return SLConfig::getBotaniaManaStack;
    }

    @Override
    public TransactionCapabilities transactionCapabilities() {
        return TransactionCapabilities.exactCompensating();
    }

    /**
     * 魔力句柄，区分产能花与 ManaReceiver。
     */
    public static final class ManaHandle {
        private final @Nullable GeneratingFlowerBlockEntity flower;
        private final @Nullable ManaReceiver receiver;
        private int currentMana;
        private boolean nonDepletingSource;

        private ManaHandle(@Nullable GeneratingFlowerBlockEntity flower,
                           @Nullable ManaReceiver receiver,
                           int currentMana) {
            if ((flower == null) == (receiver == null))
                throw new IllegalArgumentException("Exactly one of flower or receiver must be non-null");
            this.flower = flower;
            this.receiver = receiver;
            this.currentMana = currentMana;
        }

        static ManaHandle source(GeneratingFlowerBlockEntity flower, int current) {
            return new ManaHandle(flower, null, current);
        }

        static ManaHandle target(ManaReceiver receiver, int current) {
            return new ManaHandle(null, receiver, current);
        }

        boolean canExtract() {
            return flower != null || receiver instanceof ManaPool || receiver instanceof ManaCollector;
        }

        boolean canInsert() {
            return receiver != null;
        }

        int currentMana() {
            return currentMana;
        }

        int changeMana(int amount) {
            int before = currentMana;
            if (receiver != null) {
                receiver.receiveMana(amount);
                currentMana = receiver.getCurrentMana();
            } else if (flower != null) {
                flower.addMana(amount);
                currentMana = flower.getMana();
            }
            int changed = currentMana - before;
            // 创造型魔力池的读数不会下降，但应保持无限魔力源语义。
            if (amount < 0 && receiver instanceof ManaPool && before > 0 && changed == 0) {
                nonDepletingSource = true;
                return amount;
            }
            return changed;
        }

        int availableSpace() {
            if (flower != null) return 0;
            if (receiver instanceof ManaPool pool)
                return Math.max(0, pool.getMaxMana() - receiver.getCurrentMana());
            if (receiver instanceof ManaCollector col)
                return Math.max(0, col.getMaxMana() - receiver.getCurrentMana());
            return Integer.MAX_VALUE;
        }
    }

    @Override
    public boolean isPresent(ServerLevel level, BlockPos pos, Direction face) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return false;
        return be instanceof GeneratingFlowerBlockEntity || be instanceof ManaReceiver;
    }

    @Override
    public @Nullable ManaHandle resolve(ServerLevel level, BlockPos pos, Direction face) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;

        if (be instanceof GeneratingFlowerBlockEntity flower) {
            return ManaHandle.source(flower, flower.getMana());
        }

        if (be instanceof ManaReceiver receiver) {
            return ManaHandle.target(receiver, receiver.getCurrentMana());
        }

        return null;
    }

    @Override
    public long extract(ManaHandle handle, long amount, boolean simulate) {
        if (!handle.canExtract()) return 0;
        long actual = Math.min(handle.currentMana(), amount);
        if (actual <= 0) return 0;
        return simulate ? actual : Math.max(0L, -(long) handle.changeMana((int) -actual));
    }

    @Override
    public long insert(ManaHandle handle, long amount, boolean simulate) {
        if (!handle.canInsert()) return 0;
        long space = handle.availableSpace();
        if (space <= 0) return 0;
        long actual = Math.min(amount, space);
        return simulate ? actual : Math.max(0L, handle.changeMana((int) actual));
    }

    @Override
    public boolean rollback(ManaHandle source, Object value, long amount,
                            @Nullable FaceConfigComposite sourceConfig, boolean pullMode,
                            @Nullable TransferContext context) {
        if (!(value instanceof Number) || amount <= 0L || amount > Integer.MAX_VALUE) return amount <= 0L;
        int restored = source.changeMana((int) amount);
        return restored >= amount || source.nonDepletingSource;
    }
}
