package com.coobird.staticlogistics.integration.resource;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsResource;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.logic.TransferRegistries;
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
 * Botania 魔力资源适配�?(Forge 1.20.1)�? *
 * <p>Botania 1.20.1 �?BlockEntity 直接实现 {@link ManaReceiver} 接口�? * 不需�?Forge Capability 查找�? * 产能�?({@link GeneratingFlowerBlockEntity}) 作为源，ManaReceiver 作为目标�?
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
    public boolean isSimpleResource() {
        return true;
    }

    /**
     * 魔力句柄，区分源（产能花）和目标（ManaReceiver）�?
     */
    public static final class ManaHandle {
        private final @Nullable GeneratingFlowerBlockEntity flower;
        private final @Nullable ManaReceiver receiver;
        private int currentMana;

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

        boolean isSource() {
            return flower != null;
        }

        boolean isTarget() {
            return receiver != null;
        }

        int currentMana() {
            return currentMana;
        }

        void addMana(int amount) {
            if (receiver != null) receiver.receiveMana(amount);
            else if (flower != null) flower.addMana(amount);
            currentMana += amount;
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
        if (!handle.isSource()) return 0;
        long actual = Math.min(handle.currentMana(), amount);
        if (actual <= 0) return 0;
        if (!simulate) handle.addMana((int) -actual);
        return actual;
    }

    @Override
    public long insert(ManaHandle handle, long amount, boolean simulate) {
        if (!handle.isTarget()) return 0;
        long space = handle.availableSpace();
        if (space <= 0) return 0;
        long actual = Math.min(amount, space);
        if (!simulate) handle.addMana((int) actual);
        return actual;
    }

    public static void register() {
        TransferRegistries.registerAdapter(new BotaniaManaResource());
    }
}
